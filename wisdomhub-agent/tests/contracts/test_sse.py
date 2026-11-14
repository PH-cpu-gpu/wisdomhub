import json
from typing import Any

import pytest
from pydantic import ValidationError

from app.contracts.sse import (
    DeltaEvent,
    DoneEvent,
    ErrorEvent,
    MetaEvent,
    SseEvent,
    Step,
    StepsEvent,
    StepStatus,
)
from app.contracts.workflow import WorkflowKind

TRANSPORT_EVENTS = (MetaEvent, StepsEvent, DeltaEvent, DoneEvent, ErrorEvent)


def _data(event: SseEvent) -> dict[str, Any]:
    frame = event.encode()
    assert frame.raw_data is not None
    return json.loads(frame.raw_data)


def test_transport_event_names_and_terminal_flags() -> None:
    names = [event.EVENT for event in TRANSPORT_EVENTS]
    assert names == ["meta", "steps", "delta", "done", "error"]
    assert len(set(names)) == len(names)
    assert {event.EVENT for event in TRANSPORT_EVENTS if event.TERMINAL} == {"done", "error"}


def test_meta_event_shape() -> None:
    event = MetaEvent(
        conversation_id="c-1",
        message_id="m-1",
        workflow=WorkflowKind.LEARNING_PATH,
    )
    frame = event.encode()
    assert frame.event == "meta"
    assert _data(event) == {"conversationId": "c-1", "messageId": "m-1", "workflow": 2}


def test_meta_rejects_unknown_workflow() -> None:
    with pytest.raises(ValidationError):
        MetaEvent.model_validate({"conversationId": "c-1", "messageId": "m-1", "workflow": 9})


def test_steps_event_shape() -> None:
    event = StepsEvent(
        steps=[
            Step(
                id="s1",
                tool="query_prerequisite_chain",
                label="查询前置知识链",
                status=StepStatus.DONE,
                summary="返回 3 层前置",
            )
        ]
    )
    assert _data(event) == {
        "steps": [
            {
                "id": "s1",
                "tool": "query_prerequisite_chain",
                "label": "查询前置知识链",
                "status": "done",
                "summary": "返回 3 层前置",
            }
        ]
    }


def test_step_summary_is_omitted_when_none() -> None:
    event = StepsEvent(steps=[Step(id="s1", tool="t", label="l", status=StepStatus.RUNNING)])
    assert "summary" not in _data(event)["steps"][0]


def test_step_rejects_unknown_status() -> None:
    with pytest.raises(ValidationError):
        Step.model_validate({"id": "s1", "tool": "t", "label": "l", "status": "unknown"})


def test_delta_event_keeps_json_on_one_line() -> None:
    event = DeltaEvent(text="第一行\n第二行")
    frame = event.encode()
    assert frame.raw_data is not None
    assert "\n" not in frame.raw_data
    assert "第一行" in frame.raw_data
    assert json.loads(frame.raw_data) == {"text": "第一行\n第二行"}


def test_done_event_shape_and_terminal() -> None:
    event = DoneEvent()
    assert DoneEvent.TERMINAL is True
    assert event.encode().event == "done"
    assert _data(event) == {"finishReason": "stop"}


def test_error_event_shape_and_terminal() -> None:
    event = ErrorEvent(code=503, message="AI 服务暂不可用")
    assert ErrorEvent.TERMINAL is True
    assert _data(event) == {"code": 503, "message": "AI 服务暂不可用"}


def test_extra_fields_are_forbidden() -> None:
    with pytest.raises(ValidationError):
        MetaEvent.model_validate(
            {"conversationId": "c-1", "messageId": "m-1", "workflow": 1, "extra": 1}
        )
