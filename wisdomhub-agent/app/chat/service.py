"""回合编排：幂等准备、上下文、图流、终态落库。"""

import asyncio
import logging
from collections.abc import AsyncGenerator, AsyncIterator, Mapping
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any
from uuid import UUID, uuid4

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage, SystemMessage
from langgraph.graph.state import CompiledStateGraph

from app.chat.graph.build import RECURSION_LIMIT
from app.chat.graph.node import message_text
from app.chat.stream import map_events
from app.contracts.artifacts import MessageArtifacts, RouteData
from app.contracts.chat_events import RouteEvent
from app.contracts.sse import (
    DeltaEvent,
    DoneEvent,
    ErrorEvent,
    MetaEvent,
    SseEvent,
    Step,
    StepsEvent,
)
from app.contracts.workflow import WorkflowKind
from app.core.config import Settings
from app.core.errors import BusinessError, ErrorCode, friendly_error
from app.features.conversation.repository import TerminalStatus
from app.features.conversation.service import ConversationService, PreparedTurn, TurnOutcome
from app.runtime.context import build_context

log = logging.getLogger(__name__)

FALLBACK_ANSWER = "抱歉，我暂时不能生成回答，请换个问法再试一次。"
IN_PROGRESS_MESSAGE = "该消息正在生成中，请稍后重试"
PERSIST_FAILED_MESSAGE = "回答保存失败，请稍后重试"
INTERNAL_MESSAGE = "AI 服务内部异常"


@dataclass(frozen=True, slots=True)
class ChatTurnInput:
    user_id: int
    workflow: WorkflowKind
    question: str
    conversation_id: UUID | None = None
    message_id: str | None = None
    technology: str | None = None
    known: list[str] = field(default_factory=list)
    text: str | None = None


async def run_chat_turn(
    turn: ChatTurnInput,
    *,
    conversation: ConversationService,
    graph: CompiledStateGraph,
    settings: Settings,
) -> AsyncGenerator[SseEvent]:
    message_id = turn.message_id or str(uuid4())
    try:
        prepared = await conversation.prepare_turn(
            user_id=turn.user_id,
            message_id=message_id,
            workflow=turn.workflow,
            question=turn.question,
            conversation_id=turn.conversation_id,
        )
    except BusinessError as exc:
        yield ErrorEvent(code=int(exc.code), message=exc.message)
        return

    if prepared.outcome is TurnOutcome.REPLAY:
        yield _meta(prepared)
        for event in _replay_events(prepared):
            yield event
        return

    if prepared.outcome is TurnOutcome.IN_PROGRESS:
        yield _meta(prepared)
        yield ErrorEvent(code=int(ErrorCode.CONFLICT), message=IN_PROGRESS_MESSAGE)
        return

    events = _stream_turn(turn, prepared, conversation=conversation, graph=graph, settings=settings)
    try:
        async for event in events:
            yield event
    finally:
        await events.aclose()


async def _stream_turn(
    turn: ChatTurnInput,
    prepared: PreparedTurn,
    *,
    conversation: ConversationService,
    graph: CompiledStateGraph,
    settings: Settings,
) -> AsyncGenerator[SseEvent]:
    lease = prepared.expires_at
    if lease is None:
        raise RuntimeError("NEW 回合缺少租约")
    state_box: dict[str, Any] = {}
    parts: list[str] = []

    try:
        messages = await _initial_messages(conversation, prepared, settings)
        raw = graph.astream(
            _graph_input(turn, prepared, messages),
            stream_mode=["messages", "values"],
            version="v2",
            config={"recursion_limit": RECURSION_LIMIT},
        )
        try:
            stream = map_events(
                _tap(raw, state_box),
                conversation_id=str(prepared.conversation_id),
                message_id=prepared.message_id,
                workflow=prepared.workflow,
            )
            async for event in stream:
                if isinstance(event, DeltaEvent):
                    parts.append(event.text)
                yield event
        finally:
            await _close(raw)
    except (asyncio.CancelledError, GeneratorExit):
        await asyncio.shield(
            _persist(conversation, prepared, lease, "aborted", "".join(parts), state_box)
        )
        raise
    except Exception as exc:
        code, message = _error_of(exc)
        log.warning("回合失败: %s", message, exc_info=exc)
        await _persist(conversation, prepared, lease, "error", "".join(parts), state_box)
        yield ErrorEvent(code=int(code), message=message)
        return

    answer = _final_answer(state_box) or "".join(parts)
    if not answer:
        answer = FALLBACK_ANSWER
        yield DeltaEvent(text=answer)
    elif not parts:
        yield DeltaEvent(text=answer)

    persisted = await _persist(conversation, prepared, lease, "done", answer, state_box)
    if not persisted:
        yield ErrorEvent(code=int(ErrorCode.INTERNAL_ERROR), message=PERSIST_FAILED_MESSAGE)
        return
    yield DoneEvent()


