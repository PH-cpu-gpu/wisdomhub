"""图输出映射为 SSE 事件：meta、route、steps 与 delta。"""

from collections.abc import AsyncIterator, Mapping, Sequence
from typing import Any

from langchain_core.messages import AIMessage

from app.chat.graph.node import LEARNING_AGENT, QA_AGENT, TEXT_QA_AGENT, message_text
from app.contracts.artifacts import RouteData
from app.contracts.chat_events import RouteEvent
from app.contracts.sse import DeltaEvent, MetaEvent, SseEvent, Step, StepsEvent
from app.contracts.workflow import WorkflowKind

TOKEN_NODES = frozenset({QA_AGENT, LEARNING_AGENT, TEXT_QA_AGENT})
StepSnapshot = tuple[tuple[str, str, str, str, str | None], ...]


async def map_events(
    stream: AsyncIterator[Mapping[str, Any]],
    *,
    conversation_id: str,
    message_id: str,
    workflow: WorkflowKind,
) -> AsyncIterator[SseEvent]:
    yield MetaEvent(conversation_id=conversation_id, message_id=message_id, workflow=workflow)
    route_sent = False
    last_steps: StepSnapshot = ()
    poisoned: set[str] = set()

    async for part in stream:
        kind = part.get("type")
        if kind == "values":
            state = part.get("data")
            if not isinstance(state, Mapping):
                continue
            route = state.get("route")
            if route is not None and not route_sent:
                yield _route_event(route)
                route_sent = True
            steps = _validated_steps(state.get("steps"))
            snapshot = _steps_snapshot(steps)
            if snapshot and snapshot != last_steps:
                yield StepsEvent(steps=steps)
                last_steps = snapshot
        elif kind == "messages":
            event = _message_event(part, poisoned)
            if event is not None:
                yield event


def _route_event(route: Any) -> RouteEvent:
    data = route if isinstance(route, RouteData) else RouteData.model_validate(route)
    return RouteEvent(
        target=data.target,
        known=data.known,
        truncated=data.truncated,
        stages=data.stages,
    )


def _validated_steps(value: Any) -> list[Step]:
    if not isinstance(value, list) or not value:
        return []
    return [step if isinstance(step, Step) else Step.model_validate(step) for step in value]


def _steps_snapshot(steps: Sequence[Step]) -> StepSnapshot:
    return tuple((step.id, step.tool, step.label, str(step.status), step.summary) for step in steps)


def _message_event(part: Mapping[str, Any], poisoned: set[str]) -> DeltaEvent | None:
    payload = part.get("data")
    if not isinstance(payload, tuple) or len(payload) != 2:
        return None
    message, metadata = payload
    if not isinstance(message, AIMessage) or not isinstance(metadata, Mapping):
        return None
    if metadata.get("langgraph_node") not in TOKEN_NODES:
        return None

    response_id = message.id or ""
    if getattr(message, "tool_call_chunks", None) or getattr(message, "tool_calls", None):
        poisoned.add(response_id)
    if response_id in poisoned:
        return None

    text = message_text(message)
    return DeltaEvent(text=text) if text else None
