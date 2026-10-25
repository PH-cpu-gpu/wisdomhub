from datetime import datetime
from uuid import UUID

from sqlalchemy import and_, or_, select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from db.models import AgentConversation, AgentMessage


async def get_conversation(
    session: AsyncSession, conversation_id: UUID, user_id: int
) -> AgentConversation | None:
    return await session.scalar(
        select(AgentConversation).where(
            AgentConversation.id == conversation_id,
            AgentConversation.user_id == user_id,
        )
    )


async def create_conversation(
    session: AsyncSession,
    *,
    conversation_id: UUID,
    user_id: int,
    title: str,
    now: datetime,
) -> None:
    session.add(
        AgentConversation(
            id=conversation_id,
            user_id=user_id,
            title=title,
            created_at=now,
            updated_at=now,
        )
    )


async def touch_conversation(
    session: AsyncSession, conversation_id: UUID, user_id: int, now: datetime
) -> None:
    await session.execute(
        update(AgentConversation)
        .where(
            AgentConversation.id == conversation_id,
            AgentConversation.user_id == user_id,
        )
        .values(updated_at=now)
    )


async def insert_user_message_if_absent(
    session: AsyncSession,
    *,
    row_id: UUID,
    conversation_id: UUID,
    message_id: str,
    user_id: int,
    question: str,
    now: datetime,
) -> bool:
    stmt = (
        pg_insert(AgentMessage)
        .values(
            id=row_id,
            conversation_id=conversation_id,
            message_id=message_id,
            user_id=user_id,
            role="user",
            content=question,
            status="done",
            created_at=now,
        )
        .on_conflict_do_nothing(constraint="uq_agent_message_turn")
        .returning(AgentMessage.id)
    )
    return await session.scalar(stmt) is not None


async def insert_assistant_placeholder_if_absent(
    session: AsyncSession,
    *,
    row_id: UUID,
    conversation_id: UUID,
    message_id: str,
    user_id: int,
    now: datetime,
    expires_at: datetime,
) -> UUID | None:
    stmt = (
        pg_insert(AgentMessage)
        .values(
            id=row_id,
            conversation_id=conversation_id,
            message_id=message_id,
            user_id=user_id,
            role="assistant",
            content="",
            status="in_progress",
            created_at=now,
            expires_at=expires_at,
        )
        .on_conflict_do_nothing(constraint="uq_agent_message_turn")
        .returning(AgentMessage.id)
    )
    return await session.scalar(stmt)


async def get_user_message(
    session: AsyncSession, *, conversation_id: UUID, message_id: str, user_id: int
) -> AgentMessage | None:
    return await session.scalar(
        select(AgentMessage).where(
            AgentMessage.conversation_id == conversation_id,
            AgentMessage.message_id == message_id,
            AgentMessage.user_id == user_id,
            AgentMessage.role == "user",
        )
    )


async def get_assistant_message(
    session: AsyncSession, *, conversation_id: UUID, message_id: str, user_id: int
) -> AgentMessage | None:
    return await session.scalar(
        select(AgentMessage).where(
            AgentMessage.conversation_id == conversation_id,
            AgentMessage.message_id == message_id,
            AgentMessage.user_id == user_id,
            AgentMessage.role == "assistant",
        )
    )


async def claim_assistant(
    session: AsyncSession,
    *,
    row_id: UUID,
    conversation_id: UUID,
    user_id: int,
    now: datetime,
    expires_at: datetime,
) -> bool:
    result = await session.execute(
        update(AgentMessage)
        .where(
            AgentMessage.id == row_id,
            AgentMessage.conversation_id == conversation_id,
            AgentMessage.user_id == user_id,
            AgentMessage.role == "assistant",
            or_(
                AgentMessage.status.in_(("aborted", "error")),
                and_(
                    AgentMessage.status == "in_progress",
                    or_(AgentMessage.expires_at.is_(None), AgentMessage.expires_at < now),
                ),
            ),
        )
        .values(
            status="in_progress",
            content="",
            steps=None,
            expires_at=expires_at,
        )
    )
    return result.rowcount == 1


async def finish_assistant_message(
    session: AsyncSession,
    *,
    row_id: UUID,
    user_id: int,
    content: str,
    status: str,
    steps: list[dict] | None = None,
) -> int:
    result = await session.execute(
        update(AgentMessage)
        .where(
            AgentMessage.id == row_id,
            AgentMessage.user_id == user_id,
            AgentMessage.role == "assistant",
        )
        .values(content=content, steps=steps, status=status, expires_at=None)
    )
    return result.rowcount


async def list_recent_messages(
    session: AsyncSession,
    *,
    conversation_id: UUID,
    user_id: int,
    limit: int,
    exclude_message_id: str | None = None,
    only_done: bool = False,
) -> list[AgentMessage]:
    conditions = [
        AgentMessage.conversation_id == conversation_id,
        AgentMessage.user_id == user_id,
    ]
    if exclude_message_id is not None:
        conditions.append(AgentMessage.message_id != exclude_message_id)
    if only_done:
        conditions.append(AgentMessage.status == "done")

    rows = (
        await session.scalars(
            select(AgentMessage)
            .where(*conditions)
            .order_by(AgentMessage.seq.desc())
            .limit(limit)
        )
    ).all()
    return list(reversed(rows))
