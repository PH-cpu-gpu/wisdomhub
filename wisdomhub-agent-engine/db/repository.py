from datetime import datetime
from uuid import UUID

from sqlalchemy import select, update
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


async def insert_user_message(
    session: AsyncSession,
    *,
    row_id: UUID,
    conversation_id: UUID,
    message_id: str,
    user_id: int,
    question: str,
    now: datetime,
) -> None:
    session.add(
        AgentMessage(
            id=row_id,
            conversation_id=conversation_id,
            message_id=message_id,
            user_id=user_id,
            role="user",
            content=question,
            sources=[],
            status="done",
            created_at=now,
        )
    )


async def insert_assistant_placeholder(
    session: AsyncSession,
    *,
    row_id: UUID,
    conversation_id: UUID,
    message_id: str,
    user_id: int,
    now: datetime,
    expires_at: datetime,
) -> None:
    session.add(
        AgentMessage(
            id=row_id,
            conversation_id=conversation_id,
            message_id=message_id,
            user_id=user_id,
            role="assistant",
            content="",
            sources=[],
            status="in_progress",
            created_at=now,
            expires_at=expires_at,
        )
    )


async def finish_assistant_message(
    session: AsyncSession,
    *,
    row_id: UUID,
    user_id: int,
    content: str,
    sources: list[dict],
    status: str,
) -> int:
    result = await session.execute(
        update(AgentMessage)
        .where(
            AgentMessage.id == row_id,
            AgentMessage.user_id == user_id,
            AgentMessage.role == "assistant",
        )
        .values(content=content, sources=sources, status=status, expires_at=None)
    )
    return result.rowcount


async def list_recent_messages(
    session: AsyncSession, *, conversation_id: UUID, user_id: int, limit: int
) -> list[AgentMessage]:
    rows = (
        await session.scalars(
            select(AgentMessage)
            .where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.user_id == user_id,
            )
            .order_by(AgentMessage.seq.desc())
            .limit(limit)
        )
    ).all()
    return list(reversed(rows))
