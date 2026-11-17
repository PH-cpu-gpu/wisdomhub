"""聊天测试矩阵：单次 astream、幂等重放、终态、token 过滤与取消联动。"""

import asyncio
import json
from collections.abc import AsyncIterator, Sequence
from pathlib import Path
from typing import Any, cast
from uuid import UUID

import pytest
from alembic import command
from alembic.config import Config
from fastapi import FastAPI
from httpx import ASGITransport, AsyncClient
from langchain_core.messages import AIMessageChunk, BaseMessage, BaseMessageChunk
from langgraph.graph.state import CompiledStateGraph
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.capabilities.knowledge.tools import KNOWLEDGE_SERVICE, KnowledgeTools
from app.chat.api import guard_events, router
from app.chat.graph.build import build_graph
from app.chat.prompt import LEARNING_PATH_SYSTEM_PROMPT
from app.chat.service import ChatTurnInput, run_chat_turn
from app.contracts.workflow import WorkflowKind
from app.core.config import Settings
from app.core.deps import AppDeps
from app.core.errors import register_exception_handlers
from app.features.conversation.models import AgentMessage
from app.features.conversation.repository import ConversationRepository
from app.features.conversation.service import ConversationService
from tests.fakes import FakeInternalClient, FakeLLM

ROOT = Path(__file__).resolve().parents[2]
PATH = "/internal/knowledge/prerequisites"
TTL = 300
USER = {"X-User-Id": "1"}
QA_URL = "/api/agent/chat"
LEARNING_URL = "/api/agent/learning-path"
TEXT_QA_URL = "/api/agent/text-qa"


class _SpyGraph:
    def __init__(self, inner: CompiledStateGraph) -> None:
        self.inner = inner
        self.calls = 0

    def astream(self, *args: Any, **kwargs: Any) -> Any:
        self.calls += 1
        return self.inner.astream(*args, **kwargs)


class _BrokenLLM:
    async def astream(
        self, messages: Sequence[BaseMessage], **kwargs: Any
    ) -> AsyncIterator[BaseMessageChunk]:
        raise RuntimeError("boom")
        yield AIMessageChunk(content="")


def _graph(llm: Any, tools: KnowledgeTools | None = None) -> _SpyGraph:
    return _SpyGraph(build_graph(llm, tools))


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


def _client(deps: AppDeps) -> AsyncClient:
    app = FastAPI()
    app.state.deps = deps
    register_exception_handlers(app)
    app.include_router(router)
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


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


def _learning_tools() -> KnowledgeTools:
    return KnowledgeTools(FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload()}))


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


async def _create_conversation(
    session_factory: async_sessionmaker[AsyncSession],
) -> UUID:
    async with session_factory() as session, session.begin():
        conversation = await ConversationRepository(session).create_conversation(1, "会话")
        return conversation.id


