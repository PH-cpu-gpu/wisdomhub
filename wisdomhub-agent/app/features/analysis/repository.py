"""文章分析结果仓储：版本化 claim、终态写入与发布标记。"""

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from enum import StrEnum
from typing import Any

from sqlalchemy import update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.features.analysis.models import AnalysisResult

LAST_ERROR_MAX = 1000


def _utcnow() -> datetime:
    return datetime.now(UTC)


class ClaimOutcome(StrEnum):
    NEW = "new"
    IN_PROGRESS = "in_progress"
    STALE = "stale"
    DUPLICATE = "duplicate"
    REPUBLISH = "republish"


@dataclass(frozen=True, slots=True)
class ClaimResult:
    outcome: ClaimOutcome
    row: AnalysisResult


class AnalysisRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def get(self, article_id: str) -> AnalysisResult | None:
        return await self._session.get(AnalysisResult, article_id)

    async def claim(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        lease_seconds: int,
        now: datetime | None = None,
    ) -> ClaimResult:
        if lease_seconds < 1:
            raise ValueError("lease_seconds 必须大于 0")
        moment = now or _utcnow()
        expires_at = moment + timedelta(seconds=lease_seconds)

        inserted = await self._session.scalar(
            pg_insert(AnalysisResult)
            .values(
                article_id=article_id,
                status="in_progress",
                analysis=None,
                last_error=None,
                attempts=1,
                source_updated_at=source_updated_at,
                published_at=None,
                notified_at=None,
                expires_at=expires_at,
                created_at=moment,
                updated_at=moment,
            )
            .on_conflict_do_nothing(index_elements=["article_id"])
            .returning(AnalysisResult.article_id)
        )
        if inserted is not None:
            row = await self.get(article_id)
            if row is None:
                raise RuntimeError("claim 插入后读不到行")
            return ClaimResult(ClaimOutcome.NEW, row)

        row = await self.get(article_id)
        if row is None:
            raise RuntimeError("claim 冲突后读不到行")
        outcome = _classify(row, source_updated_at, moment)
        if outcome is not None:
            return ClaimResult(outcome, row)

        taken = await self._takeover(row, source_updated_at, expires_at, moment)
        if taken is not None:
            return ClaimResult(ClaimOutcome.NEW, taken)

        current = await self.get(article_id)
        if current is None:
            raise RuntimeError("claim 接管失败后读不到行")
        outcome = _classify(current, source_updated_at, moment)
        return ClaimResult(outcome or ClaimOutcome.IN_PROGRESS, current)

    async def finish(
        self,
        article_id: str,
        source_updated_at: datetime,
        analysis: dict[str, Any],
        *,
        now: datetime | None = None,
    ) -> bool:
        moment = now or _utcnow()
        updated = await self._session.scalar(
            update(AnalysisResult)
            .where(
                AnalysisResult.article_id == article_id,
                AnalysisResult.status == "in_progress",
                AnalysisResult.source_updated_at == source_updated_at,
            )
            .values(
                status="done",
                analysis=analysis,
                last_error=None,
                expires_at=None,
                updated_at=moment,
            )
            .returning(AnalysisResult.article_id)
        )
        return updated is not None

    async def fail(
        self,
        article_id: str,
        source_updated_at: datetime,
        error: str,
        *,
        now: datetime | None = None,
    ) -> bool:
        moment = now or _utcnow()
        updated = await self._session.scalar(
            update(AnalysisResult)
            .where(
                AnalysisResult.article_id == article_id,
                AnalysisResult.status == "in_progress",
                AnalysisResult.source_updated_at == source_updated_at,
            )
            .values(
                status="error",
                last_error=error[:LAST_ERROR_MAX],
                expires_at=None,
                updated_at=moment,
            )
            .returning(AnalysisResult.article_id)
        )
        return updated is not None

    async def mark_published(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        now: datetime | None = None,
    ) -> bool:
        moment = now or _utcnow()
        updated = await self._session.scalar(
            update(AnalysisResult)
            .where(
                AnalysisResult.article_id == article_id,
                AnalysisResult.status == "done",
                AnalysisResult.source_updated_at == source_updated_at,
                AnalysisResult.published_at.is_(None),
            )
            .values(published_at=moment, updated_at=moment)
            .returning(AnalysisResult.article_id)
        )
        return updated is not None

    async def mark_notified(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        now: datetime | None = None,
    ) -> bool:
        moment = now or _utcnow()
        updated = await self._session.scalar(
            update(AnalysisResult)
            .where(
                AnalysisResult.article_id == article_id,
                AnalysisResult.status == "done",
                AnalysisResult.source_updated_at == source_updated_at,
                AnalysisResult.published_at.is_not(None),
                AnalysisResult.notified_at.is_(None),
            )
            .values(notified_at=moment, updated_at=moment)
            .returning(AnalysisResult.article_id)
        )
        return updated is not None

    async def _takeover(
        self,
        row: AnalysisResult,
        source_updated_at: datetime,
        expires_at: datetime,
        moment: datetime,
    ) -> AnalysisResult | None:
        conditions = [
            AnalysisResult.article_id == row.article_id,
            AnalysisResult.status == row.status,
            (
                AnalysisResult.source_updated_at.is_(None)
                if row.source_updated_at is None
                else AnalysisResult.source_updated_at == row.source_updated_at
            ),
        ]
        if row.status == "in_progress":
            conditions.append(
                AnalysisResult.expires_at.is_(None) | (AnalysisResult.expires_at <= moment)
            )
        taken = await self._session.scalar(
            update(AnalysisResult)
            .where(*conditions)
            .values(
                status="in_progress",
                source_updated_at=source_updated_at,
                analysis=None,
                last_error=None,
                attempts=AnalysisResult.attempts + 1,
                published_at=None,
                notified_at=None,
                expires_at=expires_at,
                updated_at=moment,
            )
            .returning(AnalysisResult.article_id)
        )
        if taken is None:
            return None
        return await self.get(row.article_id)


def _classify(
    row: AnalysisResult, source_updated_at: datetime, moment: datetime
) -> ClaimOutcome | None:
    stored = row.source_updated_at
    if stored is None:
        return None
    if source_updated_at < stored:
        return ClaimOutcome.STALE
    if source_updated_at > stored:
        return None
    if row.status == "done":
        return ClaimOutcome.DUPLICATE if row.published_at is not None else ClaimOutcome.REPUBLISH
    if row.status == "in_progress" and row.expires_at is not None and row.expires_at > moment:
        return ClaimOutcome.IN_PROGRESS
    return None
