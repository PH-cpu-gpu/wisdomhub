"""回合编排：三态、上下文、落库、重放、取消与错误。"""

import asyncio
from collections.abc import AsyncIterator, Sequence
from pathlib import Path
from typing import Any
from uuid import UUID, uuid4

import pytest
from alembic import command
from alembic.config import Config
from langchain_core.messages import (
    AIMessageChunk,
    BaseMessage,
    BaseMessageChunk,
    HumanMessage,
)
from openai import OpenAIError
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.capabilities.knowledge.tools import KNOWLEDGE_SERVICE, KnowledgeTools
from app.chat.graph.build import build_graph
from app.chat.service import (
    FALLBACK_ANSWER,
    IN_PROGRESS_MESSAGE,
    INTERNAL_MESSAGE,
    PERSIST_FAILED_MESSAGE,
    ChatTurnInput,
    run_chat_turn,
)
from app.contracts.chat_events import RouteEvent
from app.contracts.sse import DeltaEvent, DoneEvent, ErrorEvent, MetaEvent, SseEvent, StepsEvent
from app.contracts.workflow import WorkflowKind
from app.core.config import Settings
from app.features.conversation.models import AgentMessage
from app.features.conversation.repository import ConversationRepository
from app.features.conversation.service import ConversationService
from tests.fakes import FakeInternalClient, FakeLLM

ROOT = Path(__file__).resolve().parents[2]
PATH = "/internal/knowledge/prerequisites"
TTL = 300


class _BrokenLLM:
    def __init__(self, error: Exception) -> None:
        self._error = error

    async def astream(
        self, messages: Sequence[BaseMessage], **kwargs: Any
    ) -> AsyncIterator[BaseMessageChunk]:
        raise self._error
        yield AIMessageChunk(content="")


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


async def _collect(stream: AsyncIterator[SseEvent]) -> list[SseEvent]:
    return [event async for event in stream]


async def _create_conversation(
    session_factory: async_sessionmaker[AsyncSession], *, user_id: int = 1
) -> UUID:
    async with session_factory() as session, session.begin():
        conversation = await ConversationRepository(session).create_conversation(user_id, "会话")
        return conversation.id


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


def _learning_graph() -> tuple[Any, FakeLLM]:
    internal = FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload()})
    llm = FakeLLM(["第一次回答"])
    return build_graph(llm, KnowledgeTools(internal)), llm


async def test_new_qa_turn_streams_and_persists(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    llm = FakeLLM(["Redis 支持 RDB 和 AOF。"])
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="Redis 怎么持久化？",
        message_id="m-1",
    )

    events = await _collect(
        run_chat_turn(
            turn,
            conversation=conversation,
            graph=build_graph(llm, None),
            settings=settings,
        )
    )

    assert [type(event) for event in events] == [MetaEvent, DeltaEvent, DoneEvent]
    meta = events[0]
    assert isinstance(meta, MetaEvent)
    conversation_id = UUID(meta.conversation_id)
    row = await _assistant(session_factory, conversation_id, "m-1")
    assert row.status == "done"
    assert row.content == "Redis 支持 RDB 和 AOF。"
    assert row.steps is None
    assert row.artifacts is None
    assert row.workflow == WorkflowKind.QA
    user = await _user(session_factory, conversation_id, "m-1")
    assert user.content == "Redis 怎么持久化？"


async def test_new_learning_turn_persists_route_and_steps(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    graph, _ = _learning_graph()
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.LEARNING_PATH,
        question="我要学 Redis",
        message_id="m-1",
        technology="Redis",
        known=["Java"],
    )

    events = await _collect(
        run_chat_turn(turn, conversation=conversation, graph=graph, settings=settings)
    )

    assert [type(event) for event in events] == [
        MetaEvent,
        RouteEvent,
        StepsEvent,
        DeltaEvent,
        DoneEvent,
    ]
    meta = events[0]
    assert isinstance(meta, MetaEvent)
    row = await _assistant(session_factory, UUID(meta.conversation_id), "m-1")
    assert row.status == "done"
    assert row.artifacts is not None
    assert row.artifacts["route"]["target"]["tagId"] == "3001"
    assert row.steps is not None
    assert row.steps[0]["tool"] == "query_prerequisite_chain"
    assert row.steps[0]["status"] == "done"
    assert row.workflow == WorkflowKind.LEARNING_PATH


async def test_replay_reemits_artifacts_without_llm(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    graph, llm = _learning_graph()
    first = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.LEARNING_PATH,
        question="我要学 Redis",
        message_id="m-1",
        technology="Redis",
    )
    first_events = await _collect(
        run_chat_turn(first, conversation=conversation, graph=graph, settings=settings)
    )
    meta = first_events[0]
    assert isinstance(meta, MetaEvent)

    second = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.LEARNING_PATH,
        question="重复请求",
        conversation_id=UUID(meta.conversation_id),
        message_id="m-1",
        technology="Redis",
    )
    second_events = await _collect(
        run_chat_turn(second, conversation=conversation, graph=graph, settings=settings)
    )

    assert [type(event) for event in second_events] == [
        MetaEvent,
        RouteEvent,
        StepsEvent,
        DeltaEvent,
        DoneEvent,
    ]
    delta = second_events[3]
    assert isinstance(delta, DeltaEvent)
    assert delta.text == "第一次回答"
    assert len(llm.stream_calls) == 1


