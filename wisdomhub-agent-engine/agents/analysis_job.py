import asyncio
import logging
from datetime import UTC, datetime, timedelta
from typing import Any

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from agents.analysis import (
    ArticleAnalysis,
    ArticleNotFound,
    ArticleNotPublished,
    analyze_article,
)
from app.config import Settings
from db import analysis_repository
from tools.internal_client import InternalClient

log = logging.getLogger(__name__)


class AnalysisJob:
    """claim → analyze_article → store → publish；返回 True 表示 ACK，False 进 DLQ。"""

    def __init__(
        self,
        *,
        settings: Settings,
        session_factory: async_sessionmaker[AsyncSession],
        client: InternalClient,
        llm: Any,
        publisher: Any,
    ) -> None:
        self._settings = settings
        self._session_factory = session_factory
        self._client = client
        self._llm = llm
        self._publisher = publisher

    async def run(self, article_id: str, *, source_updated_at: datetime) -> bool:
        now = datetime.now(UTC)
        async with self._session_factory() as session:
            async with session.begin():
                outcome, stored = await analysis_repository.claim(
                    session,
                    article_id=article_id,
                    source_updated_at=source_updated_at,
                    now=now,
                    expires_at=now
                    + timedelta(seconds=max(1, self._settings.article_analysis_ttl)),
                )
        log.info(
            "文章分析 claim article=%s version=%s outcome=%s",
            article_id,
            source_updated_at.isoformat(),
            outcome,
        )

        if outcome == "in_progress":
            log.info("文章分析已被其他消费者处理 article=%s", article_id)
            return True
        if outcome == "stale":
            log.info("文章分析事件早于已处理版本，丢弃 article=%s", article_id)
            return True
        if outcome == "duplicate":
            log.info("文章分析版本未变且已发布，跳过 article=%s", article_id)
            return True
        if outcome == "republish":
            if not isinstance(stored, dict):
                log.error("分析结果为空但状态是 done article=%s", article_id)
                return False
            return await self._publish(article_id, stored.get("entities") or [], source_updated_at)

        analysis: ArticleAnalysis | None = None
        saved = False
        last_error = ""
        total = 1 + max(0, self._settings.analysis_retry_times)

        for attempt in range(total):
            try:
                if analysis is None:
                    analysis = await analyze_article(
                        self._client, self._llm, article_id, settings=self._settings
                    )
                if not await self._finish(article_id, analysis, source_updated_at):
                    log.info("文章已改稿，丢弃过期分析结果 article=%s", article_id)
                    return True
                saved = True
                if await self._publish(article_id, analysis.entities, source_updated_at):
                    return True
                last_error = "KnowledgeExtractEvent 发布失败"
            except (ArticleNotFound, ArticleNotPublished) as exc:
                await self._fail(article_id, str(exc), source_updated_at)
                return True
            except Exception as exc:
                last_error = f"{type(exc).__name__}: {exc}"
                log.warning(
                    "文章分析失败 article=%s attempt=%d/%d: %s",
                    article_id,
                    attempt + 1,
                    total,
                    last_error,
                )

            if attempt + 1 < total:
                await asyncio.sleep(2 ** attempt)

        if not saved:
            await self._fail(article_id, last_error or "文章分析失败", source_updated_at)
        return False

    async def _finish(
        self, article_id: str, analysis: ArticleAnalysis, source_updated_at: datetime
    ) -> bool:
        async with self._session_factory() as session:
            async with session.begin():
                return await analysis_repository.finish(
                    session,
                    article_id=article_id,
                    analysis=analysis.model_dump(),
                    source_updated_at=source_updated_at,
                    now=datetime.now(UTC),
                ) > 0

    async def _fail(self, article_id: str, error: str, source_updated_at: datetime) -> None:
        try:
            async with self._session_factory() as session:
                async with session.begin():
                    await analysis_repository.fail(
                        session,
                        article_id=article_id,
                        error=error,
                        source_updated_at=source_updated_at,
                        now=datetime.now(UTC),
                    )
        except Exception:
            log.exception("文章分析失败状态落库失败 article=%s", article_id)

    async def _publish(
        self, article_id: str, entities: list[str], source_updated_at: datetime
    ) -> bool:
        try:
            await self._publisher.publish(article_id, entities)
        except Exception:
            log.warning("KnowledgeExtractEvent 发布失败 article=%s", article_id, exc_info=True)
            return False
        try:
            async with self._session_factory() as session:
                async with session.begin():
                    await analysis_repository.mark_published(
                        session,
                        article_id=article_id,
                        source_updated_at=source_updated_at,
                        now=datetime.now(UTC),
                    )
        except Exception:
            log.warning("发布状态落库失败 article=%s", article_id, exc_info=True)
            return False
        return True
