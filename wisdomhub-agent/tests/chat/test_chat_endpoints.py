"""聊天端点：请求绑定、SSE 头与帧、依赖 503 与落库。"""

import asyncio
import json
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

import pytest
from alembic import command
from alembic.config import Config
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from langgraph.graph.state import CompiledStateGraph
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.capabilities.knowledge.tools import KNOWLEDGE_SERVICE, KnowledgeTools
from app.chat.api import router
from app.chat.graph.build import build_graph
from app.contracts.workflow import WorkflowKind
from app.core.config import Settings
from app.core.deps import AppDeps
from app.core.errors import register_exception_handlers
from app.features.conversation.models import AgentConversation, AgentMessage
from app.features.conversation.service import ConversationService
from tests.fakes import FakeInternalClient, FakeLLM

ROOT = Path(__file__).resolve().parents[2]
PATH = "/internal/knowledge/prerequisites"
TTL = 300
USER = {"X-User-Id": "1"}
QA_URL = "/api/agent/chat"
LEARNING_URL = "/api/agent/learning-path"
TEXT_QA_URL = "/api/agent/text-qa"


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


@pytest.fixture
def conversation(
    session_factory: async_sessionmaker[AsyncSession],
) -> ConversationService:
    return ConversationService(session_factory, ttl_seconds=TTL)


def _deps(
    settings: Settings,
    conversation: ConversationService,
    *,
    graph: CompiledStateGraph | None = None,
    knowledge_tools: KnowledgeTools | None = None,
) -> AppDeps:
    return AppDeps(
        settings=settings,
        conversation=conversation,
        graph=graph,
        knowledge_tools=knowledge_tools,
    )


def _app(deps: AppDeps) -> FastAPI:
    app = FastAPI()
    app.state.deps = deps
    register_exception_handlers(app)
    app.include_router(router)
    return app


def _client(deps: AppDeps) -> AsyncClient:
    return AsyncClient(transport=ASGITransport(app=_app(deps)), base_url="http://test")


def _frames(body: str) -> list[tuple[str, dict[str, Any]]]:
    frames: list[tuple[str, dict[str, Any]]] = []
    for block in body.strip().split("\n\n"):
        name: str | None = None
        data: dict[str, Any] = {}
        for line in block.splitlines():
            if line.startswith("event: "):
                name = line.removeprefix("event: ")
            elif line.startswith("data: "):
                data = json.loads(line.removeprefix("data: "))
        if name is not None:
            frames.append((name, data))
    return frames


def _payload() -> dict[str, Any]:
    return {
        "tagId": "3001",
        "name": "Redis",
        "depth": 0,
        "levels": [
            {"depth": 1, "tags": [{"tagId": "3002", "name": "数据结构"}]},
            {"depth": 2, "tags": [{"tagId": "3003", "name": "内存管理"}]},
        ],
        "truncated": False,
    }


async def _assistant(
    session_factory: async_sessionmaker[AsyncSession],
    conversation_id: UUID,
    message_id: str,
) -> AgentMessage:
    async with session_factory() as session:
        row = await session.scalar(
            select(AgentMessage).where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.message_id == message_id,
                AgentMessage.role == "assistant",
            )
        )
    assert row is not None
    return row


async def _user(
    session_factory: async_sessionmaker[AsyncSession],
    conversation_id: UUID,
    message_id: str,
) -> AgentMessage:
    async with session_factory() as session:
        row = await session.scalar(
            select(AgentMessage).where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.message_id == message_id,
                AgentMessage.role == "user",
            )
        )
    assert row is not None
    return row


