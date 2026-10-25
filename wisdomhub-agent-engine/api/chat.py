import contextlib
import logging
import uuid
from collections.abc import AsyncIterator
from typing import Annotated, Literal

from fastapi import APIRouter, Depends, Request
from fastapi.sse import EventSourceResponse, ServerSentEvent
from pydantic import BaseModel, Field, field_validator
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from agents.base import ChatContext, ChatStreamer, TurnTrace
from agents.context import ContextAssembler, PromptContext
from agents.persistence import PersistingStreamer
from agents.workflow import WorkflowStreamer
from api.sse import (
    delta_event,
    done_event,
    encode,
    error_event,
    guard_events,
    meta_event,
)
from app.config import Settings, get_settings
from app.response import BusinessError, ErrorCode
from db import repository
from db.session import get_session_factory
from db.turn import TurnContext, TurnOutcome, prepare_turn
from models.tokenizer import TiktokenCounter
from prompts.learning import LEARNING_PROMPT
from security.user import current_user_id

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/agent", tags=["agent"])

MAX_QUESTION_CHARS = 4000
MAX_MESSAGE_ID_CHARS = 64


class ChatRequest(BaseModel):
    conversationId: str | None = None
    messageId: str | None = None
    workflow: Literal["qa", "learning_path"] = "qa"
    technology: str | None = None
    knownTechnologies: list[str] = Field(default_factory=list)
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

    @field_validator("technology")
    @classmethod
    def _check_technology(cls, value: str | None) -> str | None:
        value = value.strip() if value else ""
        if not value:
            return None
        if len(value) > 50:
            raise ValueError("技术名长度不能超过 50 字")
        return value

    @field_validator("knownTechnologies")
    @classmethod
    def _check_known_technologies(cls, value: list[str]) -> list[str]:
        cleaned = [item.strip() for item in value if item and item.strip()]
        if len(cleaned) > 20:
            raise ValueError("已掌握技术不能超过 20 个")
        if any(len(item) > 50 for item in cleaned):
            raise ValueError("单个技术名长度不能超过 50 字")
        return cleaned


def get_chat_request(payload: ChatRequest) -> ChatRequest:
    return payload


def get_turn_trace() -> TurnTrace:
    return TurnTrace()


def ensure_workflow_available(
    request: Request,
    payload: Annotated[ChatRequest, Depends(get_chat_request)],
) -> None:
    if payload.workflow == "learning_path" and not getattr(request.app.state, "java_tools", []):
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "学习路线需要检索服务")


def get_chat_streamer(
    request: Request,
    payload: Annotated[ChatRequest, Depends(get_chat_request)],
    trace: Annotated[TurnTrace, Depends(get_turn_trace)],
) -> ChatStreamer:
    if payload.workflow == "learning_path":
        graph = getattr(request.app.state, "learning_graph", None)
        if graph is None:
            raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "学习路线工作流未初始化")
        return WorkflowStreamer(
            graph,
            trace=trace,
            prompt=LEARNING_PROMPT,
            extra_state={
                "technology": payload.technology or "",
                "known": payload.knownTechnologies,
            },
        )

    graph = getattr(request.app.state, "qa_graph", None)
    if graph is None:
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "AI 工作流未初始化")
    return WorkflowStreamer(graph, trace=trace)


async def prepare_turn_dep(
    payload: Annotated[ChatRequest, Depends(get_chat_request)],
    _ready: Annotated[None, Depends(ensure_workflow_available)],
    user_id: Annotated[int, Depends(current_user_id)],
    settings: Annotated[Settings, Depends(get_settings)],
    session_factory: Annotated[async_sessionmaker[AsyncSession], Depends(get_session_factory)],
) -> TurnContext:
    return await prepare_turn(
        session_factory,
        user_id=user_id,
        question=payload.question,
        conversation_id=payload.conversationId,
        message_id=payload.messageId,
        ttl_seconds=settings.message_in_progress_ttl,
    )


async def build_context_dep(
    turn: Annotated[TurnContext, Depends(prepare_turn_dep)],
    settings: Annotated[Settings, Depends(get_settings)],
    session_factory: Annotated[async_sessionmaker[AsyncSession], Depends(get_session_factory)],
) -> PromptContext:
    if turn.outcome is not TurnOutcome.NEW:
        return PromptContext(
            turn=turn, history=(), dropped_turns=0, summary=None, history_tokens=0
        )

    async with session_factory() as session:
        rows = await repository.list_recent_messages(
            session,
            conversation_id=uuid.UUID(turn.conversation_id),
            user_id=turn.user_id,
            limit=max(1, settings.context_max_turns) * 2,
            exclude_message_id=turn.message_id,
            only_done=True,
        )
    prompt = await ContextAssembler(settings, TiktokenCounter()).assemble(turn, rows)
    log.info(
        "上下文组装 conversation=%s history=%d dropped=%d tokens=%d",
        turn.conversation_id,
        len(prompt.history),
        prompt.dropped_turns,
        prompt.history_tokens,
    )
    return prompt


@router.post(
    "/chat",
    response_class=EventSourceResponse,
    summary="AI 技术问答（SSE）",
    description="事件顺序：meta → delta* → done/error；data 为单行 JSON。",
)
async def chat(
    prompt: Annotated[PromptContext, Depends(build_context_dep)],
    streamer: Annotated[ChatStreamer, Depends(get_chat_streamer)],
    trace: Annotated[TurnTrace, Depends(get_turn_trace)],
    settings: Annotated[Settings, Depends(get_settings)],
    session_factory: Annotated[async_sessionmaker[AsyncSession], Depends(get_session_factory)],
) -> AsyncIterator[ServerSentEvent]:
    turn = prompt.turn

    if turn.outcome is TurnOutcome.REPLAY:
        yield encode(meta_event(turn.conversation_id, turn.message_id))
        if turn.replay is None:
            yield encode(error_event(ErrorCode.INTERNAL_ERROR, "会话状态异常"))
            return
        yield encode(delta_event(turn.replay.content))
        yield encode(done_event())
        return

    if turn.outcome is TurnOutcome.IN_PROGRESS:
        yield encode(meta_event(turn.conversation_id, turn.message_id))
        yield encode(error_event(ErrorCode.CONFLICT, "该消息正在生成中，请稍后重试"))
        return

    ctx = ChatContext(
        user_id=turn.user_id,
        conversation_id=turn.conversation_id,
        message_id=turn.message_id,
        question=turn.question,
        history=prompt.history,
        dropped_turns=prompt.dropped_turns,
        summary=prompt.summary,
    )
    persisted = PersistingStreamer(
        streamer, session_factory=session_factory, turn=turn, trace=trace
    )

    stream = guard_events(persisted.stream(ctx), idle_timeout=settings.stream_idle_timeout)
    try:
        yield encode(meta_event(turn.conversation_id, turn.message_id))
        async for event in stream:
            yield encode(event)
    finally:
        with contextlib.suppress(Exception):
            await stream.aclose()
