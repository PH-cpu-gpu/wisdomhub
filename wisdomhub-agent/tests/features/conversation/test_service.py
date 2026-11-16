"""会话回合服务：三态、接管、归属、标题与并发。"""

import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import UUID

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.contracts.workflow import WorkflowKind
from app.core.errors import BusinessError, ErrorCode
from app.features.conversation.models import AgentConversation, AgentMessage
from app.features.conversation.repository import ConversationRepository, TerminalStatus
from app.features.conversation.service import (
    DEFAULT_TITLE,
    TITLE_MAX_LENGTH,
    ConversationService,
    TurnOutcome,
)

ROOT = Path(__file__).resolve().parents[3]
NOW = datetime(2026, 11, 16, 8, 0, tzinfo=UTC)
TTL = 300
LEASE = NOW + timedelta(seconds=TTL)


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
def service(session_factory: async_sessionmaker[AsyncSession]) -> ConversationService:
    return ConversationService(session_factory, ttl_seconds=TTL, clock=lambda: NOW)


async def _create_conversation(
    session_factory: async_sessionmaker[AsyncSession],
    *,
    user_id: int = 1,
    title: str = "既有会话",
) -> UUID:
    async with session_factory() as session, session.begin():
        conversation = await ConversationRepository(session).create_conversation(user_id, title)
        return conversation.id


async def _finish(
    session_factory: async_sessionmaker[AsyncSession],
    conversation_id: UUID,
    message_id: str,
    *,
    status: TerminalStatus,
    content: str,
) -> None:
    async with session_factory() as session, session.begin():
        finished = await ConversationRepository(session).finish_assistant(
            conversation_id,
            message_id,
            status=status,
            content=content,
            steps=[{"id": "s1", "tool": "query", "label": "查询", "status": "done"}],
            artifacts=None,
            workflow=WorkflowKind.QA,
            lease_expires_at=LEASE,
            now=NOW,
        )
        assert finished is True


async def _messages(
    session_factory: async_sessionmaker[AsyncSession], conversation_id: UUID
) -> list[AgentMessage]:
    async with session_factory() as session:
        rows = await session.scalars(
            select(AgentMessage)
            .where(AgentMessage.conversation_id == conversation_id)
            .order_by(AgentMessage.seq)
        )
        return list(rows)


async def _conversation(
    session_factory: async_sessionmaker[AsyncSession], conversation_id: UUID
) -> AgentConversation:
    async with session_factory() as session:
        conversation = await session.get(AgentConversation, conversation_id)
        assert conversation is not None
        return conversation


@pytest.mark.pg
async def test_prepare_new_conversation(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    turn = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="Redis 持久化怎么做？",
    )

    assert turn.outcome is TurnOutcome.NEW
    assert turn.workflow is WorkflowKind.QA
    assert turn.question == "Redis 持久化怎么做？"
    assert turn.expires_at == LEASE

    conversation = await _conversation(session_factory, turn.conversation_id)
    assert conversation.user_id == 1
    assert conversation.title == "Redis 持久化怎么做？"

    rows = await _messages(session_factory, turn.conversation_id)
    assert [(row.role, row.status, row.content) for row in rows] == [
        ("user", "done", "Redis 持久化怎么做？"),
        ("assistant", "in_progress", ""),
    ]
    assert rows[1].expires_at == LEASE


@pytest.mark.pg
async def test_prepare_new_turn_keeps_title_and_touches(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    conversation_id = await _create_conversation(session_factory, title="保持标题")

    turn = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.LEARNING_PATH,
        question="怎么学 Redis",
        conversation_id=conversation_id,
    )

    assert turn.outcome is TurnOutcome.NEW
    conversation = await _conversation(session_factory, conversation_id)
    assert conversation.title == "保持标题"
    assert conversation.updated_at == NOW
    assert len(await _messages(session_factory, conversation_id)) == 2


