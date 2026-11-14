import json
from typing import Any, get_args

import pytest
from pydantic import ValidationError

from app.contracts.artifacts import PlanStage, RouteData, TechRef
from app.contracts.chat_events import (
    LearningStreamEvent,
    QaStreamEvent,
    RouteEvent,
    TextQaStreamEvent,
)
from app.contracts.sse import DeltaEvent, DoneEvent, ErrorEvent, MetaEvent, SseEvent, StepsEvent


def _data(event: SseEvent) -> dict[str, Any]:
    frame = event.encode()
    assert frame.raw_data is not None
    return json.loads(frame.raw_data)


def _route() -> RouteEvent:
    return RouteEvent(
        target=TechRef(tag_id="10", name="Redis"),
        known=["Java"],
        truncated=False,
        stages=[
            PlanStage(depth=2, tags=[TechRef(tag_id="21", name="计算机网络")]),
            PlanStage(depth=0, tags=[TechRef(tag_id="10", name="Redis")]),
        ],
    )


def test_route_event_inheritance_and_name() -> None:
    assert issubclass(RouteEvent, RouteData)
    assert issubclass(RouteEvent, SseEvent)
    assert RouteEvent.EVENT == "route"
    assert RouteEvent.TERMINAL is False


def test_endpoint_stream_event_aliases() -> None:
    qa_members = set(get_args(QaStreamEvent))
    assert qa_members == {MetaEvent, DeltaEvent, DoneEvent, ErrorEvent}
    assert set(get_args(TextQaStreamEvent)) == qa_members
    assert set(get_args(LearningStreamEvent)) == qa_members | {StepsEvent, RouteEvent}


def test_route_event_shape() -> None:
    event = _route()
    frame = event.encode()
    assert frame.event == "route"
    assert _data(event) == {
        "stages": [
            {"depth": 2, "tags": [{"tagId": "21", "name": "计算机网络"}]},
            {"depth": 0, "tags": [{"tagId": "10", "name": "Redis"}]},
        ],
        "target": {"tagId": "10", "name": "Redis"},
        "known": ["Java"],
        "truncated": False,
    }


def test_route_allows_empty_known_and_stages() -> None:
    event = RouteEvent(
        target=TechRef(tag_id="10", name="Redis"),
        known=[],
        truncated=True,
        stages=[],
    )
    data = _data(event)
    assert data["known"] == []
    assert data["stages"] == []


def test_route_rejects_extra_fields() -> None:
    with pytest.raises(ValidationError):
        RouteEvent.model_validate(
            {
                "target": {"tagId": "10", "name": "Redis"},
                "known": [],
                "truncated": False,
                "stages": [],
                "extra": 1,
            }
        )
