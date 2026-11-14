"""结构化日志与 trace_id。"""

import io
import json
import logging

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core.logging import (
    SERVICE_NAME,
    JsonFormatter,
    TextFormatter,
    TraceIdMiddleware,
    get_trace_id,
    normalize_trace_id,
    trace_context,
)


def _capture(formatter: logging.Formatter, message: str = "hello", exc_info: bool = False) -> str:
    stream = io.StringIO()
    handler = logging.StreamHandler(stream)
    handler.setFormatter(formatter)
    logger = logging.getLogger("tests.logging")
    logger.handlers = [handler]
    logger.setLevel(logging.INFO)
    logger.propagate = False
    logger.info(message, exc_info=exc_info)
    return stream.getvalue()


def _client() -> TestClient:
    app = FastAPI()
    app.add_middleware(TraceIdMiddleware)

    @app.get("/trace")
    def trace() -> dict[str, str | None]:
        return {"trace_id": get_trace_id()}

    return TestClient(app)


def test_json_formatter_includes_trace_id() -> None:
    with trace_context("trace-123"):
        payload = json.loads(_capture(JsonFormatter()))

    assert payload["trace_id"] == "trace-123"
    assert payload["service"] == SERVICE_NAME
    assert payload["logger"] == "tests.logging"
    assert payload["message"] == "hello"
    assert payload["level"] == "INFO"


def test_json_formatter_keeps_exception_in_log() -> None:
    try:
        raise RuntimeError("boom")
    except RuntimeError:
        payload = json.loads(_capture(JsonFormatter(), exc_info=True))

    assert "RuntimeError: boom" in payload["exception"]


def test_text_formatter_includes_trace_id() -> None:
    with trace_context("t1"):
        line = _capture(TextFormatter())

    assert "[t1]" in line
    assert "hello" in line


def test_trace_context_resets() -> None:
    assert get_trace_id() is None

    with trace_context("abc"):
        assert get_trace_id() == "abc"

    assert get_trace_id() is None


def test_normalize_trace_id() -> None:
    assert normalize_trace_id("abc-123.X") == "abc-123.X"
    assert normalize_trace_id("bad id") is None
    assert normalize_trace_id("x" * 65) is None
    assert normalize_trace_id(None) is None


def test_middleware_generates_and_echoes_trace() -> None:
    resp = _client().get("/trace")

    trace_id = resp.json()["trace_id"]
    assert trace_id is not None
    assert len(trace_id) == 32
    assert resp.headers["X-Trace-Id"] == trace_id


def test_middleware_uses_request_id_header() -> None:
    resp = _client().get("/trace", headers={"X-Request-Id": "req-1"})

    assert resp.json()["trace_id"] == "req-1"
    assert resp.headers["X-Trace-Id"] == "req-1"


def test_middleware_replaces_invalid_trace_id() -> None:
    resp = _client().get("/trace", headers={"X-Trace-Id": "bad id!"})

    trace_id = resp.json()["trace_id"]
    assert trace_id != "bad id!"
    assert trace_id is not None
    assert len(trace_id) == 32