async def test_new_turn_calls_astream_once(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    llm = FakeLLM(["答案"])
    spy = _graph(llm)
    deps = _deps(settings, conversation, graph=cast(CompiledStateGraph, spy))

    async with _client(deps) as client:
        response = await client.post(
            QA_URL, json={"question": "问题", "messageId": "m-1"}, headers=USER
        )

    assert [name for name, _ in _frames(response.text)] == ["meta", "delta", "done"]
    assert spy.calls == 1
    assert len(llm.stream_calls) == 1


async def test_replay_does_not_call_astream(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    llm = FakeLLM(["第一次回答"])
    spy = _graph(llm)
    deps = _deps(settings, conversation, graph=cast(CompiledStateGraph, spy))

    async with _client(deps) as client:
        first = await client.post(
            QA_URL, json={"question": "问题", "messageId": "m-1"}, headers=USER
        )
        meta = _frames(first.text)[0][1]
        second = await client.post(
            QA_URL,
            json={
                "question": "重复请求",
                "conversationId": meta["conversationId"],
                "messageId": "m-1",
            },
            headers=USER,
        )

    frames = _frames(second.text)
    assert [name for name, _ in frames] == ["meta", "delta", "done"]
    assert frames[1][1]["text"] == "第一次回答"
    assert spy.calls == 1
    assert len(llm.stream_calls) == 1


async def test_in_progress_returns_409_without_astream(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    conversation_id = await _create_conversation(session_factory)
    await conversation.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
    )
    llm = FakeLLM(["不应出现"])
    spy = _graph(llm)
    deps = _deps(settings, conversation, graph=cast(CompiledStateGraph, spy))

    async with _client(deps) as client:
        response = await client.post(
            QA_URL,
            json={
                "question": "问题",
                "conversationId": str(conversation_id),
                "messageId": "m-1",
            },
            headers=USER,
        )

    frames = _frames(response.text)
    assert [name for name, _ in frames] == ["meta", "error"]
    assert frames[1][1]["code"] == 409
    assert spy.calls == 0
    assert llm.stream_calls == []


async def test_learning_replay_reemits_artifacts(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    tools = _learning_tools()
    llm = FakeLLM(["先学数据结构。"])
    spy = _graph(llm, tools)
    deps = _deps(
        settings,
        conversation,
        graph=cast(CompiledStateGraph, spy),
        knowledge_tools=tools,
    )

    async with _client(deps) as client:
        first = await client.post(
            LEARNING_URL,
            json={"question": "我要学 Redis", "technology": "Redis", "messageId": "m-1"},
            headers=USER,
        )
        meta = _frames(first.text)[0][1]
        second = await client.post(
            LEARNING_URL,
            json={
                "question": "重复请求",
                "technology": "Redis",
                "conversationId": meta["conversationId"],
                "messageId": "m-1",
            },
            headers=USER,
        )

    frames = _frames(second.text)
    assert [name for name, _ in frames] == ["meta", "route", "steps", "delta", "done"]
    assert frames[1][1]["target"]["tagId"] == "3001"
    assert spy.calls == 1
    assert len(llm.stream_calls) == 1


async def test_http_error_is_terminal_and_persisted(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    spy = _graph(_BrokenLLM())
    deps = _deps(settings, conversation, graph=cast(CompiledStateGraph, spy))

    async with _client(deps) as client:
        response = await client.post(
            QA_URL, json={"question": "问题", "messageId": "m-1"}, headers=USER
        )

    frames = _frames(response.text)
    assert [name for name, _ in frames] == ["meta", "error"]
    assert frames[1][1]["code"] == 500
    row = await _assistant(session_factory, UUID(frames[0][1]["conversationId"]), "m-1")
    assert row.status == "error"


async def test_token_filter_end_to_end(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    tools = _learning_tools()
    llm = FakeLLM(["先学数据结构。"])
    spy = _graph(llm, tools)
    deps = _deps(
        settings,
        conversation,
        graph=cast(CompiledStateGraph, spy),
        knowledge_tools=tools,
    )

    async with _client(deps) as client:
        response = await client.post(
            LEARNING_URL,
            json={"question": "我要学 Redis", "technology": "Redis", "messageId": "m-1"},
            headers=USER,
        )

    delta_text = "".join(data["text"] for name, data in _frames(response.text) if name == "delta")
    assert delta_text == "先学数据结构。"
    assert LEARNING_PATH_SYSTEM_PROMPT not in delta_text
    assert "查询前置知识链" not in delta_text
    assert "<route>" not in delta_text


async def test_guard_close_persists_aborted(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    conversation_id = await _create_conversation(session_factory)
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
        message_id="m-1",
    )
    events = run_chat_turn(
        turn,
        conversation=conversation,
        graph=cast(CompiledStateGraph, _graph(FakeLLM(["答案"]))),
        settings=settings,
    )
    stream = guard_events(events, idle_timeout=10.0)

    first = await anext(stream)
    assert first.event == "meta"
    await stream.aclose()

    row = await _assistant(session_factory, conversation_id, "m-1")
    assert row.status == "aborted"


async def test_endpoint_event_sets(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    tools = _learning_tools()
    llm = FakeLLM(["答案", "答案", "先学数据结构。"])
    spy = _graph(llm, tools)
    deps = _deps(
        settings,
        conversation,
        graph=cast(CompiledStateGraph, spy),
        knowledge_tools=tools,
    )

    async with _client(deps) as client:
        qa = await client.post(QA_URL, json={"question": "问题", "messageId": "m-1"}, headers=USER)
        text = await client.post(
            TEXT_QA_URL,
            json={"question": "问题", "text": "选中文本", "messageId": "m-2"},
            headers=USER,
        )
        learning = await client.post(
            LEARNING_URL,
            json={"question": "我要学 Redis", "technology": "Redis", "messageId": "m-3"},
            headers=USER,
        )

    qa_names = [name for name, _ in _frames(qa.text)]
    text_names = [name for name, _ in _frames(text.text)]
    learning_names = [name for name, _ in _frames(learning.text)]
    assert "route" not in qa_names and "steps" not in qa_names
    assert "route" not in text_names and "steps" not in text_names
    assert "route" in learning_names and "steps" in learning_names
