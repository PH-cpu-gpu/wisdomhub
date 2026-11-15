"""能力层工具异常：统一 kind、用户文案与步骤映射。"""

from enum import StrEnum

from app.contracts.sse import Step, StepStatus
from app.infra.http.internal_client import (
    InternalAuthError,
    InternalClientError,
    InternalNotFound,
    InternalProtocolError,
)

SUMMARY_MAX = 200


class ToolErrorKind(StrEnum):
    INVALID_INPUT = "invalid_input"
    UNAUTHORIZED = "unauthorized"
    NOT_FOUND = "not_found"
    REJECTED = "rejected"
    RATE_LIMITED = "rate_limited"
    UNAVAILABLE = "unavailable"
    INTERNAL = "internal"


MESSAGES: dict[ToolErrorKind, str] = {
    ToolErrorKind.INVALID_INPUT: "请求参数不合法",
    ToolErrorKind.UNAUTHORIZED: "未登录或登录已过期",
    ToolErrorKind.NOT_FOUND: "没有找到相关数据",
    ToolErrorKind.REJECTED: "操作未通过审核",
    ToolErrorKind.RATE_LIMITED: "请求过于频繁，请稍后重试",
    ToolErrorKind.UNAVAILABLE: "服务暂不可用，请稍后重试",
    ToolErrorKind.INTERNAL: "服务返回异常，请稍后重试",
}


class ToolException(Exception):
    """工具失败；message 直接给用户或模型看。"""

    def __init__(self, kind: ToolErrorKind, message: str | None = None) -> None:
        self.kind = kind
        self.message = message or MESSAGES[kind]
        super().__init__(self.message)

    @property
    def summary(self) -> str:
        """单行、截断到 SUMMARY_MAX，给 steps.summary 用。"""
        text = " ".join(self.message.split())
        if len(text) <= SUMMARY_MAX:
            return text
        return text[: SUMMARY_MAX - 1] + "…"

    def to_step(self, *, step_id: str, tool: str, label: str) -> Step:
        return Step(
            id=step_id,
            tool=tool,
            label=label,
            status=StepStatus.ERROR,
            summary=self.summary,
        )


def from_internal_error(
    exc: InternalClientError,
    *,
    not_found: str | None = None,
) -> ToolException:
    if isinstance(exc, InternalNotFound):
        return ToolException(ToolErrorKind.NOT_FOUND, not_found)
    if isinstance(exc, InternalAuthError):
        return ToolException(ToolErrorKind.UNAUTHORIZED)
    if isinstance(exc, InternalProtocolError):
        return ToolException(ToolErrorKind.INTERNAL)
    return ToolException(ToolErrorKind.UNAVAILABLE)
