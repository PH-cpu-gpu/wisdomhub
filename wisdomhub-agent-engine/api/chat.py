import contextlib
import uuid
from collections.abc import AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.sse import EventSourceResponse, ServerSentEvent
from pydantic import BaseModel, field_validator
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from agents.base import ChatContext, ChatStreamer
from agents.echo import EchoStreamer
from agents.persistence import PersistingStreamer
from api.sse import encode, guard_events, meta_event
from app.config import Settings, get_settings
from db.session import get_session_factory
from db.turn import TurnContext, prepare_turn
from security.user import current_user_id

router = APIRouter(prefix="/api/agent", tags=["agent"])

MAX_QUESTION_CHARS = 4000
MAX_MESSAGE_ID_CHARS = 64


class ChatRequest(BaseModel):
    conversationId: str | None = None
    messageId: str | None = None
    question: str

    @field_validator("question")
    @classmethod
    def _check_question(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("问题不能为空")
        if len(value) > MAX_QUESTION_CHARS:
            raise ValueError(f"问题长度不能超过 {MAX_QUESTION_CHARS} 字")
        return value

    @field_validator("conversationId")
    @classmethod
    def _check_conversation_id(cls, value: str | None) -> str | None:
        value = value.strip() if value else ""
        if not value:
            return None
        try:
            return str(uuid.UUID(value))
        except ValueError:
            raise ValueError("conversationId 格式不正确") from None

    @field_validator("messageId")
    @classmethod
    def _check_message_id(cls, value: str | None) -> str | None:
        value = value.strip() if value else ""
        if not value:
            return None
        if len(value) > MAX_MESSAGE_ID_CHARS:
            raise ValueError(f"messageId 长度不能超过 {MAX_MESSAGE_ID_CHARS} 字")
        return value


def get_chat_streamer() -> ChatStreamer:
    """TODO B3.18接真实工作流时替换这里。"""
    return EchoStreamer()


async def prepare_turn_dep(
    request: ChatRequest,
    user_id: Annotated[int, Depends(current_user_id)],
    settings: Annotated[Settings, Depends(get_settings)],
    session_factory: Annotated[async_sessionmaker[AsyncSession], Depends(get_session_factory)],
) -> TurnContext:
    return await prepare_turn(
        session_factory,
        user_id=user_id,
        question=request.question,
        conversation_id=request.conversationId,
        message_id=request.messageId,
        ttl_seconds=settings.message_in_progress_ttl,
    )


@router.post(
    "/chat",
    response_class=EventSourceResponse,
    summary="AI 技术问答（SSE）",
    description="事件顺序：meta → delta* → sources? → done/error；data 为单行 JSON。",
)
async def chat(
    turn: Annotated[TurnContext, Depends(prepare_turn_dep)],
    streamer: Annotated[ChatStreamer, Depends(get_chat_streamer)],
    settings: Annotated[Settings, Depends(get_settings)],
    session_factory: Annotated[async_sessionmaker[AsyncSession], Depends(get_session_factory)],
) -> AsyncIterator[ServerSentEvent]:
    ctx = ChatContext(
        user_id=turn.user_id,
        conversation_id=turn.conversation_id,
        message_id=turn.message_id,
        question=turn.question,
    )
    persisted = PersistingStreamer(streamer, session_factory=session_factory, turn=turn)

    stream = guard_events(persisted.stream(ctx), idle_timeout=settings.stream_idle_timeout)
    try:
        yield encode(meta_event(turn.conversation_id, turn.message_id))
        async for event in stream:
            yield encode(event)
    finally:
        with contextlib.suppress(Exception):
            await stream.aclose()
