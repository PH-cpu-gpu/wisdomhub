"""文章分析 Job：claim → pipeline → finish → publish，带指数退避。"""

import asyncio
import logging
from collections.abc import Awaitable, Callable
from datetime import UTC, datetime
from enum import StrEnum
from typing import Any, Protocol

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.capabilities.content.client import ArticleContentClient, ArticleSnapshotError
from app.features.analysis.models import AnalysisResult
from app.features.analysis.pipeline import ArticleAnalysis, run_pipeline
from app.features.analysis.repository import (
    AnalysisRepository,
    ClaimOutcome,
    ClaimResult,
)
from app.infra.mq.topology import KNOWLEDGE_EXTRACT
from app.runtime.structured import StructuredLLM

log = logging.getLogger(__name__)

RETRY_BASE_SECONDS = 2.0


class JobResult(StrEnum):
    """消费端处理结果：ACK 确认，DLQ 拒绝且不重回队列。"""

    ACK = "ack"
    DLQ = "dlq"


class EventPublisher(Protocol):
    async def publish(self, event_type: str, payload: dict[str, Any]) -> Any: ...


Sleeper = Callable[[float], Awaitable[None]]


def _utcnow() -> datetime:
    return datetime.now(UTC)


def _describe(exc: Exception) -> str:
    return f"{type(exc).__name__}: {exc}"


class AnalysisJob:
    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        client: ArticleContentClient,
        llm: StructuredLLM,
        publisher: EventPublisher,
        *,
        retry_times: int,
        lease_seconds: int,
        max_article_tokens: int,
        structured_retries: int,
        clock: Callable[[], datetime] = _utcnow,
        sleep: Sleeper = asyncio.sleep,
    ) -> None:
        if retry_times < 0:
            raise ValueError("retry_times 不能为负数")
        if lease_seconds < 1:
            raise ValueError("lease_seconds 必须大于 0")
        if max_article_tokens < 1:
            raise ValueError("max_article_tokens 必须大于 0")
        if structured_retries < 0:
            raise ValueError("structured_retries 不能为负数")
        self._session_factory = session_factory
        self._client = client
        self._llm = llm
        self._publisher = publisher
        self._retry_times = retry_times
        self._lease_seconds = lease_seconds
        self._max_article_tokens = max_article_tokens
        self._structured_retries = structured_retries
        self._clock = clock
        self._sleep = sleep

    async def run(self, article_id: str, source_updated_at: datetime) -> JobResult:
        claim = await self._claim(article_id, source_updated_at)
        log.info(
            "文章分析 claim article=%s version=%s outcome=%s",
            article_id,
            source_updated_at.isoformat(),
            claim.outcome,
        )

        if claim.outcome in (
            ClaimOutcome.IN_PROGRESS,
            ClaimOutcome.STALE,
            ClaimOutcome.DUPLICATE,
        ):
            return JobResult.ACK

        if claim.outcome is ClaimOutcome.REPUBLISH:
            entities = _stored_entities(claim.row)
            if entities is None:
                await self._fail(article_id, source_updated_at, "已存分析结果缺少 entities")
                return JobResult.DLQ
            return await self._execute(
                article_id,
                source_updated_at,
                analysis=None,
                entities=entities,
                finished=True,
            )

        return await self._execute(
            article_id,
            source_updated_at,
            analysis=None,
            entities=None,
            finished=False,
        )

    async def _execute(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        analysis: ArticleAnalysis | None,
        entities: list[str] | None,
        finished: bool,
    ) -> JobResult:
        total = self._retry_times + 1
        last_error = ""
        for attempt in range(total):
            try:
                if entities is None:
                    if analysis is None:
                        analysis = await run_pipeline(
                            self._client,
                            self._llm,
                            article_id,
                            max_article_tokens=self._max_article_tokens,
                            structured_retries=self._structured_retries,
                        )
                    if not finished:
                        if not await self._finish(article_id, source_updated_at, analysis):
                            log.info("文章已改稿，丢弃过期分析结果 article=%s", article_id)
                            return JobResult.ACK
                        finished = True
                    entities = analysis.entities

                await self._publisher.publish(
                    KNOWLEDGE_EXTRACT,
                    _payload(article_id, entities),
                )
                if not await self._mark_published(article_id, source_updated_at):
                    log.warning("发布标记未写入（版本可能已变化）article=%s", article_id)
                return JobResult.ACK
            except ArticleSnapshotError as exc:
                await self._fail(article_id, source_updated_at, _describe(exc))
                return JobResult.ACK
            except Exception as exc:
                last_error = _describe(exc)
                log.warning(
                    "文章分析失败 article=%s attempt=%d/%d: %s",
                    article_id,
                    attempt + 1,
                    total,
                    last_error,
                    exc_info=True,
                )

            if attempt + 1 < total:
                await self._sleep(RETRY_BASE_SECONDS * 2**attempt)

        await self._fail(article_id, source_updated_at, last_error or "文章分析失败")
        return JobResult.DLQ

    async def _claim(self, article_id: str, source_updated_at: datetime) -> ClaimResult:
        async with self._session_factory() as session, session.begin():
            return await AnalysisRepository(session).claim(
                article_id,
                source_updated_at,
                lease_seconds=self._lease_seconds,
                now=self._clock(),
            )

    async def _finish(
        self,
        article_id: str,
        source_updated_at: datetime,
        analysis: ArticleAnalysis,
    ) -> bool:
        async with self._session_factory() as session, session.begin():
            return await AnalysisRepository(session).finish(
                article_id,
                source_updated_at,
                analysis.model_dump(),
            )

    async def _mark_published(self, article_id: str, source_updated_at: datetime) -> bool:
        async with self._session_factory() as session, session.begin():
            return await AnalysisRepository(session).mark_published(
                article_id,
                source_updated_at,
            )

    async def _fail(self, article_id: str, source_updated_at: datetime, error: str) -> None:
        try:
            async with self._session_factory() as session, session.begin():
                await AnalysisRepository(session).fail(article_id, source_updated_at, error)
        except Exception:
            log.exception("文章分析失败状态落库失败 article=%s", article_id)


def _stored_entities(row: AnalysisResult) -> list[str] | None:
    analysis = row.analysis
    if not isinstance(analysis, dict):
        return None
    entities = analysis.get("entities")
    if not isinstance(entities, list) or not all(isinstance(name, str) for name in entities):
        return None
    names = [name.strip() for name in entities]
    return [name for name in names if name]


def _payload(article_id: str, entities: list[str]) -> dict[str, Any]:
    return {
        "articleId": int(article_id),
        "entities": [{"name": name} for name in entities],
    }
