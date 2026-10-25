import logging
import uuid
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from enum import StrEnum
from typing import Any
from uuid import UUID

from sqlalchemy.exc import IntegrityError, SQLAlchemyError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.response import BusinessError, ErrorCode
from db import repository
from db.models import AgentMessage

log = logging.getLogger(__name__)

TITLE_MAX_CHARS = 50


class TurnOutcome(StrEnum):
    NEW = "new"
    REPLAY = "replay"
    IN_PROGRESS = "in_progress"


@dataclass(frozen=True, slots=True)
class ReplayAnswer:
    content: str
    sources: tuple[dict[str, Any], ...] = ()


@dataclass(frozen=True, slots=True)
class TurnContext:
    conversation_id: str
    message_id: str
    user_id: int
    question: str
    assistant_row_id: UUID
    outcome: TurnOutcome = TurnOutcome.NEW
    replay: ReplayAnswer | None = None


def _title(question: str) -> str:
    return " ".join(question.split())[:TITLE_MAX_CHARS] or "新会话"


async def _resolve_existing(
    session: AsyncSession,
    existing: AgentMessage,
    *,
    user_id: int,
    now: datetime,
    ttl_seconds: int,
) -> tuple[TurnOutcome, ReplayAnswer | None]:
    for _ in range(2):
        if existing.status == "done":
            return TurnOutcome.REPLAY, ReplayAnswer(
                content=existing.content,
                sources=tuple(existing.sources or ()),
            )
        if (
            existing.status == "in_progress"
            and existing.expires_at is not None
            and existing.expires_at > now
        ):
            return TurnOutcome.IN_PROGRESS, None

        claimed = await repository.claim_assistant(
            session,
            row_id=existing.id,
            conversation_id=existing.conversation_id,
            user_id=user_id,
            now=now,
            expires_at=now + timedelta(seconds=ttl_seconds),
        )
        if claimed:
            log.info(
                "接管消息 conversation=%s message=%s previous=%s",
                existing.conversation_id,
                existing.message_id,
                existing.status,
            )
            return TurnOutcome.NEW, None

        refreshed = await repository.get_assistant_message(
            session,
            conversation_id=existing.conversation_id,
            message_id=existing.message_id,
            user_id=user_id,
        )
        if refreshed is None:
            break
        existing = refreshed

    return TurnOutcome.IN_PROGRESS, None


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
    outcome = TurnOutcome.NEW
    replay: ReplayAnswer | None = None
    effective_question = question

    try:
        async with session_factory() as session:
            async with session.begin():
                if conversation_id is not None:
                    conversation = await repository.get_conversation(
                        session, conversation_uuid, user_id
                    )
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

                await repository.insert_user_message_if_absent(
                    session,
                    row_id=uuid.uuid4(),
                    conversation_id=conversation_uuid,
                    message_id=message_id,
                    user_id=user_id,
                    question=question,
                    now=now,
                )
                user_row = await repository.get_user_message(
                    session,
                    conversation_id=conversation_uuid,
                    message_id=message_id,
                    user_id=user_id,
                )
                if user_row is not None:
                    effective_question = user_row.content

                inserted_id = await repository.insert_assistant_placeholder_if_absent(
                    session,
                    row_id=assistant_row_id,
                    conversation_id=conversation_uuid,
                    message_id=message_id,
                    user_id=user_id,
                    now=now,
                    expires_at=now + timedelta(seconds=ttl_seconds),
                )
                if inserted_id is not None:
                    assistant_row_id = inserted_id
                    await repository.touch_conversation(
                        session, conversation_uuid, user_id, now
                    )
                else:
                    existing = await repository.get_assistant_message(
                        session,
                        conversation_id=conversation_uuid,
                        message_id=message_id,
                        user_id=user_id,
                    )
                    if existing is None:
                        log.error(
                            "assistant 幂等行缺失 conversation=%s message=%s",
                            conversation_uuid,
                            message_id,
                        )
                        raise RuntimeError("assistant 幂等行缺失")
                    assistant_row_id = existing.id
                    outcome, replay = await _resolve_existing(
                        session,
                        existing,
                        user_id=user_id,
                        now=now,
                        ttl_seconds=ttl_seconds,
                    )
                    if outcome is TurnOutcome.REPLAY:
                        log.info(
                            "幂等重放 conversation=%s message=%s",
                            conversation_uuid,
                            message_id,
                        )
                    elif outcome is TurnOutcome.IN_PROGRESS:
                        log.info(
                            "消息生成中 conversation=%s message=%s",
                            conversation_uuid,
                            message_id,
                        )
    except IntegrityError as exc:
        log.exception("会话库约束冲突")
        raise BusinessError(ErrorCode.INTERNAL_ERROR) from exc
    except (SQLAlchemyError, OSError, TimeoutError) as exc:
        log.exception("会话库写入失败")
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "会话服务暂不可用") from exc

    return TurnContext(
        conversation_id=str(conversation_uuid),
        message_id=message_id,
        user_id=user_id,
        question=effective_question,
        assistant_row_id=assistant_row_id,
        outcome=outcome,
        replay=replay,
    )
