"""分析读接口：状态形状、懒触发、404 与依赖缺失。"""

import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any

import pytest
from alembic import command
from alembic.config import Config
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.capabilities.content.client import CONTENT_SERVICE, ArticleContentClient
from app.core.config import Settings
from app.core.deps import AppDeps
from app.core.errors import register_exception_handlers
from app.features.analysis.api import router
from app.features.analysis.models import AnalysisResult
from app.features.analysis.repository import AnalysisRepository
from app.features.analysis.service import ERROR_MESSAGE, AnalysisService
from app.infra.http.internal_client import InternalNotFound, InternalUnavailable
from tests.fakes import FakeInternalClient

ROOT = Path(__file__).resolve().parents[3]
ARTICLE = "1001"
VERSION = datetime(2026, 11, 17, 8, tzinfo=UTC)
NOW = datetime(2026, 11, 18, 8, tzinfo=UTC)
LEASE = 1800
URL = f"/api/agent/analysis/{ARTICLE}"
SNAPSHOT_PATH = f"/internal/content/article/{ARTICLE}"
ANALYSIS = {
    "article_id": ARTICLE,
    "topic": "Redis 持久化",
    "entities": ["Redis", "AOF"],
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


def _snapshot(**overrides: Any) -> dict[str, Any]:
    data: dict[str, Any] = {
        "articleId": ARTICLE,
        "authorId": "2001",
        "title": "Redis AOF 重写",
        "summary": "摘要",
        "state": 2,
        "createTime": "2026-11-15T08:00:00Z",
        "publishTime": "2026-11-15T09:00:00Z",
        "updateTime": "2026-11-17T08:00:00Z",
        "viewCount": 12,
        "tags": [{"tagId": "3001", "name": "Redis", "category": "STORAGE"}],
        "markdown": "# 正文",
        "locked": False,
    }
    data.update(overrides)
    return data


@pytest.fixture
def internal() -> FakeInternalClient:
    return FakeInternalClient({(CONTENT_SERVICE, SNAPSHOT_PATH): _snapshot()})


class _Spawn:
    def __init__(self) -> None:
        self.tasks: list[asyncio.Task[Any]] = []

    def __call__(self, coro: Any, *, name: str) -> asyncio.Task[Any]:
        task = asyncio.create_task(coro, name=name)
        self.tasks.append(task)
        return task

    async def drain(self) -> None:
        while self.tasks:
            await asyncio.gather(*self.tasks)
            self.tasks = [task for task in self.tasks if not task.done()]


class _Job:
    """按真实 Job 的写入路径落库：claim → finish。"""

    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        *,
        gate: asyncio.Event | None = None,
    ) -> None:
        self._session_factory = session_factory
        self._gate = gate
        self.calls: list[tuple[str, datetime, str | None]] = []
        self.started = asyncio.Event()

    async def run(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        author_id: str | None = None,
    ) -> str:
        self.calls.append((article_id, source_updated_at, author_id))
        self.started.set()
        if self._gate is not None:
            await self._gate.wait()
        async with self._session_factory() as session, session.begin():
            repository = AnalysisRepository(session)
            await repository.claim(article_id, source_updated_at, lease_seconds=LEASE, now=NOW)
            await repository.finish(article_id, source_updated_at, ANALYSIS, now=NOW)
        return "ack"


async def _seed(
    session_factory: async_sessionmaker[AsyncSession],
    *,
    status: str,
    analysis: dict[str, Any] | None = None,
    source_updated_at: datetime | None = VERSION,
    expires_at: datetime | None = None,
    last_error: str | None = None,
) -> None:
    async with session_factory() as session, session.begin():
        session.add(
            AnalysisResult(
                article_id=ARTICLE,
                status=status,
                analysis=analysis,
                last_error=last_error,
                attempts=1,
                source_updated_at=source_updated_at,
                expires_at=expires_at,
                created_at=NOW,
                updated_at=NOW,
            )
        )


def _app(deps: AppDeps) -> FastAPI:
    app = FastAPI()
    app.state.deps = deps
    register_exception_handlers(app)
    app.include_router(router)
    return app


def _client(deps: AppDeps) -> AsyncClient:
    return AsyncClient(transport=ASGITransport(app=_app(deps)), base_url="http://test")


async def _get(
    client: AsyncClient,
    *,
    article: str = ARTICLE,
    trigger: bool = False,
) -> dict[str, Any]:
    params = {"trigger": "true"} if trigger else None
    response = await client.get(f"/api/agent/analysis/{article}", params=params)
    assert response.status_code == 200
    return response.json()


def _service(
    session_factory: async_sessionmaker[AsyncSession],
    content: ArticleContentClient,
    job: _Job,
    spawn: _Spawn,
) -> AnalysisService:
    return AnalysisService(
        session_factory,
        content,
        job,
        spawn=spawn,
        clock=lambda: NOW,
    )


async def test_missing_row_is_pending(
    settings: Settings, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(FakeInternalClient()), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client)

    assert body["code"] == 200
    assert body["data"] == {
        "articleId": ARTICLE,
        "status": "pending",
        "topic": None,
        "entities": None,
        "knowledgePoints": None,
        "sourceUpdatedAt": None,
        "analyzedAt": None,
        "message": None,
    }
    assert job.calls == []


