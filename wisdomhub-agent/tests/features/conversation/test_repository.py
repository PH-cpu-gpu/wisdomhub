"""会话仓储：幂等、归属、租约、分页与级联。"""

import asyncio
from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import UUID

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.pool import NullPool

from app.contracts.workflow import WorkflowKind
from app.features.conversation.models import AgentMessage
from app.features.conversation.repository import ConversationRepository

ROOT = Path(__file__).resolve().parents[3]
BASE = datetime(2026, 11, 16, 8, 0, tzinfo=UTC)
LEASE = BASE + timedelta(minutes=5)


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
async def session(
    session_factory: async_sessionmaker[AsyncSession],
) -> AsyncIterator[AsyncSession]:
    async with session_factory() as value:
        yield value


@pytest.fixture
def repo(session: AsyncSession) -> ConversationRepository:
    return ConversationRepository(session)


async def _count_messages(session: AsyncSession, conversation_id: UUID) -> int:
    total = await session.scalar(
        select(func.count())
        .select_from(AgentMessage)
        .where(AgentMessage.conversation_id == conversation_id)
    )
    return total or 0


@pytest.mark.pg
async def test_create_and_owned_lookup(repo: ConversationRepository, session: AsyncSession) -> None:
    conversation = await repo.create_conversation(1, "Redis 持久化")

    assert conversation.id is not None
    assert conversation.created_at.tzinfo is not None
    assert conversation.updated_at.tzinfo is not None
    assert await repo.get_conversation(1, conversation.id) is conversation
    assert await repo.get_conversation(2, conversation.id) is None


@pytest.mark.pg
async def test_user_message_insert_is_idempotent(
    repo: ConversationRepository, session: AsyncSession
) -> None:
    conversation = await repo.create_conversation(1, "幂等")

    first = await repo.insert_user_message(conversation.id, 1, "m-1", WorkflowKind.QA, "什么是 RDB")
    second = await repo.insert_user_message(
        conversation.id, 1, "m-1", WorkflowKind.QA, "改过的提问"
    )

    assert first.created is True
    assert second.created is False
    assert second.message.id == first.message.id
    assert second.message.content == "什么是 RDB"

    count = await session.scalar(
        select(func.count())
        .select_from(AgentMessage)
        .where(
            AgentMessage.conversation_id == conversation.id,
            AgentMessage.message_id == "m-1",
            AgentMessage.role == "user",
        )
    )
    assert count == 1


@pytest.mark.pg
async def test_user_message_stores_only_question(repo: ConversationRepository) -> None:
    conversation = await repo.create_conversation(1, "字段")

    message = (
        await repo.insert_user_message(
            conversation.id, 1, "m-1", WorkflowKind.TEXT_QA, "这段文本讲了什么"
        )
    ).message

    assert message.role == "user"
    assert message.content == "这段文本讲了什么"
    assert message.status == "done"
    assert message.workflow == WorkflowKind.TEXT_QA
    assert message.steps is None
    assert message.artifacts is None
    assert message.expires_at is None


@pytest.mark.pg
async def test_assistant_placeholder_is_idempotent(repo: ConversationRepository) -> None:
    conversation = await repo.create_conversation(1, "占位")

    first = await repo.insert_assistant_placeholder(
        conversation.id, 1, "m-1", WorkflowKind.LEARNING_PATH, LEASE
    )
    second = await repo.insert_assistant_placeholder(
        conversation.id, 1, "m-1", WorkflowKind.LEARNING_PATH, LEASE + timedelta(minutes=1)
    )

    assert first.created is True
    assert second.created is False
    placeholder = second.message
    assert placeholder.id == first.message.id
    assert placeholder.status == "in_progress"
    assert placeholder.content == ""
    assert placeholder.workflow == WorkflowKind.LEARNING_PATH
    assert placeholder.expires_at == LEASE


