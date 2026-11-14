from datetime import UTC, datetime
from uuid import UUID

import pytest
from pydantic import ValidationError

from app.contracts.artifacts import MessageArtifacts, PlanStage, RouteData, TechRef
from app.contracts.conversation import (
    ConversationDetailVO,
    ConversationItemVO,
    ConversationMessageVO,
    ConversationPageVO,
)
from app.contracts.sse import Step, StepStatus
from app.contracts.workflow import WorkflowKind

CONVERSATION_ID = "550e8400-e29b-41d4-a716-446655440000"
MESSAGE_ROW_ID = "7c9e6679-7425-40de-944b-e07fc1f90ae7"
NOW = datetime(2026, 11, 14, 12, 0, tzinfo=UTC)


def _route() -> RouteData:
    return RouteData(
        target=TechRef(tag_id="10", name="Redis"),
        known=["Java"],
        truncated=False,
        stages=[
            PlanStage(depth=0, tags=[TechRef(tag_id="10", name="Redis")]),
        ],
    )


def _message(**overrides: object) -> ConversationMessageVO:
    payload: dict[str, object] = {
        "id": MESSAGE_ROW_ID,
        "messageId": "m-1",
        "role": "assistant",
        "content": "Redis 的持久化方式有 RDB 和 AOF。",
        "workflow": 1,
        "status": "done",
        "createdAt": NOW,
    }
    payload.update(overrides)
    return ConversationMessageVO.model_validate(payload)


def _item(**overrides: object) -> ConversationItemVO:
    payload: dict[str, object] = {
        "id": CONVERSATION_ID,
        "title": "Redis 持久化",
        "workflow": 1,
        "createdAt": NOW,
        "updatedAt": NOW,
    }
    payload.update(overrides)
    return ConversationItemVO.model_validate(payload)


def test_conversation_item_shape() -> None:
    item = _item()

    assert item.id == UUID(CONVERSATION_ID)
    assert item.workflow == WorkflowKind.QA
    assert item.model_dump(mode="json", by_alias=True)["createdAt"]

    data = item.model_dump(mode="json", by_alias=True)
    assert data["workflow"] == 1
    assert "updatedAt" in data


def test_conversation_item_allows_null_workflow() -> None:
    item = _item(workflow=None)
    assert item.workflow is None
    assert item.model_dump(mode="json", by_alias=True)["workflow"] is None


def test_message_user_row_has_no_steps_or_artifacts() -> None:
    message = _message(role="user", steps=None, artifacts=None)

    assert message.role == "user"
    assert message.steps is None
    assert message.artifacts is None


def test_message_assistant_row_with_steps_and_artifacts() -> None:
    message = _message(
        steps=[
            Step(
                id="s1",
                tool="query_prerequisite_chain",
                label="查询前置知识链",
                status=StepStatus.DONE,
                summary="返回 3 层前置",
            )
        ],
        artifacts=MessageArtifacts(route=_route()),
    )

    assert message.steps is not None
    assert message.steps[0].status == StepStatus.DONE
    assert message.artifacts is not None
    assert message.artifacts.route is not None
    assert message.artifacts.route.target.tag_id == "10"

    data = message.model_dump(mode="json", by_alias=True)
    assert data["messageId"] == "m-1"
    assert data["artifacts"]["route"]["target"]["tagId"] == "10"
    assert data["steps"][0]["status"] == "done"


def test_message_artifacts_parse_route_from_wire() -> None:
    artifacts = MessageArtifacts.model_validate(
        {
            "route": {
                "target": {"tagId": "10", "name": "Redis"},
                "known": ["Java"],
                "truncated": False,
                "stages": [],
            }
        }
    )

    assert artifacts.route is not None
    assert artifacts.route.target.tag_id == "10"


def test_message_artifacts_allow_no_route() -> None:
    assert MessageArtifacts().route is None


def test_message_status_and_role_validation() -> None:
    with pytest.raises(ValidationError):
        _message(role="system")

    with pytest.raises(ValidationError):
        _message(status="streaming")


def test_message_allows_null_workflow() -> None:
    assert _message(workflow=None).workflow is None


def test_conversation_detail_with_messages() -> None:
    detail = ConversationDetailVO.model_validate(
        {
            "id": CONVERSATION_ID,
            "title": "Redis 持久化",
            "workflow": 1,
            "createdAt": NOW,
            "updatedAt": NOW,
            "messages": [
                {
                    "id": MESSAGE_ROW_ID,
                    "messageId": "m-1",
                    "role": "assistant",
                    "content": "回答",
                    "workflow": 1,
                    "status": "done",
                    "createdAt": NOW,
                }
            ],
        }
    )

    assert len(detail.messages) == 1
    assert detail.messages[0].message_id == "m-1"


def test_conversation_detail_allows_empty_messages() -> None:
    detail = ConversationDetailVO.model_validate(
        {
            "id": CONVERSATION_ID,
            "title": "空会话",
            "workflow": None,
            "createdAt": NOW,
            "updatedAt": NOW,
            "messages": [],
        }
    )

    assert detail.messages == []


def test_conversation_page_shape() -> None:
    page = ConversationPageVO.model_validate(
        {
            "total": 1,
            "pages": 1,
            "pageSize": 10,
            "page": 1,
            "items": [
                {
                    "id": CONVERSATION_ID,
                    "title": "Redis 持久化",
                    "workflow": 1,
                    "createdAt": NOW,
                    "updatedAt": NOW,
                }
            ],
        }
    )

    data = page.model_dump(mode="json", by_alias=True)
    assert data["pageSize"] == 10
    assert data["total"] == 1
    assert len(data["items"]) == 1


def test_naive_datetime_is_rejected() -> None:
    with pytest.raises(ValidationError):
        _item(createdAt=datetime(2026, 11, 14, 12, 0))


def test_extra_fields_are_forbidden() -> None:
    with pytest.raises(ValidationError):
        _item(extra=1)
