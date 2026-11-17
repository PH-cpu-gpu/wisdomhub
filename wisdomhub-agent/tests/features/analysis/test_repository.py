"""分析仓储：五态 claim、并发、版本条件写入与发布标记。"""

import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.features.analysis.models import AnalysisResult
from app.features.analysis.repository import (
    LAST_ERROR_MAX,
    AnalysisRepository,
    ClaimOutcome,
    ClaimResult,
)

ROOT = Path(__file__).resolve().parents[3]
BASE = datetime(2026, 11, 17, 8, tzinfo=UTC)
LEASE = 1800
V1 = datetime(2026, 11, 16, 8, tzinfo=UTC)
V2 = V1 + timedelta(hours=1)
V3 = V2 + timedelta(hours=1)
ARTICLE = "a-1"
ANALYSIS = {
    "article_id": ARTICLE,
    "topic": "Redis 持久化",
    "entities": ["Redis"],
    "knowledge_points": ["RDB 与 AOF"],
}


def _config(url: str) -> Config:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "app/infra/db/migrations"))
    config.set_main_option("prepend_sys_path", str(ROOT))
    config.set_main_option("sqlalchemy.url", url)
    return config


@pytest.fixture
async def session_factory(
    pg_database: str,
) -> AsyncIterator[async_sessionmaker[AsyncSession]]:
    await asyncio.to_thread(command.upgrade, _config(pg_database), "head")
    engine = create_async_engine(pg_database, poolclass=NullPool)
    try:
        yield async_sessionmaker(engine, expire_on_commit=False, autoflush=False)
    finally:
        await engine.dispose()


async def _claim(
    session_factory: async_sessionmaker[AsyncSession],
    *,
    version: datetime = V2,
    now: datetime = BASE,
    lease_seconds: int = LEASE,
) -> ClaimResult:
    async with session_factory() as session, session.begin():
        return await AnalysisRepository(session).claim(
            ARTICLE, version, lease_seconds=lease_seconds, now=now
        )


async def _finish(
    session_factory: async_sessionmaker[AsyncSession],
    *,
    version: datetime = V2,
    analysis: dict[str, Any] = ANALYSIS,
) -> bool:
    async with session_factory() as session, session.begin():
        return await AnalysisRepository(session).finish(ARTICLE, version, analysis)


async def _fail(
    session_factory: async_sessionmaker[AsyncSession],
    *,
    version: datetime = V2,
    error: str = "boom",
) -> bool:
    async with session_factory() as session, session.begin():
        return await AnalysisRepository(session).fail(ARTICLE, version, error)


async def _mark_published(
    session_factory: async_sessionmaker[AsyncSession], *, version: datetime = V2
) -> bool:
    async with session_factory() as session, session.begin():
        return await AnalysisRepository(session).mark_published(ARTICLE, version)


async def _mark_notified(
    session_factory: async_sessionmaker[AsyncSession], *, version: datetime = V2
) -> bool:
    async with session_factory() as session, session.begin():
        return await AnalysisRepository(session).mark_notified(ARTICLE, version)


async def _get(session_factory: async_sessionmaker[AsyncSession]) -> AnalysisResult:
    async with session_factory() as session:
        row = await AnalysisRepository(session).get(ARTICLE)
    assert row is not None
    return row


async def _seed(session_factory: async_sessionmaker[AsyncSession], **values: Any) -> None:
    async with session_factory() as session, session.begin():
        session.add(AnalysisResult(article_id=ARTICLE, **values))