async def test_in_progress_turn_returns_conflict(
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
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
        message_id="m-1",
    )

    events = await _collect(
        run_chat_turn(
            turn,
            conversation=conversation,
            graph=build_graph(llm, None),
            settings=settings,
        )
    )

    assert [type(event) for event in events] == [MetaEvent, ErrorEvent]
    error = events[1]
    assert isinstance(error, ErrorEvent)
    assert error.code == 409
    assert error.message == IN_PROGRESS_MESSAGE
    assert llm.stream_calls == []


async def test_missing_conversation_returns_404(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=uuid4(),
        message_id="m-1",
    )

    events = await _collect(
        run_chat_turn(
            turn,
            conversation=conversation,
            graph=build_graph(FakeLLM(["不应出现"]), None),
            settings=settings,
        )
    )

    assert len(events) == 1
    error = events[0]
    assert isinstance(error, ErrorEvent)
    assert error.code == 404


async def test_empty_answer_uses_fallback(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="问题",
        message_id="m-1",
    )

    events = await _collect(
        run_chat_turn(
            turn,
            conversation=conversation,
            graph=build_graph(FakeLLM([""]), None),
            settings=settings,
        )
    )

    assert [type(event) for event in events] == [MetaEvent, DeltaEvent, DoneEvent]
    delta = events[1]
    assert isinstance(delta, DeltaEvent)
    assert delta.text == FALLBACK_ANSWER
    meta = events[0]
    assert isinstance(meta, MetaEvent)
    row = await _assistant(session_factory, UUID(meta.conversation_id), "m-1")
    assert row.content == FALLBACK_ANSWER


@pytest.mark.parametrize(
    ("error", "code", "message"),
    [
        (RuntimeError("boom"), 500, INTERNAL_MESSAGE),
        (OpenAIError("boom"), 503, "AI 服务暂不可用"),
    ],
)
async def test_llm_error_persists_error_row(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
    error: Exception,
    code: int,
    message: str,
) -> None:
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="问题",
        message_id="m-1",
    )

    events = await _collect(
        run_chat_turn(
            turn,
            conversation=conversation,
            graph=build_graph(_BrokenLLM(error), None),
            settings=settings,
        )
    )

    assert [type(event) for event in events] == [MetaEvent, ErrorEvent]
    failure = events[1]
    assert isinstance(failure, ErrorEvent)
    assert (failure.code, failure.message) == (code, message)
    meta = events[0]
    assert isinstance(meta, MetaEvent)
    row = await _assistant(session_factory, UUID(meta.conversation_id), "m-1")
    assert row.status == "error"


async def test_persist_failure_emits_error_without_done(
    conversation: ConversationService,
    settings: Settings,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    async def fail(**kwargs: Any) -> bool:
        return False

    monkeypatch.setattr(conversation, "finish_turn", fail)
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="问题",
        message_id="m-1",
    )

    events = await _collect(
        run_chat_turn(
            turn,
            conversation=conversation,
            graph=build_graph(FakeLLM(["答案"]), None),
            settings=settings,
        )
    )

    assert [type(event) for event in events] == [MetaEvent, DeltaEvent, ErrorEvent]
    failure = events[2]
    assert isinstance(failure, ErrorEvent)
    assert (failure.code, failure.message) == (500, PERSIST_FAILED_MESSAGE)


async def test_cancel_persists_aborted(
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
    stream = run_chat_turn(
        turn,
        conversation=conversation,
        graph=build_graph(FakeLLM(["答案"]), None),
        settings=settings,
    )

    first = await anext(stream)
    assert isinstance(first, MetaEvent)
    await stream.aclose()

    row = await _assistant(session_factory, conversation_id, "m-1")
    assert row.status == "aborted"


async def test_second_turn_uses_previous_history(
    conversation: ConversationService,
    settings: Settings,
) -> None:
    llm = FakeLLM(["第一答", "第二答"])
    graph = build_graph(llm, None)
    first = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="第一问",
        message_id="m-1",
    )
    first_events = await _collect(
        run_chat_turn(first, conversation=conversation, graph=graph, settings=settings)
    )
    meta = first_events[0]
    assert isinstance(meta, MetaEvent)

    second = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.QA,
        question="第二问",
        conversation_id=UUID(meta.conversation_id),
        message_id="m-2",
    )
    await _collect(run_chat_turn(second, conversation=conversation, graph=graph, settings=settings))

    sent = llm.stream_calls[1]
    contents = [message.content for message in sent if isinstance(message.content, str)]
    assert "第一问" in contents
    assert "第一答" in contents
    assert contents[-1] == "第二问"
    assert contents.count("第二问") == 1


async def test_text_turn_wraps_selected_text_and_stores_question_only(
    conversation: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    settings: Settings,
) -> None:
    llm = FakeLLM(["这段文本讲了 Redis 的持久化。"])
    turn = ChatTurnInput(
        user_id=1,
        workflow=WorkflowKind.TEXT_QA,
        question="这段讲了什么？",
        message_id="m-1",
        text="Redis 支持 RDB 和 AOF。",
    )

    events = await _collect(
        run_chat_turn(
            turn,
            conversation=conversation,
            graph=build_graph(llm, None),
            settings=settings,
        )
    )

    sent = llm.stream_calls[0]
    humans = [message for message in sent if isinstance(message, HumanMessage)]
    assert len(humans) == 1
    assert "<selected_text>Redis 支持 RDB 和 AOF。</selected_text>" in humans[0].content
    assert humans[0].content.count("这段讲了什么？") == 1
    meta = events[0]
    assert isinstance(meta, MetaEvent)
    user = await _user(session_factory, UUID(meta.conversation_id), "m-1")
    assert user.content == "这段讲了什么？"