@pytest.mark.pg
async def test_prepare_replay_done_turn(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    conversation_id = await _create_conversation(session_factory)
    first = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="原问题",
        conversation_id=conversation_id,
    )
    assert first.expires_at is not None
    await _finish(session_factory, conversation_id, "m-1", status="done", content="完整答案")
    before = await _conversation(session_factory, conversation_id)

    replay = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="重复请求的新问题",
        conversation_id=conversation_id,
    )

    assert replay.outcome is TurnOutcome.REPLAY
    assert replay.question == "原问题"
    assert replay.assistant is not None
    assert replay.assistant.content == "完整答案"
    assert replay.expires_at is None

    rows = await _messages(session_factory, conversation_id)
    assert len(rows) == 2
    unchanged = await _conversation(session_factory, conversation_id)
    assert (unchanged.title, unchanged.updated_at) == (before.title, before.updated_at)


@pytest.mark.pg
async def test_prepare_in_progress_keeps_lease(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    conversation_id = await _create_conversation(session_factory)
    await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="第一问",
        conversation_id=conversation_id,
    )

    busy = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="第二问",
        conversation_id=conversation_id,
    )

    assert busy.outcome is TurnOutcome.IN_PROGRESS
    assert busy.question == "第一问"
    assert busy.expires_at == LEASE
    assert len(await _messages(session_factory, conversation_id)) == 2


@pytest.mark.pg
async def test_prepare_takes_over_expired_lease(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    clock = {"now": NOW}
    service = ConversationService(session_factory, ttl_seconds=TTL, clock=lambda: clock["now"])
    conversation_id = await _create_conversation(session_factory)
    await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
    )
    before = await _messages(session_factory, conversation_id)

    clock["now"] = NOW + timedelta(seconds=TTL + 1)
    taken = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
    )

    expected_lease = clock["now"] + timedelta(seconds=TTL)
    assert taken.outcome is TurnOutcome.NEW
    assert taken.expires_at == expected_lease
    after = await _messages(session_factory, conversation_id)
    assert len(after) == 2
    assert (after[1].id, after[1].seq, after[1].created_at) == (
        before[1].id,
        before[1].seq,
        before[1].created_at,
    )
    assert after[1].status == "in_progress"
    assert after[1].expires_at == expected_lease


@pytest.mark.pg
@pytest.mark.parametrize("status", ["aborted", "error"])
async def test_prepare_takes_over_terminal_turn(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    status: TerminalStatus,
) -> None:
    conversation_id = await _create_conversation(session_factory)
    await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
    )
    await _finish(session_factory, conversation_id, "m-1", status=status, content="半截内容")
    before = await _messages(session_factory, conversation_id)
    assert before[1].steps is not None

    taken = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
    )

    assert taken.outcome is TurnOutcome.NEW
    after = await _messages(session_factory, conversation_id)
    assert len(after) == 2
    assert after[1].id == before[1].id
    assert after[1].status == "in_progress"
    assert after[1].content == ""
    assert after[1].steps is None
    assert after[1].expires_at == LEASE


@pytest.mark.pg
async def test_prepare_rejects_foreign_conversation(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    conversation_id = await _create_conversation(session_factory, user_id=2)

    with pytest.raises(BusinessError) as exc:
        await service.prepare_turn(
            user_id=1,
            message_id="m-1",
            workflow=WorkflowKind.QA,
            question="越权",
            conversation_id=conversation_id,
        )

    assert exc.value.code is ErrorCode.NOT_FOUND
    assert await _messages(session_factory, conversation_id) == []


@pytest.mark.pg
@pytest.mark.parametrize(
    ("question", "expected"),
    [
        ("  \n \n Redis 怎么学？ \n第二行", "Redis 怎么学？"),
        ("Redis   持久化", "Redis 持久化"),
        ("   \n\t ", DEFAULT_TITLE),
        ("a" * (TITLE_MAX_LENGTH + 5), "a" * TITLE_MAX_LENGTH + "…"),
    ],
)
async def test_prepare_derives_title(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
    question: str,
    expected: str,
) -> None:
    turn = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question=question,
    )

    conversation = await _conversation(session_factory, turn.conversation_id)
    assert conversation.title == expected