@pytest.mark.pg
async def test_finish_writes_snapshot_and_touches_conversation(
    repo: ConversationRepository, session: AsyncSession
) -> None:
    conversation = await repo.create_conversation(1, "完成")
    await repo.insert_assistant_placeholder(conversation.id, 1, "m-1", WorkflowKind.QA, LEASE)
    steps = [
        {
            "id": "s1",
            "tool": "query_prerequisite_chain",
            "label": "取前置链",
            "status": "done",
            "summary": None,
        }
    ]
    artifacts = {
        "route": {
            "target": {"tagId": "10", "name": "Redis"},
            "known": ["Java"],
            "truncated": False,
            "stages": [],
        }
    }
    finished_at = conversation.updated_at + timedelta(seconds=1)

    finished = await repo.finish_assistant(
        conversation.id,
        "m-1",
        status="done",
        content="Redis 的持久化方式有 RDB 和 AOF。",
        steps=steps,
        artifacts=artifacts,
        workflow=WorkflowKind.QA,
        lease_expires_at=LEASE,
        now=finished_at,
    )

    assert finished is True
    assistant = (await repo.get_turn(conversation.id, "m-1")).assistant
    assert assistant is not None
    assert assistant.content == "Redis 的持久化方式有 RDB 和 AOF。"
    assert assistant.status == "done"
    assert assistant.workflow == WorkflowKind.QA
    assert assistant.steps == steps
    assert assistant.artifacts == artifacts
    assert assistant.expires_at is None

    await session.refresh(conversation)
    assert conversation.updated_at == finished_at


@pytest.mark.pg
async def test_finish_only_once(repo: ConversationRepository) -> None:
    conversation = await repo.create_conversation(1, "重复完成")
    await repo.insert_assistant_placeholder(conversation.id, 1, "m-1", WorkflowKind.QA, LEASE)

    async def finish() -> bool:
        return await repo.finish_assistant(
            conversation.id,
            "m-1",
            status="done",
            content="回答",
            steps=None,
            artifacts=None,
            workflow=WorkflowKind.QA,
            lease_expires_at=LEASE,
        )

    assert await finish() is True
    assert await finish() is False


@pytest.mark.pg
async def test_takeover_rejects_stale_finish(repo: ConversationRepository) -> None:
    conversation = await repo.create_conversation(1, "接管")
    await repo.insert_assistant_placeholder(conversation.id, 1, "m-1", WorkflowKind.QA, LEASE)
    renewed = LEASE + timedelta(minutes=10)

    assert (
        await repo.renew_assistant_lease(
            conversation.id,
            "m-1",
            expected_expires_at=LEASE,
            expires_at=renewed,
        )
        is True
    )
    assert (
        await repo.renew_assistant_lease(
            conversation.id,
            "m-1",
            expected_expires_at=LEASE,
            expires_at=renewed,
        )
        is False
    )
    assert (
        await repo.finish_assistant(
            conversation.id,
            "m-1",
            status="done",
            content="旧回合结果",
            steps=None,
            artifacts=None,
            workflow=WorkflowKind.QA,
            lease_expires_at=LEASE,
        )
        is False
    )
    assert (
        await repo.finish_assistant(
            conversation.id,
            "m-1",
            status="done",
            content="新回合结果",
            steps=None,
            artifacts=None,
            workflow=WorkflowKind.QA,
            lease_expires_at=renewed,
        )
        is True
    )


@pytest.mark.pg
async def test_list_page_orders_and_scopes(repo: ConversationRepository) -> None:
    first = await repo.create_conversation(1, "第一")
    second = await repo.create_conversation(1, "第二")
    third = await repo.create_conversation(1, "第三")
    other = await repo.create_conversation(2, "他人")

    await repo.touch_conversation(first.id, when=BASE)
    await repo.touch_conversation(second.id, when=BASE + timedelta(seconds=1))
    await repo.touch_conversation(third.id, when=BASE + timedelta(seconds=2))
    await repo.touch_conversation(other.id, when=BASE + timedelta(seconds=3))

    page_one = await repo.list_page(1, 1, 2)
    page_two = await repo.list_page(1, 2, 2)

    assert page_one.total == 3
    assert [item.conversation.id for item in page_one.items] == [third.id, second.id]
    assert [item.conversation.id for item in page_two.items] == [first.id]