async def test_in_progress_is_pending_with_version(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
    internal: FakeInternalClient,
) -> None:
    await _seed(
        session_factory,
        status="in_progress",
        expires_at=NOW + timedelta(seconds=LEASE),
    )
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client)

    assert body["data"]["status"] == "pending"
    assert body["data"]["sourceUpdatedAt"] == "2026-11-17T08:00:00Z"


async def test_done_row_returns_analysis_fields(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _seed(session_factory, status="done", analysis=ANALYSIS)
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(FakeInternalClient()), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client)

    assert body["data"] == {
        "articleId": ARTICLE,
        "status": "done",
        "topic": "Redis 持久化",
        "entities": ["Redis", "AOF"],
        "knowledgePoints": ["RDB 与 AOF"],
        "sourceUpdatedAt": "2026-11-17T08:00:00Z",
        "analyzedAt": "2026-11-18T08:00:00Z",
        "message": None,
    }


async def test_error_row_hides_last_error(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    await _seed(
        session_factory,
        status="error",
        last_error="InternalServerError: upstream exploded",
    )
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(FakeInternalClient()), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client)

    assert body["data"]["status"] == "error"
    assert body["data"]["message"] == ERROR_MESSAGE
    assert "upstream" not in str(body)


async def test_trigger_false_does_not_touch_snapshot(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
    internal: FakeInternalClient,
) -> None:
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client)

    assert body["data"]["status"] == "pending"
    assert internal.calls == []
    assert job.calls == []


async def test_trigger_runs_job_and_returns_pending(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
    internal: FakeInternalClient,
) -> None:
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        triggered = await _get(client, trigger=True)
        await asyncio.wait_for(job.started.wait(), 1)
        assert triggered["data"]["status"] == "pending"
        assert triggered["data"]["sourceUpdatedAt"] == "2026-11-17T08:00:00Z"
        assert triggered["data"]["topic"] is None
        assert job.calls == [(ARTICLE, VERSION, "2001")]
        assert internal.calls[0].path == SNAPSHOT_PATH

        await spawn.drain()
        done = await _get(client)

    assert done["data"]["status"] == "done"
    assert done["data"]["topic"] == "Redis 持久化"


async def test_trigger_skips_done_row(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
    internal: FakeInternalClient,
) -> None:
    await _seed(session_factory, status="done", analysis=ANALYSIS)
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client, trigger=True)

    assert body["data"]["status"] == "done"
    assert internal.calls == []
    assert job.calls == []


async def test_trigger_takes_over_expired_lease(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
    internal: FakeInternalClient,
) -> None:
    await _seed(
        session_factory,
        status="in_progress",
        expires_at=NOW - timedelta(seconds=1),
    )
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client, trigger=True)
        await asyncio.wait_for(job.started.wait(), 1)

    assert body["data"]["status"] == "pending"
    assert job.calls == [(ARTICLE, VERSION, "2001")]


async def test_trigger_unknown_article_returns_404(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    internal = FakeInternalClient(
        error=InternalNotFound("missing", service=CONTENT_SERVICE, path=SNAPSHOT_PATH)
    )
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client, trigger=True)

    assert body["code"] == 404
    assert job.calls == []


async def test_trigger_unpublished_article_stays_pending(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, SNAPSHOT_PATH): _snapshot(state=1)})
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client, trigger=True)

    assert body["data"]["status"] == "pending"
    assert job.calls == []


async def test_trigger_snapshot_failure_stays_pending(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    internal = FakeInternalClient(
        error=InternalUnavailable("down", service=CONTENT_SERVICE, path=SNAPSHOT_PATH)
    )
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client, trigger=True)

    assert body["data"]["status"] == "pending"
    assert body["data"]["sourceUpdatedAt"] is None
    assert job.calls == []


async def test_trigger_dedupes_inflight_job(
    settings: Settings,
    session_factory: async_sessionmaker[AsyncSession],
    internal: FakeInternalClient,
) -> None:
    gate = asyncio.Event()
    spawn = _Spawn()
    job = _Job(session_factory, gate=gate)
    service = _service(session_factory, ArticleContentClient(internal), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        first = await _get(client, trigger=True)
        second = await _get(client, trigger=True)
        await asyncio.wait_for(job.started.wait(), 1)

    assert first["data"]["status"] == "pending"
    assert second["data"]["status"] == "pending"
    assert job.calls == [(ARTICLE, VERSION, "2001")]

    gate.set()
    await spawn.drain()


async def test_missing_service_returns_503(settings: Settings) -> None:
    async with _client(AppDeps(settings=settings)) as client:
        body = await _get(client)

    assert body["code"] == 503


async def test_article_id_too_long_is_rejected(
    settings: Settings, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    spawn = _Spawn()
    job = _Job(session_factory)
    service = _service(session_factory, ArticleContentClient(FakeInternalClient()), job, spawn)

    async with _client(AppDeps(settings=settings, analysis=service)) as client:
        body = await _get(client, article="a" * 65)

    assert body["code"] == 400
