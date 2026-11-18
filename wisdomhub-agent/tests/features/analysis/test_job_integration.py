"""分析 Job 集成测试：真实 PostgreSQL 仓储上的发布幂等与通知补发。"""

import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, cast

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.features.analysis import job as job_module
from app.features.analysis.job import AnalysisJob, JobResult
from app.features.analysis.models import AnalysisResult
from app.features.analysis.pipeline import ArticleAnalysis
from app.features.analysis.repository import AnalysisRepository
from app.infra.mq.topology import AI_COMPLETED, KNOWLEDGE_EXTRACT
from tests.fakes import FakePublisher

ROOT = Path(__file__).resolve().parents[3]
ARTICLE = "1001"
AUTHOR = "2001"
VERSION = datetime(2026, 11, 17, 8, tzinfo=UTC)
NOW = datetime(2026, 11, 18, 8, tzinfo=UTC)
LEASE = 1800
ANALYSIS = ArticleAnalysis(
    article_id=ARTICLE,
    topic="Redis 持久化",
    entities=["Redis", "AOF"],
    knowledge_points=["RDB 与 AOF"],
)


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


class _Pipeline:
    def __init__(self) -> None:
        self.calls: list[str] = []

    async def __call__(
        self,
        client: Any,
        llm: Any,
        article_id: str,
        *,
        max_article_tokens: int,
        structured_retries: int,
    ) -> ArticleAnalysis:
        self.calls.append(article_id)
        return ANALYSIS


async def _no_sleep(_: float) -> None:
    return None


def _job(session_factory: async_sessionmaker[AsyncSession]) -> tuple[AnalysisJob, FakePublisher]:
    publisher = FakePublisher()
    job = AnalysisJob(
        session_factory,
        cast(Any, object()),
        cast(Any, object()),
        publisher,
        retry_times=1,
        lease_seconds=LEASE,
        max_article_tokens=24000,
        structured_retries=0,
        clock=lambda: NOW,
        sleep=_no_sleep,
    )
    return job, publisher


def _use_pipeline(monkeypatch: pytest.MonkeyPatch) -> _Pipeline:
    pipeline = _Pipeline()
    monkeypatch.setattr(job_module, "run_pipeline", pipeline)
    return pipeline


async def _seed(session_factory: async_sessionmaker[AsyncSession], **values: Any) -> None:
    async with session_factory() as session, session.begin():
        session.add(
            AnalysisResult(
                article_id=ARTICLE,
                created_at=NOW,
                updated_at=NOW,
                **values,
            )
        )


async def _row(session_factory: async_sessionmaker[AsyncSession]) -> AnalysisResult:
    async with session_factory() as session:
        row = await AnalysisRepository(session).get(ARTICLE)
    assert row is not None
    return row


async def test_first_run_persists_and_publishes_both_events(
    monkeypatch: pytest.MonkeyPatch,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    job, publisher = _job(session_factory)
    pipeline = _use_pipeline(monkeypatch)

    result = await job.run(ARTICLE, VERSION, author_id=AUTHOR)

    assert result is JobResult.ACK
    assert pipeline.calls == [ARTICLE]
    row = await _row(session_factory)
    assert row.status == "done"
    assert row.analysis == ANALYSIS.model_dump()
    assert row.source_updated_at == VERSION
    assert row.published_at is not None
    assert row.notified_at is not None
    assert row.attempts == 1
    assert row.expires_at is None
    assert [message.routing_key for message in publisher.messages] == [
        KNOWLEDGE_EXTRACT,
        AI_COMPLETED,
    ]
    assert publisher.messages[0].payload == {
        "articleId": 1001,
        "entities": [{"name": "Redis"}, {"name": "AOF"}],
    }
    assert publisher.messages[1].payload == {"articleId": 1001, "authorId": 2001}


async def test_redelivery_does_not_rerun_or_republish(
    monkeypatch: pytest.MonkeyPatch,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    job, publisher = _job(session_factory)
    pipeline = _use_pipeline(monkeypatch)
    assert await job.run(ARTICLE, VERSION, author_id=AUTHOR) is JobResult.ACK
    before = await _row(session_factory)

    assert await job.run(ARTICLE, VERSION, author_id=AUTHOR) is JobResult.ACK

    assert pipeline.calls == [ARTICLE]
    assert len(publisher.messages) == 2
    after = await _row(session_factory)
    assert (after.published_at, after.notified_at, after.updated_at) == (
        before.published_at,
        before.notified_at,
        before.updated_at,
    )


async def test_duplicate_repairs_missing_notification(
    monkeypatch: pytest.MonkeyPatch,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _seed(
        session_factory,
        status="done",
        analysis=ANALYSIS.model_dump(),
        attempts=1,
        source_updated_at=VERSION,
        published_at=NOW,
        notified_at=None,
    )
    job, publisher = _job(session_factory)
    pipeline = _use_pipeline(monkeypatch)

    result = await job.run(ARTICLE, VERSION, author_id=AUTHOR)

    assert result is JobResult.ACK
    assert pipeline.calls == []
    assert [message.routing_key for message in publisher.messages] == [AI_COMPLETED]
    row = await _row(session_factory)
    assert row.published_at == NOW
    assert row.notified_at is not None


async def test_republish_after_crash_before_publish(
    monkeypatch: pytest.MonkeyPatch,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _seed(
        session_factory,
        status="done",
        analysis=ANALYSIS.model_dump(),
        attempts=1,
        source_updated_at=VERSION,
        published_at=None,
        notified_at=None,
    )
    job, publisher = _job(session_factory)
    pipeline = _use_pipeline(monkeypatch)

    result = await job.run(ARTICLE, VERSION, author_id=AUTHOR)

    assert result is JobResult.ACK
    assert pipeline.calls == []
    assert [message.routing_key for message in publisher.messages] == [
        KNOWLEDGE_EXTRACT,
        AI_COMPLETED,
    ]
    row = await _row(session_factory)
    assert row.published_at is not None
    assert row.notified_at is not None
    assert row.analysis == ANALYSIS.model_dump()


async def test_error_row_is_reanalyzed(
    monkeypatch: pytest.MonkeyPatch,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _seed(
        session_factory,
        status="error",
        analysis=None,
        last_error="boom",
        attempts=1,
        source_updated_at=VERSION,
    )
    job, publisher = _job(session_factory)
    pipeline = _use_pipeline(monkeypatch)

    result = await job.run(ARTICLE, VERSION, author_id=AUTHOR)

    assert result is JobResult.ACK
    assert pipeline.calls == [ARTICLE]
    row = await _row(session_factory)
    assert row.status == "done"
    assert row.last_error is None
    assert row.attempts == 2
    assert row.published_at is not None
    assert row.notified_at is not None
    assert [message.routing_key for message in publisher.messages] == [
        KNOWLEDGE_EXTRACT,
        AI_COMPLETED,
    ]