@pytest.mark.pg
async def test_list_and_detail_derive_latest_workflow(
    repo: ConversationRepository,
) -> None:
    conversation = await repo.create_conversation(1, "学习路线")
    await repo.insert_user_message(conversation.id, 1, "m-1", WorkflowKind.QA, "怎么学")
    await repo.insert_assistant_placeholder(conversation.id, 1, "m-1", WorkflowKind.QA, LEASE)
    await repo.insert_assistant_placeholder(
        conversation.id, 1, "m-2", WorkflowKind.LEARNING_PATH, LEASE
    )

    empty = await repo.create_conversation(1, "只有用户消息")
    await repo.insert_user_message(empty.id, 1, "m-1", WorkflowKind.TEXT_QA, "分析这段话")

    page = await repo.list_page(1, 1, 10)
    workflows = {item.conversation.id: item.workflow for item in page.items}
    assert workflows[conversation.id] == WorkflowKind.LEARNING_PATH
    assert workflows[empty.id] is None

    detail = await repo.get_detail(1, conversation.id)
    assert detail is not None
    assert detail.summary.workflow == WorkflowKind.LEARNING_PATH


@pytest.mark.pg
async def test_detail_orders_by_seq_and_hides_other_users(
    repo: ConversationRepository,
) -> None:
    conversation = await repo.create_conversation(1, "详情")
    await repo.insert_user_message(conversation.id, 1, "m-1", WorkflowKind.QA, "q1")
    await repo.insert_assistant_placeholder(conversation.id, 1, "m-1", WorkflowKind.QA, LEASE)
    await repo.finish_assistant(
        conversation.id,
        "m-1",
        status="done",
        content="a1",
        steps=None,
        artifacts=None,
        workflow=WorkflowKind.QA,
        lease_expires_at=LEASE,
    )
    await repo.insert_user_message(conversation.id, 1, "m-2", WorkflowKind.QA, "q2")

    detail = await repo.get_detail(1, conversation.id)
    assert detail is not None
    assert [(item.role, item.content) for item in detail.messages] == [
        ("user", "q1"),
        ("assistant", "a1"),
        ("user", "q2"),
    ]

    history = await repo.list_history(conversation.id)
    assert [item.content for item in history] == ["q1", "a1", "q2"]
    assert await repo.get_detail(2, conversation.id) is None


@pytest.mark.pg
async def test_delete_scopes_and_cascades(
    repo: ConversationRepository, session: AsyncSession
) -> None:
    owned = await repo.create_conversation(1, "我的会话")
    other = await repo.create_conversation(2, "他人会话")
    await repo.insert_user_message(owned.id, 1, "m-1", WorkflowKind.QA, "问题")
    await repo.insert_user_message(other.id, 2, "m-1", WorkflowKind.QA, "问题")

    assert await repo.delete_conversation(2, owned.id) is False
    assert await repo.get_conversation(1, owned.id) is not None

    assert await repo.delete_conversation(1, owned.id) is True
    assert await repo.get_conversation(1, owned.id) is None
    assert await _count_messages(session, owned.id) == 0
    assert await repo.get_conversation(2, other.id) is not None
    assert await _count_messages(session, other.id) == 1


@pytest.mark.pg
async def test_concurrent_user_message_insert_creates_one_row(
    session_factory: async_sessionmaker[AsyncSession],
) -> None:
    async with session_factory() as session:
        conversation = await ConversationRepository(session).create_conversation(1, "并发")
        await session.commit()
        conversation_id = conversation.id

    async def insert(question: str) -> bool:
        async with session_factory() as session:
            result = await ConversationRepository(session).insert_user_message(
                conversation_id, 1, "m-1", WorkflowKind.QA, question
            )
            await session.commit()
            return result.created

    created = await asyncio.gather(insert("问题 A"), insert("问题 B"))

    assert sorted(created) == [False, True]
    async with session_factory() as session:
        count = await session.scalar(
            select(func.count())
            .select_from(AgentMessage)
            .where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.message_id == "m-1",
                AgentMessage.role == "user",
            )
        )
        assert count == 1


@pytest.mark.pg
async def test_get_detail_without_messages(repo: ConversationRepository) -> None:
    conversation = await repo.create_conversation(1, "空会话")

    detail = await repo.get_detail(1, conversation.id)

    assert detail is not None
    assert detail.summary.workflow is None
    assert detail.messages == []
