import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.response import MESSAGES, BusinessError, ErrorCode, error

log = logging.getLogger(__name__)

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


def _json(body: dict) -> JSONResponse:
    """服务侧统一 HTTP 200，成败看 body.code。"""
    return JSONResponse(status_code=200, content=body)


def _validation_detail(exc: RequestValidationError) -> str:
    parts: list[str] = []
    for item in exc.errors():
        err_type = item.get("type", "")
        if err_type == "json_invalid":
            return "请求体格式不正确"
        loc = ".".join(str(p) for p in item.get("loc", ()) if p not in ("body", "query", "path"))
        if err_type == "missing":
            parts.append(f"缺少参数: {loc}")
            continue
        msg = item.get("msg", MESSAGES[ErrorCode.BAD_REQUEST]).removeprefix("Value error, ")
        parts.append(f"{loc}: {msg}" if loc else msg)
    return "; ".join(parts)


async def _validation_error(_: Request, exc: RequestValidationError) -> JSONResponse:
    detail = _validation_detail(exc)
    log.warning("参数校验失败: %s", detail)
    return _json(error(ErrorCode.BAD_REQUEST, detail))


async def _business_error(_: Request, exc: BusinessError) -> JSONResponse:
    log.warning("业务异常, code=%s, message=%s", int(exc.code), exc.message)
    return _json(error(exc.code, exc.message))


async def _http_error(request: Request, exc: StarletteHTTPException) -> JSONResponse:
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
