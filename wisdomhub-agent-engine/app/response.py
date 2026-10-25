from enum import IntEnum
from typing import Any


class ErrorCode(IntEnum):
    """与 Java common-core 的 CommonErrorCode 一一对应。"""

    SUCCESS = 200
    BAD_REQUEST = 400
    UNAUTHORIZED = 401
    FORBIDDEN = 403
    NOT_FOUND = 404
    METHOD_NOT_ALLOWED = 405
    CONFLICT = 409
    TOO_MANY_REQUESTS = 429
    INTERNAL_ERROR = 500
    SERVICE_UNAVAILABLE = 503


MESSAGES: dict[ErrorCode, str] = {
    ErrorCode.SUCCESS: "ok",
    ErrorCode.BAD_REQUEST: "请求参数不合法",
    ErrorCode.UNAUTHORIZED: "未登录或登录已过期",
    ErrorCode.FORBIDDEN: "无权限访问",
    ErrorCode.NOT_FOUND: "资源不存在",
    ErrorCode.METHOD_NOT_ALLOWED: "请求方法不支持",
    ErrorCode.CONFLICT: "操作冲突",
    ErrorCode.TOO_MANY_REQUESTS: "请求过于频繁，请稍后重试",
    ErrorCode.INTERNAL_ERROR: "服务内部异常",
    ErrorCode.SERVICE_UNAVAILABLE: "依赖服务暂不可用",
}


def success(data: Any = None) -> dict[str, Any]:
    return {
        "code": int(ErrorCode.SUCCESS),
        "message": MESSAGES[ErrorCode.SUCCESS],
        "data": data,
    }


def error(code: ErrorCode, message: str | None = None) -> dict[str, Any]:
    return {
        "code": int(code),
        "message": message or MESSAGES[code],
        "data": None
    }


class BusinessError(Exception):
    """业务失败：HTTP 恒 200，成败由 body.code 表达。"""

    def __init__(self, code: ErrorCode, message: str | None = None) -> None:
        self.code = code
        self.message = message or MESSAGES[code]
        super().__init__(self.message)
