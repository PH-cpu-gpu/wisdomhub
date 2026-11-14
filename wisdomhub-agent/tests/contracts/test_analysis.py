from datetime import UTC, datetime

import pytest
from pydantic import ValidationError

from app.contracts.analysis import AnalysisVO

NOW = datetime(2026, 11, 14, 12, 0, tzinfo=UTC)


def _analysis(**overrides: object) -> AnalysisVO:
    payload: dict[str, object] = {
        "articleId": "2108913491297783810",
        "status": "pending",
    }
    payload.update(overrides)
    return AnalysisVO.model_validate(payload)


def test_pending_shape() -> None:
    analysis = _analysis()

    assert analysis.article_id == "2108913491297783810"
    assert analysis.status == "pending"
    assert analysis.topic is None
    assert analysis.entities is None
    assert analysis.knowledge_points is None
    assert analysis.source_updated_at is None
    assert analysis.analyzed_at is None
    assert analysis.message is None


def test_done_shape() -> None:
    analysis = _analysis(
        status="done",
        topic="Spring Bean 生命周期",
        entities=["Spring Bean", "BeanPostProcessor"],
        knowledgePoints=["实例化", "初始化"],
        sourceUpdatedAt=NOW,
        analyzedAt=NOW,
    )

    assert analysis.status == "done"
    assert analysis.topic == "Spring Bean 生命周期"
    assert analysis.entities == ["Spring Bean", "BeanPostProcessor"]
    assert analysis.knowledge_points == ["实例化", "初始化"]
    assert analysis.source_updated_at == NOW
    assert analysis.analyzed_at == NOW
    assert analysis.message is None


def test_error_shape() -> None:
    analysis = _analysis(status="error", message="文章分析失败，请稍后重试。")

    assert analysis.status == "error"
    assert analysis.topic is None
    assert analysis.entities is None
    assert analysis.knowledge_points is None
    assert analysis.message == "文章分析失败，请稍后重试。"


def test_camel_case_output() -> None:
    analysis = _analysis(
        status="done",
        topic="topic",
        entities=[],
        knowledgePoints=[],
        sourceUpdatedAt=NOW,
        analyzedAt=NOW,
    )

    data = analysis.model_dump(mode="json", by_alias=True)
    assert data["articleId"] == "2108913491297783810"
    assert data["knowledgePoints"] == []
    assert "sourceUpdatedAt" in data
    assert "analyzedAt" in data


def test_pending_can_have_source_updated_at() -> None:
    analysis = _analysis(status="pending", sourceUpdatedAt=NOW)
    assert analysis.source_updated_at == NOW


def test_empty_lists_are_allowed() -> None:
    analysis = _analysis(
        status="done",
        topic="topic",
        entities=[],
        knowledgePoints=[],
        analyzedAt=NOW,
    )
    assert analysis.entities == []
    assert analysis.knowledge_points == []


def test_done_requires_result_fields() -> None:
    with pytest.raises(ValidationError):
        _analysis(status="done", topic="topic")


def test_done_rejects_message() -> None:
    with pytest.raises(ValidationError):
        _analysis(
            status="done",
            topic="topic",
            entities=[],
            knowledgePoints=[],
            analyzedAt=NOW,
            message="不应出现",
        )


def test_error_requires_message_and_rejects_result_fields() -> None:
    with pytest.raises(ValidationError):
        _analysis(status="error")

    with pytest.raises(ValidationError):
        _analysis(status="error", message="失败", topic="topic")


def test_pending_rejects_result_and_message() -> None:
    with pytest.raises(ValidationError):
        _analysis(status="pending", topic="topic")

    with pytest.raises(ValidationError):
        _analysis(status="pending", message="失败")


def test_invalid_status_is_rejected() -> None:
    with pytest.raises(ValidationError):
        _analysis(status="in_progress")


def test_naive_datetime_is_rejected() -> None:
    with pytest.raises(ValidationError):
        _analysis(sourceUpdatedAt=datetime(2026, 11, 14, 12, 0))


def test_extra_fields_are_forbidden() -> None:
    with pytest.raises(ValidationError):
        _analysis(extra=1)