async def test_claim_inserts_new_row(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    result = await _claim(session_factory)

    assert result.outcome is ClaimOutcome.NEW
    row = result.row
    assert row.status == "in_progress"
    assert row.source_updated_at == V2
    assert row.attempts == 1
    assert row.expires_at == BASE + timedelta(seconds=LEASE)
    assert row.analysis is None
    assert (row.published_at, row.notified_at) == (None, None)
    assert (row.created_at, row.updated_at) == (BASE, BASE)


async def test_concurrent_claim_only_one_new(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    results = await asyncio.gather(
        _claim(session_factory),
        _claim(session_factory),
    )

    assert sorted(result.outcome for result in results) == [
        ClaimOutcome.IN_PROGRESS,
        ClaimOutcome.NEW,
    ]
    assert (await _get(session_factory)).attempts == 1


async def test_active_lease_returns_in_progress(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)

    result = await _claim(session_factory, now=BASE + timedelta(minutes=1))

    assert result.outcome is ClaimOutcome.IN_PROGRESS
    assert result.row.expires_at == BASE + timedelta(seconds=LEASE)


async def test_expired_lease_is_taken_over(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)
    later = BASE + timedelta(seconds=LEASE + 1)

    result = await _claim(session_factory, now=later)

    assert result.outcome is ClaimOutcome.NEW
    assert result.row.attempts == 2
    assert result.row.expires_at == later + timedelta(seconds=LEASE)


async def test_error_is_taken_over(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)
    assert await _fail(session_factory) is True

    result = await _claim(session_factory)

    assert result.outcome is ClaimOutcome.NEW
    assert result.row.status == "in_progress"
    assert result.row.last_error is None
    assert result.row.analysis is None
    assert result.row.attempts == 2


async def test_done_published_is_duplicate(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)
    assert await _finish(session_factory) is True
    assert await _mark_published(session_factory) is True

    result = await _claim(session_factory)

    assert result.outcome is ClaimOutcome.DUPLICATE
    assert result.row.analysis == ANALYSIS
    assert result.row.attempts == 1


async def test_done_unpublished_is_republish(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)
    assert await _finish(session_factory) is True

    result = await _claim(session_factory)

    assert result.outcome is ClaimOutcome.REPUBLISH
    assert result.row.status == "done"
    assert result.row.analysis == ANALYSIS
    assert result.row.published_at is None


async def test_older_version_is_stale(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory, version=V2)
    assert await _finish(session_factory, version=V2) is True

    result = await _claim(session_factory, version=V1)

    assert result.outcome is ClaimOutcome.STALE
    assert result.row.source_updated_at == V2
    assert result.row.status == "done"
    assert result.row.analysis == ANALYSIS


async def test_newer_version_takes_over(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory, version=V1)
    assert await _finish(session_factory, version=V1) is True
    assert await _mark_published(session_factory, version=V1) is True
    assert await _mark_notified(session_factory, version=V1) is True

    result = await _claim(session_factory, version=V3)

    assert result.outcome is ClaimOutcome.NEW
    assert result.row.source_updated_at == V3
    assert result.row.status == "in_progress"
    assert result.row.analysis is None
    assert (result.row.published_at, result.row.notified_at) == (None, None)
    assert result.row.attempts == 2


async def test_concurrent_takeover_only_one_new(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)
    later = BASE + timedelta(seconds=LEASE + 1)

    results = await asyncio.gather(
        _claim(session_factory, now=later),
        _claim(session_factory, now=later),
    )

    assert sorted(result.outcome for result in results) == [
        ClaimOutcome.IN_PROGRESS,
        ClaimOutcome.NEW,
    ]
    assert (await _get(session_factory)).attempts == 2


async def test_finish_only_current_version(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory, version=V2)

    assert await _finish(session_factory, version=V1) is False
    assert (await _get(session_factory)).status == "in_progress"
    assert await _finish(session_factory, version=V2) is True
    assert await _finish(session_factory, version=V2) is False


async def test_fail_truncates_error_and_clears_lease(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)

    assert await _fail(session_factory, error="x" * (LAST_ERROR_MAX + 200)) is True

    row = await _get(session_factory)
    assert row.status == "error"
    assert row.last_error == "x" * LAST_ERROR_MAX
    assert row.expires_at is None
    assert await _finish(session_factory) is False


async def test_mark_published_only_once(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)
    assert await _mark_published(session_factory) is False

    assert await _finish(session_factory) is True
    assert await _mark_published(session_factory) is True
    assert await _mark_published(session_factory) is False


async def test_mark_notified_requires_published_and_only_once(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _claim(session_factory)
    assert await _finish(session_factory) is True
    assert await _mark_notified(session_factory) is False

    assert await _mark_published(session_factory) is True
    assert await _mark_notified(session_factory) is True
    assert await _mark_notified(session_factory) is False


async def test_row_without_version_is_taken_over(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _seed(
        session_factory,
        status="done",
        analysis=ANALYSIS,
        attempts=1,
        source_updated_at=None,
        created_at=BASE,
        updated_at=BASE,
    )

    result = await _claim(session_factory, version=V2)

    assert result.outcome is ClaimOutcome.NEW
    assert result.row.status == "in_progress"
    assert result.row.source_updated_at == V2
    assert result.row.attempts == 2
    assert result.row.analysis is None


async def test_get_missing_returns_none(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    async with session_factory() as session:
        assert await AnalysisRepository(session).get("missing") is None
