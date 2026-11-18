"""分析结果读取与懒触发。"""

import asyncio
import logging
from collections.abc import Callable, Coroutine
from datetime import UTC, datetime
from typing import Any, Protocol

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.capabilities.content.client import (
    ArticleContentClient,
    ArticleContentMissing,
    ArticleNotFound,
    ArticleNotPublished,
    ArticleSnapshot,
)
from app.contracts.analysis import AnalysisVO
from app.core.errors import BusinessError, ErrorCode
from app.features.analysis.models import AnalysisResult
from app.features.analysis.repository import AnalysisRepository

log = logging.getLogger(__name__)

ERROR_MESSAGE = "文章分析失败，请稍后重试。"
NOT_FOUND_MESSAGE = "文章不存在"


class AnalysisRunner(Protocol):
    async def run(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        author_id: str | None = None,
    ) -> Any: ...


class Spawn(Protocol):
    def __call__(self, coro: Coroutine[Any, Any, Any], *, name: str) -> asyncio.Task[Any]: ...


def _utcnow() -> datetime:
    return datetime.now(UTC)


class AnalysisService:
    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        client: ArticleContentClient,
        job: AnalysisRunner,
        *,
        spawn: Spawn,
        clock: Callable[[], datetime] = _utcnow,
    ) -> None:
        self._session_factory = session_factory
        self._client = client
        self._job = job
        self._spawn = spawn
        self._clock = clock
        self._inflight: set[str] = set()

    async def get(self, article_id: str, *, trigger: bool = False) -> AnalysisVO:
        row = await self._load(article_id)
        if not trigger or not self._should_trigger(row):
            return _to_vo(article_id, row)

        snapshot = await self._snapshot(article_id)
        if snapshot is None:
            return _to_vo(article_id, row)
        self._start(article_id, snapshot.update_time, snapshot.author_id)
        return AnalysisVO(
            article_id=article_id,
            status="pending",
            source_updated_at=snapshot.update_time,
        )

    async def _load(self, article_id: str) -> AnalysisResult | None:
        async with self._session_factory() as session:
            return await AnalysisRepository(session).get(article_id)

    def _should_trigger(self, row: AnalysisResult | None) -> bool:
        if row is None:
            return True
        if row.status == "error":
            return True
        if row.status == "in_progress":
            return row.expires_at is None or row.expires_at <= self._clock()
        return False

    async def _snapshot(self, article_id: str) -> ArticleSnapshot | None:
        try:
            return await self._client.get_snapshot(article_id)
        except ArticleNotFound as exc:
            raise BusinessError(ErrorCode.NOT_FOUND, NOT_FOUND_MESSAGE) from exc
        except (ArticleNotPublished, ArticleContentMissing) as exc:
            log.info("文章不可分析，跳过懒触发 article=%s: %s", article_id, exc)
            return None
        except Exception:
            log.warning("文章快照不可用，跳过懒触发 article=%s", article_id, exc_info=True)
            return None

    def _start(self, article_id: str, source_updated_at: datetime, author_id: str) -> None:
        if article_id in self._inflight:
            return
        self._inflight.add(article_id)
        coro = self._job.run(article_id, source_updated_at, author_id=author_id)
        try:
            task = self._spawn(coro, name=f"analysis:{article_id}")
        except Exception:
            coro.close()
            self._inflight.discard(article_id)
            raise
        task.add_done_callback(lambda _: self._inflight.discard(article_id))


def _to_vo(article_id: str, row: AnalysisResult | None) -> AnalysisVO:
    if row is None or row.status == "in_progress":
        return AnalysisVO(
            article_id=article_id,
            status="pending",
            source_updated_at=None if row is None else row.source_updated_at,
        )
    if row.status == "error":
        return AnalysisVO(
            article_id=article_id,
            status="error",
            source_updated_at=row.source_updated_at,
            message=ERROR_MESSAGE,
        )
    analysis = row.analysis if isinstance(row.analysis, dict) else {}
    return AnalysisVO(
        article_id=article_id,
        status="done",
        topic=_text(analysis.get("topic")),
        entities=_names(analysis.get("entities")),
        knowledge_points=_names(analysis.get("knowledge_points")),
        source_updated_at=row.source_updated_at,
        analyzed_at=row.updated_at,
    )


def _text(value: object) -> str:
    return value.strip() if isinstance(value, str) else ""


def _names(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [item for item in value if isinstance(item, str) and item.strip()]
