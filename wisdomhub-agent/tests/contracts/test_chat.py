from uuid import UUID

import pytest
from pydantic import ValidationError

from app.contracts.chat import (
    LearningPathChatRequest,
    QaChatRequest,
    TextQaChatRequest,
)


def test_request_models_have_no_workflow_field() -> None:
    for model in (QaChatRequest, LearningPathChatRequest, TextQaChatRequest):
        assert "workflow" not in model.model_fields


def test_qa_request() -> None:
    request = QaChatRequest.model_validate({"question": "Redis 怎么学？"})

    assert request.question == "Redis 怎么学？"
    assert request.conversation_id is None
    assert request.message_id is None


def test_learning_path_request() -> None:
    request = LearningPathChatRequest.model_validate(
        {
            "technology": "Spring Boot",
            "knownTechnologies": ["Java"],
            "question": "我要学 Spring Boot",
        }
    )

    assert request.technology == "Spring Boot"
    assert request.known_technologies == ["Java"]


def test_text_qa_request() -> None:
    request = TextQaChatRequest.model_validate({"text": " Redis 的 RDB ", "question": "解释一下"})

    assert request.text == "Redis 的 RDB"


def test_workflow_field_is_rejected() -> None:
    for model in (QaChatRequest, LearningPathChatRequest, TextQaChatRequest):
        payload: dict[str, object] = {"question": "q", "workflow": 1}
        if model is LearningPathChatRequest:
            payload["technology"] = "Redis"
        if model is TextQaChatRequest:
            payload["text"] = "一段文本"

        with pytest.raises(ValidationError):
            model.model_validate(payload)


def test_branch_fields_are_exclusive() -> None:
    with pytest.raises(ValidationError):
        QaChatRequest.model_validate({"question": "q", "text": "x"})

    with pytest.raises(ValidationError):
        QaChatRequest.model_validate({"question": "q", "technology": "Redis"})

    with pytest.raises(ValidationError):
        LearningPathChatRequest.model_validate(
            {"question": "q", "technology": "Redis", "text": "x"}
        )

    with pytest.raises(ValidationError):
        TextQaChatRequest.model_validate({"question": "q", "text": "x", "technology": "Redis"})


def test_learning_path_requires_technology() -> None:
    with pytest.raises(ValidationError):
        LearningPathChatRequest.model_validate({"question": "q"})


def test_text_qa_requires_text() -> None:
    with pytest.raises(ValidationError):
        TextQaChatRequest.model_validate({"question": "q"})


def test_question_is_trimmed() -> None:
    assert QaChatRequest.model_validate({"question": "  hi  "}).question == "hi"


@pytest.mark.parametrize("question", ["", "   ", "x" * 4001])
def test_question_bounds(question: str) -> None:
    with pytest.raises(ValidationError):
        QaChatRequest.model_validate({"question": question})


def test_conversation_id_validation() -> None:
    request = QaChatRequest.model_validate(
        {
            "conversationId": "550e8400-e29b-41d4-a716-446655440000",
            "question": "q",
        }
    )
    assert request.conversation_id == UUID("550e8400-e29b-41d4-a716-446655440000")

    with pytest.raises(ValidationError):
        QaChatRequest.model_validate({"conversationId": "not-a-uuid", "question": "q"})


def test_blank_optional_fields_become_none() -> None:
    request = QaChatRequest.model_validate(
        {
            "conversationId": "   ",
            "messageId": "   ",
            "question": "q",
        }
    )

    assert request.conversation_id is None
    assert request.message_id is None


def test_message_id_bounds() -> None:
    with pytest.raises(ValidationError):
        QaChatRequest.model_validate({"messageId": "x" * 65, "question": "q"})


def test_technology_bounds() -> None:
    with pytest.raises(ValidationError):
        LearningPathChatRequest.model_validate({"technology": "x" * 51, "question": "q"})


def test_known_technologies_are_cleaned() -> None:
    request = LearningPathChatRequest.model_validate(
        {
            "technology": "Redis",
            "knownTechnologies": [" Java ", "", "  ", "Redis"],
            "question": "q",
        }
    )

    assert request.known_technologies == ["Java", "Redis"]


def test_known_technologies_bounds() -> None:
    with pytest.raises(ValidationError):
        LearningPathChatRequest.model_validate(
            {
                "technology": "Redis",
                "knownTechnologies": ["x"] * 21,
                "question": "q",
            }
        )

    with pytest.raises(ValidationError):
        LearningPathChatRequest.model_validate(
            {
                "technology": "Redis",
                "knownTechnologies": ["x" * 51],
                "question": "q",
            }
        )


def test_text_bounds() -> None:
    with pytest.raises(ValidationError):
        TextQaChatRequest.model_validate({"text": "x" * 2001, "question": "q"})


def test_camel_case_fields() -> None:
    request = LearningPathChatRequest.model_validate(
        {
            "conversationId": "550e8400-e29b-41d4-a716-446655440000",
            "messageId": "m1",
            "technology": "Spring Boot",
            "knownTechnologies": ["Java"],
            "question": "我要学 Spring Boot",
        }
    )

    assert request.conversation_id == UUID("550e8400-e29b-41d4-a716-446655440000")
    assert request.message_id == "m1"
    assert request.technology == "Spring Boot"
    assert request.known_technologies == ["Java"]


def test_extra_fields_are_forbidden() -> None:
    with pytest.raises(ValidationError):
        QaChatRequest.model_validate({"question": "q", "pageContext": {"articleId": "123"}})
