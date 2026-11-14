"""结构化日志与 trace_id。"""

import json
import logging
import re
from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from datetime import UTC, datetime
from typing import Any
from uuid import uuid4

from starlette.datastructures import Headers, MutableHeaders
from starlette.types import ASGIApp, Message, Receive, Scope, Send

SERVICE_NAME = "wisdomhub-agent"
TRACE_HEADER = "X-Trace-Id"
REQUEST_ID_HEADER = "X-Request-Id"
_TRACE_ID_RE = re.compile(r"^[A-Za-z0-9._-]{1,64}$")

_trace_id: ContextVar[str | None] = ContextVar("trace_id", default=None)
_handler: logging.Handler | None = None


def get_trace_id() -> str | None:
    return _trace_id.get()


def new_trace_id() -> str:
    return uuid4().hex


def normalize_trace_id(value: str | None) -> str | None:
    if not value:
        return None
    value = value.strip()
    return value if _TRACE_ID_RE.fullmatch(value) else None


@contextmanager
def trace_context(trace_id: str | None = None) -> Iterator[str]:
    value = trace_id or new_trace_id()
    token = _trace_id.set(value)
    try:
        yield value
    finally:
        _trace_id.reset(token)


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": datetime.fromtimestamp(record.created, UTC).isoformat(
                timespec="milliseconds"
            ),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "service": SERVICE_NAME,
            "trace_id": get_trace_id(),
        }
        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)
        if record.stack_info:
            payload["stack"] = self.formatStack(record.stack_info)
        return json.dumps(payload, ensure_ascii=False, default=str)


class TextFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        trace_id = get_trace_id() or "-"
        line = (
            f"{self.formatTime(record, '%Y-%m-%d %H:%M:%S')} "
            f"{record.levelname:<8} [{trace_id}] {record.name}: {record.getMessage()}"
        )
        if record.exc_info:
            line = f"{line}\n{self.formatException(record.exc_info)}"
        if record.stack_info:
            line = f"{line}\n{self.formatStack(record.stack_info)}"
        return line


def configure_logging(level: str = "INFO", json_logs: bool = True) -> None:
    global _handler
    root = logging.getLogger()
    if _handler is not None:
        root.removeHandler(_handler)
        _handler.close()
    _handler = logging.StreamHandler()
    _handler.setFormatter(JsonFormatter() if json_logs else TextFormatter())
    root.addHandler(_handler)
    root.setLevel(level.upper())


class TraceIdMiddleware:
    """纯 ASGI 中间件，不缓冲 SSE 响应。"""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        headers = Headers(scope=scope)
        trace_id = (
            normalize_trace_id(headers.get(TRACE_HEADER))
            or normalize_trace_id(headers.get(REQUEST_ID_HEADER))
            or new_trace_id()
        )

        async def send_with_trace(message: Message) -> None:
            if message["type"] == "http.response.start":
                MutableHeaders(scope=message)[TRACE_HEADER] = trace_id
            await send(message)

        token = _trace_id.set(trace_id)
        try:
            await self.app(scope, receive, send_with_trace)
        finally:
            _trace_id.reset(token)
