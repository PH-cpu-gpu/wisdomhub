"""会话 REST：分页、归属 404、级联删除与参数校验。"""

import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

import pytest
from alembic import command
from alembic.config import Config
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.contracts.workflow import WorkflowKind
from app.core.config import Settings
from app.core.deps import AppDeps
from app.core.errors import register_exception_handlers
from app.features.conversation.api import router
from app.features.conversation.models import AgentMessage
from app.features.conversation.repository import ConversationRepository, TerminalStatus
from app.features.conversation.service import ConversationService

ROOT = Path(__file__).resolve().parents[3]
BASE = datetime(2026, 11, 16, 8, 0, tzinfo=UTC)
LEASE = BASE + timedelta(minutes=5)
TTL = 300
URL = "/api/agent/conversations"
USER = {"X-User-Id": "1"}


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


def _app(deps: AppDeps) -> FastAPI:
    app = FastAPI()
    app.state.deps = deps
    register_exception_handlers(app)
    app.include_router(router)
    return app


@pytest.fixture
async def client(
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> AsyncIterator[AsyncClient]:
    deps = AppDeps(
        settings=settings,
        conversation=ConversationService(session_factory, ttl_seconds=TTL),
    )
    transport = ASGITransport(app=_app(deps))
    async with AsyncClient(transport=transport, base_url="http://test") as value:
        yield value


async def _create(
    session_factory: async_sessionmaker[AsyncSession],
    *,
    user_id: int = 1,
    title: str = "会话",
    when: datetime | None = None,
) -> UUID:
    async with session_factory() as session, session.begin():
        repo = ConversationRepository(session)
        conversation = await repo.create_conversation(user_id, title)
        if when is not None:
            await repo.touch_conversation(conversation.id, when=when)
        return conversation.id


async def _seed_turn(
    session_factory: async_sessionmaker[AsyncSession],
    conversation_id: UUID,
    *,
    user_id: int = 1,
    message_id: str = "m-1",
    workflow: WorkflowKind = WorkflowKind.QA,
    question: str = "问题",
    answer: str = "回答",
    steps: list[dict[str, Any]] | None = None,
    artifacts: dict[str, Any] | None = None,
) -> None:
    async with session_factory() as session, session.begin():
        repo = ConversationRepository(session)
        await repo.insert_user_message(conversation_id, user_id, message_id, workflow, question)
        await repo.insert_assistant_placeholder(
            conversation_id, user_id, message_id, workflow, LEASE
        )
        finished = await repo.finish_assistant(
            conversation_id,
            message_id,
            status="done",
            content=answer,
            steps=steps,
            artifacts=artifacts,
            workflow=workflow,
            lease_expires_at=LEASE,
        )
        assert finished is True


async def _count_messages(
    session_factory: async_sessionmaker[AsyncSession], conversation_id: UUID
) -> int:
    async with session_factory() as session:
        total = await session.scalar(
            select(func.count())
            .select_from(AgentMessage)
            .where(AgentMessage.conversation_id == conversation_id)
        )
    return total or 0


@pytest.mark.pg
async def test_list_paginates_orders_and_scopes(
    client: AsyncClient, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    first = await _create(session_factory, title="第一", when=BASE)
    second = await _create(session_factory, title="第二", when=BASE + timedelta(seconds=1))
    third = await _create(session_factory, title="第三", when=BASE + timedelta(seconds=2))
    await _create(session_factory, user_id=2, title="他人", when=BASE + timedelta(seconds=3))

    first_page = (await client.get(URL, params={"page": 1, "pageSize": 2}, headers=USER)).json()
    second_page = (await client.get(URL, params={"page": 2, "pageSize": 2}, headers=USER)).json()
    beyond = (await client.get(URL, params={"page": 3, "pageSize": 2}, headers=USER)).json()

    assert first_page["code"] == 200
    data = first_page["data"]
    assert (data["total"], data["pages"], data["page"], data["pageSize"]) == (3, 2, 1, 2)
    assert [item["id"] for item in data["items"]] == [str(third), str(second)]
    assert set(data["items"][0]) == {"id", "title", "workflow", "createdAt", "updatedAt"}
    assert [item["id"] for item in second_page["data"]["items"]] == [str(first)]
    assert beyond["data"]["items"] == []


@pytest.mark.pg
async def test_list_defaults_and_empty_page(client: AsyncClient) -> None:
    body = (await client.get(URL, headers=USER)).json()

    assert body["code"] == 200
    assert (body["data"]["total"], body["data"]["pages"]) == (0, 0)
    assert (body["data"]["page"], body["data"]["pageSize"]) == (1, 10)
    assert body["data"]["items"] == []


@pytest.mark.pg
async def test_detail_derives_workflow_and_maps_messages(
    client: AsyncClient, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    conversation = await _create(session_factory, title="学习路线")
    await _seed_turn(
        session_factory, conversation, message_id="m-1", question="怎么学", answer="先学基础"
    )
    await _seed_turn(
        session_factory,
        conversation,
        message_id="m-2",
        workflow=WorkflowKind.LEARNING_PATH,
        question="规划 Redis",
        answer="路线如下",
        steps=[
            {
                "id": "s1",
                "tool": "query_prerequisite_chain",
                "label": "查询前置链",
                "status": "done",
            }
        ],
        artifacts={
            "route": {
                "target": {"tagId": "10", "name": "Redis"},
                "known": ["Java"],
                "truncated": False,
                "stages": [],
            }
        },
    )

    body = (await client.get(f"{URL}/{conversation}", headers=USER)).json()
    detail = body["data"]

    assert body["code"] == 200
    assert detail["workflow"] == 2
    assert [(item["role"], item["content"]) for item in detail["messages"]] == [
        ("user", "怎么学"),
        ("assistant", "先学基础"),
        ("user", "规划 Redis"),
        ("assistant", "路线如下"),
    ]
    last = detail["messages"][-1]
    assert last["workflow"] == 2
    assert last["status"] == "done"
    assert last["steps"][0]["status"] == "done"
    assert last["artifacts"]["route"]["target"]["tagId"] == "10"
    assert detail["messages"][0]["steps"] is None
    assert detail["messages"][0]["artifacts"] is None


@pytest.mark.pg
async def test_detail_returns_404_for_missing_or_foreign(
    client: AsyncClient, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    foreign = await _create(session_factory, user_id=2)

    for conversation_id in (foreign, uuid4()):
        body = (await client.get(f"{URL}/{conversation_id}", headers=USER)).json()
        assert body == {"code": 404, "message": "会话不存在", "data": None}


@pytest.mark.pg
async def test_delete_cascades_and_hides_others(
    client: AsyncClient, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    owned = await _create(session_factory, title="我的")
    foreign = await _create(session_factory, user_id=2, title="他人")
    await _seed_turn(session_factory, owned)
    await _seed_turn(session_factory, foreign, user_id=2)

    denied = (await client.delete(f"{URL}/{foreign}", headers=USER)).json()
    assert denied == {"code": 404, "message": "会话不存在", "data": None}
    assert await _count_messages(session_factory, foreign) == 2

    deleted = (await client.delete(f"{URL}/{owned}", headers=USER)).json()
    assert deleted == {"code": 200, "message": "ok", "data": None}
    assert await _count_messages(session_factory, owned) == 0

    again = (await client.delete(f"{URL}/{owned}", headers=USER)).json()
    assert again == {"code": 404, "message": "会话不存在", "data": None}


@pytest.mark.pg
@pytest.mark.parametrize(
    "params",
    [
        {"page": 0},
        {"page": -1},
        {"pageSize": 0},
        {"pageSize": 101},
        {"page": "abc"},
        {"pageSize": "abc"},
    ],
)
async def test_list_rejects_invalid_pagination(
    client: AsyncClient, params: dict[str, str | int]
) -> None:
    body = (await client.get(URL, params=params, headers=USER)).json()

    assert body["code"] == 400


@pytest.mark.pg
@pytest.mark.parametrize("headers", [{}, {"X-User-Id": ""}, {"X-User-Id": "abc"}])
async def test_requires_valid_user_header(client: AsyncClient, headers: dict[str, str]) -> None:
    body = (await client.get(URL, headers=headers)).json()

    assert body["code"] == 401


@pytest.mark.pg
async def test_invalid_conversation_id_is_bad_request(client: AsyncClient) -> None:
    body = (await client.get(f"{URL}/not-a-uuid", headers=USER)).json()

    assert body["code"] == 400


@pytest.mark.pg
async def test_unavailable_service_returns_503(settings: Settings) -> None:
    transport = ASGITransport(app=_app(AppDeps(settings=settings)))
    async with AsyncClient(transport=transport, base_url="http://test") as raw:
        body = (await raw.get(URL, headers=USER)).json()

    assert body["code"] == 503


@pytest.mark.pg
@pytest.mark.parametrize("status", ["aborted", "error"])
async def test_detail_exposes_in_progress_and_terminal_status(
    client: AsyncClient,
    session_factory: async_sessionmaker[AsyncSession],
    status: TerminalStatus,
) -> None:
    conversation = await _create(session_factory)
    async with session_factory() as session, session.begin():
        repo = ConversationRepository(session)
        await repo.insert_user_message(conversation, 1, "m-1", WorkflowKind.QA, "进行中")
        await repo.insert_assistant_placeholder(conversation, 1, "m-1", WorkflowKind.QA, LEASE)
        await repo.insert_user_message(conversation, 1, "m-2", WorkflowKind.QA, "结束")
        await repo.insert_assistant_placeholder(conversation, 1, "m-2", WorkflowKind.QA, LEASE)
        finished = await repo.finish_assistant(
            conversation,
            "m-2",
            status=status,
            content="半截",
            steps=None,
            artifacts=None,
            workflow=WorkflowKind.QA,
            lease_expires_at=LEASE,
        )
        assert finished is True

    messages = (await client.get(f"{URL}/{conversation}", headers=USER)).json()["data"]["messages"]
    by_turn = {(item["messageId"], item["role"]): item for item in messages}
    assert by_turn[("m-1", "assistant")]["status"] == "in_progress"
    assert by_turn[("m-1", "assistant")]["content"] == ""
    assert by_turn[("m-2", "assistant")]["status"] == status
    assert by_turn[("m-2", "assistant")]["content"] == "半截"


@pytest.mark.pg
async def test_delete_removes_from_list_and_detail(
    client: AsyncClient, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    removed = await _create(session_factory, title="待删", when=BASE)
    kept = await _create(session_factory, title="保留", when=BASE + timedelta(seconds=1))

    await client.delete(f"{URL}/{removed}", headers=USER)

    listing = (await client.get(URL, headers=USER)).json()["data"]
    assert listing["total"] == 1
    assert [item["id"] for item in listing["items"]] == [str(kept)]
    detail = (await client.get(f"{URL}/{removed}", headers=USER)).json()
    assert detail["code"] == 404


@pytest.mark.pg
async def test_list_order_tie_breaks_by_id(
    client: AsyncClient, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    first = await _create(session_factory, title="同刻 A", when=BASE)
    second = await _create(session_factory, title="同刻 B", when=BASE)

    body = (await client.get(URL, headers=USER)).json()

    expected = [str(value) for value in sorted((first, second), reverse=True)]
    assert [item["id"] for item in body["data"]["items"]] == expected


@pytest.mark.pg
async def test_finish_moves_conversation_to_top(
    client: AsyncClient, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    older = await _create(session_factory, title="旧会话", when=BASE)
    newer = await _create(session_factory, title="新会话", when=BASE + timedelta(seconds=1))
    async with session_factory() as session, session.begin():
        repo = ConversationRepository(session)
        await repo.insert_user_message(older, 1, "m-1", WorkflowKind.QA, "问题")
        await repo.insert_assistant_placeholder(older, 1, "m-1", WorkflowKind.QA, LEASE)
        finished = await repo.finish_assistant(
            older,
            "m-1",
            status="done",
            content="回答",
            steps=None,
            artifacts=None,
            workflow=WorkflowKind.QA,
            lease_expires_at=LEASE,
            now=BASE + timedelta(seconds=10),
        )
        assert finished is True

    body = (await client.get(URL, headers=USER)).json()

    assert [item["id"] for item in body["data"]["items"]] == [str(older), str(newer)]


@pytest.mark.pg
async def test_page_size_upper_bound_accepted(
    client: AsyncClient, session_factory: async_sessionmaker[AsyncSession]
) -> None:
    await _create(session_factory)

    data = (await client.get(URL, params={"pageSize": 100}, headers=USER)).json()["data"]

    assert (data["pageSize"], data["pages"]) == (100, 1)
