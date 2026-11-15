"""工具错误：kind、默认文案与步骤映射。"""

import pytest

from app.capabilities.errors import (
    MESSAGES,
    SUMMARY_MAX,
    ToolErrorKind,
    ToolException,
    from_internal_error,
)
from app.contracts.sse import StepStatus
from app.infra.http.internal_client import (
    InternalAuthError,
    InternalClientError,
    InternalNotFound,
    InternalProtocolError,
    InternalServerError,
    InternalUnavailable,
)

PATH = "/internal/test"


def test_default_messages() -> None:
    for kind, message in MESSAGES.items():
        assert ToolException(kind).message == message


def test_custom_message_overrides_default() -> None:
    exc = ToolException(ToolErrorKind.NOT_FOUND, "没有找到技术「Redis」")

    assert exc.message == "没有找到技术「Redis」"
    assert str(exc) == "没有找到技术「Redis」"


def test_to_step_maps_to_error_status() -> None:
    exc = ToolException(ToolErrorKind.NOT_FOUND, "知识图谱中没有找到技术「Redis」")

    step = exc.to_step(step_id="s1", tool="query_prerequisite_chain", label="查询前置知识链")

    assert step.id == "s1"
    assert step.tool == "query_prerequisite_chain"
    assert step.label == "查询前置知识链"
    assert step.status is StepStatus.ERROR
    assert step.summary == "知识图谱中没有找到技术「Redis」"


def test_summary_collapses_whitespace_and_truncates() -> None:
    assert ToolException(ToolErrorKind.INTERNAL, "a\nb\tc").summary == "a b c"

    exc = ToolException(ToolErrorKind.INTERNAL, "x" * (SUMMARY_MAX + 10))

    assert len(exc.summary) == SUMMARY_MAX
    assert exc.summary.endswith("…")


@pytest.mark.parametrize(
    ("error", "kind"),
    [
        (InternalNotFound("x", service="s", path=PATH), ToolErrorKind.NOT_FOUND),
        (InternalAuthError("x", service="s", path=PATH), ToolErrorKind.UNAUTHORIZED),
        (InternalProtocolError("x", service="s", path=PATH), ToolErrorKind.INTERNAL),
        (InternalUnavailable("x", service="s", path=PATH), ToolErrorKind.UNAVAILABLE),
        (InternalServerError("x", service="s", path=PATH), ToolErrorKind.UNAVAILABLE),
    ],
)
def test_from_internal_error_maps_kind(error: InternalClientError, kind: ToolErrorKind) -> None:
    assert from_internal_error(error).kind is kind


def test_not_found_message_can_be_overridden() -> None:
    error = InternalNotFound("x", service="s", path=PATH)

    exc = from_internal_error(error, not_found="知识图谱中没有找到技术「Redis」")

    assert exc.kind is ToolErrorKind.NOT_FOUND
    assert exc.message == "知识图谱中没有找到技术「Redis」"


def test_mapping_does_not_leak_internal_details() -> None:
    error = InternalUnavailable("down: /internal/x 500", service="s", path=PATH)

    message = str(from_internal_error(error))

    assert message == "服务暂不可用，请稍后重试"
    assert "/internal" not in message
    assert "500" not in message