@pytest.mark.pg
async def test_concurrent_same_message_id_writes_single_rows(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    conversation_id = await _create_conversation(session_factory)

    results = await asyncio.gather(
        service.prepare_turn(
            user_id=1,
            message_id="m-1",
            workflow=WorkflowKind.QA,
            question="并发问题",
            conversation_id=conversation_id,
        ),
        service.prepare_turn(
            user_id=1,
            message_id="m-1",
            workflow=WorkflowKind.QA,
            question="并发问题",
            conversation_id=conversation_id,
        ),
    )

    assert sorted(item.outcome for item in results) == [
        TurnOutcome.IN_PROGRESS,
        TurnOutcome.NEW,
    ]
    rows = await _messages(session_factory, conversation_id)
    assert [(row.role, row.message_id) for row in rows] == [
        ("user", "m-1"),
        ("assistant", "m-1"),
    ]


@pytest.mark.pg
async def test_prepare_takes_over_at_exact_expiry(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    clock = {"now": NOW}
    service = ConversationService(session_factory, ttl_seconds=TTL, clock=lambda: clock["now"])
    conversation_id = await _create_conversation(session_factory)
    await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
    )

    clock["now"] = LEASE
    taken = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
    )

    assert taken.outcome is TurnOutcome.NEW
    assert taken.expires_at == LEASE + timedelta(seconds=TTL)


@pytest.mark.pg
async def test_concurrent_takeover_of_expired_lease(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    clock = {"now": NOW}
    service = ConversationService(session_factory, ttl_seconds=TTL, clock=lambda: clock["now"])
    conversation_id = await _create_conversation(session_factory)
    await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="问题",
        conversation_id=conversation_id,
    )

    clock["now"] = NOW + timedelta(seconds=TTL + 1)
    results = await asyncio.gather(
        service.prepare_turn(
            user_id=1,
            message_id="m-1",
            workflow=WorkflowKind.QA,
            question="问题",
            conversation_id=conversation_id,
        ),
        service.prepare_turn(
            user_id=1,
            message_id="m-1",
            workflow=WorkflowKind.QA,
            question="问题",
            conversation_id=conversation_id,
        ),
    )

    assert sorted(item.outcome for item in results) == [
        TurnOutcome.IN_PROGRESS,
        TurnOutcome.NEW,
    ]
    rows = await _messages(session_factory, conversation_id)
    assert len(rows) == 2
    assert rows[1].status == "in_progress"
    assert rows[1].expires_at == clock["now"] + timedelta(seconds=TTL)


@pytest.mark.pg
async def test_prepare_rejects_deleted_conversation(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    conversation_id = await _create_conversation(session_factory)
    async with session_factory() as session, session.begin():
        deleted = await ConversationRepository(session).delete_conversation(1, conversation_id)
        assert deleted is True

    with pytest.raises(BusinessError) as exc:
        await service.prepare_turn(
            user_id=1,
            message_id="m-1",
            workflow=WorkflowKind.QA,
            question="已删除的会话",
            conversation_id=conversation_id,
        )

    assert exc.value.code is ErrorCode.NOT_FOUND
    assert await _messages(session_factory, conversation_id) == []


@pytest.mark.pg
async def test_replay_keeps_original_question_and_stored_workflow(
    service: ConversationService,
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    conversation_id = await _create_conversation(session_factory)
    await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.QA,
        question="原问题",
        conversation_id=conversation_id,
    )
    await _finish(session_factory, conversation_id, "m-1", status="done", content="完整答案")

    replay = await service.prepare_turn(
        user_id=1,
        message_id="m-1",
        workflow=WorkflowKind.LEARNING_PATH,
        question="重复请求的新问题",
        conversation_id=conversation_id,
    )

    assert replay.outcome is TurnOutcome.REPLAY
    assert replay.question == "原问题"
    rows = await _messages(session_factory, conversation_id)
    assert [row.workflow for row in rows] == [WorkflowKind.QA, WorkflowKind.QA]
