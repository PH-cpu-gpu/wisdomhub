"""统一错误码、响应体与异常处理。"""

import logging
from enum import IntEnum
from typing import Any

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

log = logging.getLogger(__name__)


class ErrorCode(IntEnum):
    """与 Java CommonErrorCode 一一对应。"""

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


def error(code: ErrorCode, message: str | None = None, data: Any = None) -> dict[str, Any]:
    return {
        "code": int(code),
        "message": message or MESSAGES[code],
        "data": data,
    }


class BusinessError(Exception):
    """业务失败：HTTP 恒 200，成败由 body.code 表达。"""

    def __init__(self, code: ErrorCode, message: str | None = None) -> None:
        self.code = code
        self.message = message or MESSAGES[code]
        super().__init__(self.message)


_DEGRADED: dict[type[BaseException], tuple[ErrorCode, str]] = {}
_DEGRADED_BY_NAME: dict[str, tuple[ErrorCode, str]] = {
    "InternalClientError": (ErrorCode.SERVICE_UNAVAILABLE, "检索服务暂不可用"),
    "OpenAIError": (ErrorCode.SERVICE_UNAVAILABLE, "AI 服务暂不可用"),
    "GraphRecursionError": (ErrorCode.SERVICE_UNAVAILABLE, "AI 服务响应异常"),
}


def register_degraded_error(exc_type: type[BaseException], code: ErrorCode, message: str) -> None:
    _DEGRADED[exc_type] = (code, message)


def friendly_error(exc: BaseException) -> tuple[ErrorCode, str] | None:
    for cls in type(exc).__mro__:
        if cls in _DEGRADED:
            return _DEGRADED[cls]
        by_name = _DEGRADED_BY_NAME.get(cls.__name__)
        if by_name is not None:
            return by_name
    return None


_HTTP_CODES = {
    400: ErrorCode.BAD_REQUEST,
    401: ErrorCode.UNAUTHORIZED,
    403: ErrorCode.FORBIDDEN,
    404: ErrorCode.NOT_FOUND,
    405: ErrorCode.METHOD_NOT_ALLOWED,
    409: ErrorCode.CONFLICT,
    429: ErrorCode.TOO_MANY_REQUESTS,
    500: ErrorCode.INTERNAL_ERROR,
    503: ErrorCode.SERVICE_UNAVAILABLE,
}


def _json(body: dict[str, Any]) -> JSONResponse:
    """服务侧统一 HTTP 200，成败看 body.code。"""
    return JSONResponse(status_code=200, content=body)


def _validation_detail(exc: RequestValidationError) -> str:
    parts: list[str] = []
    for item in exc.errors():
        if item.get("type") == "json_invalid":
            return "请求体格式不正确"
        loc = ".".join(str(p) for p in item.get("loc", ()) if p not in ("body", "query", "path"))
        if item.get("type") == "missing":
            parts.append(f"缺少参数: {loc}")
            continue
        msg = str(item.get("msg", MESSAGES[ErrorCode.BAD_REQUEST])).removeprefix("Value error, ")
        parts.append(f"{loc}: {msg}" if loc else msg)
    return "; ".join(parts) or MESSAGES[ErrorCode.BAD_REQUEST]


async def _validation_error(_: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, RequestValidationError)
    detail = _validation_detail(exc)
    log.warning("参数校验失败: %s", detail)
    return _json(error(ErrorCode.BAD_REQUEST, detail))


async def _business_error(_: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, BusinessError)
    log.warning("业务异常, code=%s, message=%s", int(exc.code), exc.message)
    return _json(error(exc.code, exc.message))


async def _http_error(request: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, StarletteHTTPException)
    code = _HTTP_CODES.get(exc.status_code)
    if code is None:
        return _json(error(ErrorCode.INTERNAL_ERROR))
    if code is ErrorCode.NOT_FOUND:
        return _json(error(code, "请求的地址不存在"))
    if code is ErrorCode.METHOD_NOT_ALLOWED:
        return _json(error(code, f"该地址不支持 {request.method} 方法"))
    return _json(error(code))


async def _unexpected_error(_: Request, exc: Exception) -> JSONResponse:
    log.exception("系统异常", exc_info=exc)
    return _json(error(ErrorCode.INTERNAL_ERROR))


def register_exception_handlers(app: FastAPI) -> None:
    app.add_exception_handler(RequestValidationError, _validation_error)
    app.add_exception_handler(BusinessError, _business_error)
    app.add_exception_handler(StarletteHTTPException, _http_error)
    app.add_exception_handler(Exception, _unexpected_error)
