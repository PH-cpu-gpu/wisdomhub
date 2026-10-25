import logging
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from uuid import UUID

from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.response import BusinessError, ErrorCode
from db import repository

log = logging.getLogger(__name__)

TITLE_MAX_CHARS = 50


@dataclass(frozen=True, slots=True)
class TurnContext:
    conversation_id: str
    message_id: str
    user_id: int
    question: str
    assistant_row_id: UUID


def _title(question: str) -> str:
    return " ".join(question.split())[:TITLE_MAX_CHARS] or "新会话"


async def prepare_turn(
    session_factory: async_sessionmaker[AsyncSession],
    *,
    user_id: int,
    question: str,
    conversation_id: str | None,
    message_id: str | None,
    ttl_seconds: int,
) -> TurnContext:
    now = datetime.now(UTC)
    message_id = message_id or str(uuid.uuid4())
    conversation_uuid = UUID(conversation_id) if conversation_id else uuid.uuid4()
    assistant_row_id = uuid.uuid4()

    try:
        async with session_factory() as session:
            async with session.begin():
                if conversation_id is not None:
                    conversation = await repository.get_conversation(session, conversation_uuid, user_id)
                    if conversation is None:
                        raise BusinessError(ErrorCode.NOT_FOUND)
                else:
                    await repository.create_conversation(
                        session,
                        conversation_id=conversation_uuid,
                        user_id=user_id,
                        title=_title(question),
                        now=now,
                    )
                await repository.insert_user_message(
                    session,
                    row_id=uuid.uuid4(),
                    conversation_id=conversation_uuid,
                    message_id=message_id,
                    user_id=user_id,
                    question=question,
                    now=now,
                )
                await repository.insert_assistant_placeholder(
                    session,
                    row_id=assistant_row_id,
                    conversation_id=conversation_uuid,
                    message_id=message_id,
                    user_id=user_id,
                    now=now,
                    expires_at=now + timedelta(seconds=ttl_seconds),
                )
                await repository.touch_conversation(session, conversation_uuid, user_id, now)
    except IntegrityError as exc:
        log.warning("回合唯一键冲突: %s", exc)
        raise BusinessError(ErrorCode.CONFLICT) from exc
    except (SQLAlchemyError, OSError, TimeoutError) as exc:
        log.exception("会话库写入失败")
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "会话服务暂不可用") from exc

    return TurnContext(
        conversation_id=str(conversation_uuid),
        message_id=message_id,
        user_id=user_id,
        question=question,
        assistant_row_id=assistant_row_id,
    )
