from datetime import datetime
from typing import Any

from sqlalchemy import and_, or_, select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from db.models import AiAnalysisResult

MAX_ERROR_CHARS = 1000


async def get(session: AsyncSession, article_id: str) -> AiAnalysisResult | None:
    return await session.scalar(
        select(AiAnalysisResult).where(AiAnalysisResult.article_id == article_id)
    )


async def claim(
    session: AsyncSession,
    *,
    article_id: str,
    source_updated_at: datetime,
    now: datetime,
    expires_at: datetime,
) -> tuple[str, dict[str, Any] | None]:
    """返回 new / reanalyze / republish / duplicate / stale / in_progress；republish 带已存 analysis。"""
    inserted = await session.scalar(
        pg_insert(AiAnalysisResult)
        .values(
            article_id=article_id,
            status="in_progress",
            analysis=None,
            source_updated_at=source_updated_at,
            published_at=None,
            last_error=None,
            attempts=0,
            expires_at=expires_at,
            created_at=now,
            updated_at=now,
        )
        .on_conflict_do_nothing(index_elements=["article_id"])
        .returning(AiAnalysisResult.article_id)
    )
    if inserted is not None:
        return "new", None

    row = await get(session, article_id)
    if row is None:
        return "in_progress", None

    stored = row.source_updated_at
    if stored is not None and source_updated_at < stored:
        return "stale", None
    if stored is not None and source_updated_at == stored:
        if row.status == "done":
            return ("republish" if row.published_at is None else "duplicate"), row.analysis
        if row.status == "in_progress" and row.expires_at and row.expires_at > now:
            return "in_progress", None
        # error / 过期 in_progress → 同版本重试

    result = await session.execute(
        update(AiAnalysisResult)
        .where(
            AiAnalysisResult.article_id == article_id,
            or_(
                and_(
                    AiAnalysisResult.status == "error",
                    or_(
                        AiAnalysisResult.source_updated_at.is_(None),
                        AiAnalysisResult.source_updated_at <= source_updated_at,
                    ),
                ),
                and_(
                    AiAnalysisResult.status == "done",
                    or_(
                        AiAnalysisResult.source_updated_at.is_(None),
                        AiAnalysisResult.source_updated_at < source_updated_at,
                    ),
                ),
                and_(
                    AiAnalysisResult.status == "in_progress",
                    or_(
                        AiAnalysisResult.source_updated_at.is_(None),
                        AiAnalysisResult.source_updated_at < source_updated_at,
                        AiAnalysisResult.expires_at.is_(None),
                        AiAnalysisResult.expires_at < now,
                    ),
                ),
            ),
        )
        .values(
            status="in_progress",
            analysis=None,
            source_updated_at=source_updated_at,
            published_at=None,
            last_error=None,
            attempts=AiAnalysisResult.attempts + 1,
            expires_at=expires_at,
            updated_at=now,
        )
    )
    return ("reanalyze", None) if result.rowcount == 1 else ("in_progress", None)


async def finish(
    session: AsyncSession,
    *,
    article_id: str,
    analysis: dict[str, Any],
    source_updated_at: datetime,
    now: datetime,
) -> int:
    """只写回仍属于该版本的结果；版本已被改稿事件推走后返回 0。"""
    result = await session.execute(
        update(AiAnalysisResult)
        .where(
            AiAnalysisResult.article_id == article_id,
            AiAnalysisResult.source_updated_at == source_updated_at,
        )
        .values(
            status="done",
            analysis=analysis,
            last_error=None,
            expires_at=None,
            updated_at=now,
        )
    )
    return result.rowcount


async def mark_published(
    session: AsyncSession,
    *,
    article_id: str,
    source_updated_at: datetime,
    now: datetime,
) -> int:
    result = await session.execute(
        update(AiAnalysisResult)
        .where(
            AiAnalysisResult.article_id == article_id,
            AiAnalysisResult.source_updated_at == source_updated_at,
        )
        .values(published_at=now, updated_at=now)
    )
    return result.rowcount


async def fail(
    session: AsyncSession,
    *,
    article_id: str,
    error: str,
    source_updated_at: datetime,
    now: datetime,
) -> int:
    result = await session.execute(
        update(AiAnalysisResult)
        .where(
            AiAnalysisResult.article_id == article_id,
            AiAnalysisResult.source_updated_at == source_updated_at,
        )
        .values(
            status="error",
            last_error=error[:MAX_ERROR_CHARS],
            expires_at=None,
            updated_at=now,
        )
    )
    return result.rowcount