def _meta(prepared: PreparedTurn) -> MetaEvent:
    return MetaEvent(
        conversation_id=str(prepared.conversation_id),
        message_id=prepared.message_id,
        workflow=prepared.workflow,
    )


def _replay_events(prepared: PreparedTurn) -> list[SseEvent]:
    assistant = prepared.assistant
    events: list[SseEvent] = []
    if assistant is not None:
        artifacts = _parse_artifacts(assistant.artifacts)
        if artifacts is not None and artifacts.route is not None:
            events.append(_route_event(artifacts.route))
        steps = _parse_steps(assistant.steps)
        if steps:
            events.append(StepsEvent(steps=steps))
        if assistant.content:
            events.append(DeltaEvent(text=assistant.content))
    events.append(DoneEvent())
    return events


def _parse_artifacts(value: Mapping[str, Any] | None) -> MessageArtifacts | None:
    if not value:
        return None
    return MessageArtifacts.model_validate(value)


def _parse_steps(value: Any) -> list[Step]:
    if not isinstance(value, list) or not value:
        return []
    return [step if isinstance(step, Step) else Step.model_validate(step) for step in value]


def _route_event(route: RouteData) -> RouteEvent:
    return RouteEvent(
        target=route.target,
        known=route.known,
        truncated=route.truncated,
        stages=route.stages,
    )


async def _initial_messages(
    conversation: ConversationService,
    prepared: PreparedTurn,
    settings: Settings,
) -> list[BaseMessage]:
    history = await conversation.history(prepared.conversation_id)
    prior = [message for message in history if message.message_id != prepared.message_id]
    context = await build_context(
        prior,
        prepared.question,
        max_turns=settings.limits.context_max_turns,
        max_tokens=settings.limits.context_max_tokens,
        message_max_tokens=settings.limits.context_message_max_tokens,
    )
    messages: list[BaseMessage] = []
    if context.notice:
        messages.append(SystemMessage(content=context.notice))
    messages.extend(context.history)
    messages.append(HumanMessage(content=context.question))
    return messages


def _graph_input(
    turn: ChatTurnInput,
    prepared: PreparedTurn,
    messages: list[BaseMessage],
) -> dict[str, Any]:
    data: dict[str, Any] = {"messages": messages, "workflow": prepared.workflow}
    if turn.workflow is WorkflowKind.LEARNING_PATH:
        data["technology"] = turn.technology or ""
        data["known"] = list(turn.known)
    elif turn.workflow is WorkflowKind.TEXT_QA and turn.text:
        data["text"] = turn.text
    return data


async def _close(stream: Any) -> None:
    close = getattr(stream, "aclose", None)
    if callable(close):
        await close()


async def _tap(
    stream: AsyncIterator[Mapping[str, Any]],
    state_box: dict[str, Any],
) -> AsyncIterator[Mapping[str, Any]]:
    async for part in stream:
        if part.get("type") == "values":
            data = part.get("data")
            if isinstance(data, Mapping):
                state_box.clear()
                state_box.update(data)
        yield part


async def _persist(
    conversation: ConversationService,
    prepared: PreparedTurn,
    lease: datetime,
    status: TerminalStatus,
    content: str,
    state_box: Mapping[str, Any],
) -> bool:
    try:
        return await conversation.finish_turn(
            conversation_id=prepared.conversation_id,
            message_id=prepared.message_id,
            status=status,
            content=content,
            steps=_steps_payload(state_box),
            artifacts=_artifacts_payload(state_box),
            workflow=prepared.workflow,
            lease_expires_at=lease,
        )
    except Exception:
        log.exception("回合落库失败: message_id=%s", prepared.message_id)
        return False


def _final_answer(state: Mapping[str, Any]) -> str:
    messages = state.get("messages")
    if not isinstance(messages, list):
        return ""
    for message in reversed(messages):
        if isinstance(message, AIMessage):
            text = message_text(message)
            if text.strip():
                return text
    return ""


def _steps_payload(state: Mapping[str, Any]) -> list[dict[str, Any]] | None:
    steps = _parse_steps(state.get("steps"))
    if not steps:
        return None
    return [step.model_dump(mode="json") for step in steps]


def _artifacts_payload(state: Mapping[str, Any]) -> dict[str, Any] | None:
    route = state.get("route")
    if route is None:
        return None
    data = route if isinstance(route, RouteData) else RouteData.model_validate(route)
    return MessageArtifacts(route=data).model_dump(mode="json", by_alias=True)


def _error_of(exc: BaseException) -> tuple[ErrorCode, str]:
    friendly = friendly_error(exc)
    if friendly is not None:
        return friendly
    return ErrorCode.INTERNAL_ERROR, INTERNAL_MESSAGE