async def test_qa_endpoint_streams_sse_and_persists(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    llm = FakeLLM(["Redis 支持 RDB 和 AOF。"])
    deps = _deps(settings, conversation, graph=build_graph(llm, None))

    async with _client(deps) as client:
        response = await client.post(
            QA_URL,
            json={"question": "Redis 怎么持久化？", "messageId": "m-1"},
            headers=USER,
        )

    assert response.status_code == 200
    assert response.headers["content-type"].startswith("text/event-stream")
    assert response.headers["cache-control"] == "no-cache"
    assert response.headers["x-accel-buffering"] == "no"
    frames = _frames(response.text)
    assert [name for name, _ in frames] == ["meta", "delta", "done"]
    assert frames[1][1]["text"] == "Redis 支持 RDB 和 AOF。"
    meta = frames[0][1]
    assert meta["messageId"] == "m-1"
    assert meta["workflow"] == WorkflowKind.QA
    row = await _assistant(session_factory, UUID(meta["conversationId"]), "m-1")
    assert row.status == "done"


async def test_learning_endpoint_streams_route_and_steps(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    tools = KnowledgeTools(FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload()}))
    llm = FakeLLM(["先学数据结构。"])
    deps = _deps(
        settings,
        conversation,
        graph=build_graph(llm, tools),
        knowledge_tools=tools,
    )

    async with _client(deps) as client:
        response = await client.post(
            LEARNING_URL,
            json={
                "question": "我要学 Redis",
                "technology": "Redis",
                "knownTechnologies": ["Java"],
                "messageId": "m-1",
            },
            headers=USER,
        )

    frames = _frames(response.text)
    assert [name for name, _ in frames] == ["meta", "route", "steps", "delta", "done"]
    assert frames[1][1]["target"]["tagId"] == "3001"
    assert frames[2][1]["steps"][0]["status"] == "done"
    assert frames[3][1]["text"] == "先学数据结构。"


async def test_text_endpoint_wraps_selected_text_and_stores_question(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    llm = FakeLLM(["这段文本介绍了 Redis 的持久化。"])
    deps = _deps(settings, conversation, graph=build_graph(llm, None))

    async with _client(deps) as client:
        response = await client.post(
            TEXT_QA_URL,
            json={
                "question": "这段讲了什么？",
                "text": "Redis 支持 RDB 和 AOF。",
                "messageId": "m-1",
            },
            headers=USER,
        )

    frames = _frames(response.text)
    assert [name for name, _ in frames] == ["meta", "delta", "done"]
    sent = llm.stream_calls[0]
    human = sent[-1]
    assert "<selected_text>Redis 支持 RDB 和 AOF。</selected_text>" in str(human.content)
    meta = frames[0][1]
    user = await _user(session_factory, UUID(meta["conversationId"]), "m-1")
    assert user.content == "这段讲了什么？"


@pytest.mark.parametrize(
    ("url", "payload"),
    [
        (QA_URL, {"question": "问题", "text": "多余字段"}),
        (QA_URL, {"question": "问题", "technology": "Redis"}),
        (LEARNING_URL, {"question": "问题"}),
        (TEXT_QA_URL, {"question": "问题"}),
    ],
)
async def test_request_models_are_isolated(
    conversation: ConversationService,
    settings: Settings,
    url: str,
    payload: dict[str, Any],
) -> None:
    tools = KnowledgeTools(FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload()}))
    deps = _deps(
        settings,
        conversation,
        graph=build_graph(FakeLLM(["答案"]), tools),
        knowledge_tools=tools,
    )

    async with _client(deps) as client:
        response = await client.post(url, json=payload, headers=USER)

    assert response.status_code == 200
    assert response.json()["code"] == 400


async def test_missing_user_header_returns_401(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    deps = _deps(settings, conversation, graph=build_graph(FakeLLM(["答案"]), None))

    async with _client(deps) as client:
        response = await client.post(QA_URL, json={"question": "问题"})

    assert response.status_code == 200
    assert response.json()["code"] == 401


async def test_missing_graph_returns_503(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    deps = _deps(settings, conversation)

    async with _client(deps) as client:
        response = await client.post(QA_URL, json={"question": "问题"}, headers=USER)

    assert response.status_code == 200
    assert response.json()["code"] == 503


async def test_learning_without_tools_returns_503_before_persisting(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    deps = _deps(settings, conversation, graph=build_graph(FakeLLM(["答案"]), None))

    async with _client(deps) as client:
        response = await client.post(
            LEARNING_URL,
            json={"question": "我要学 Redis", "technology": "Redis"},
            headers=USER,
        )

    assert response.status_code == 200
    assert response.json()["code"] == 503
    async with session_factory() as session:
        count = await session.scalar(
            select(func.count())
            .select_from(AgentConversation)
            .where(AgentConversation.user_id == 1)
        )
    assert count == 0


async def test_missing_conversation_returns_sse_error(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    deps = _deps(settings, conversation, graph=build_graph(FakeLLM(["答案"]), None))

    async with _client(deps) as client:
        response = await client.post(
            QA_URL,
            json={"question": "问题", "conversationId": str(uuid4()), "messageId": "m-1"},
            headers=USER,
        )

    frames = _frames(response.text)
    assert [name for name, _ in frames] == ["error"]
    assert frames[0][1]["code"] == 404
