"""统一错误码、响应体与异常处理器。"""

import logging

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.core.errors import (
    BusinessError,
    ErrorCode,
    error,
    friendly_error,
    register_degraded_error,
    register_exception_handlers,
    success,
)


def _client() -> TestClient:
    app = FastAPI()
    register_exception_handlers(app)

    @app.get("/business")
    def business() -> None:
        raise BusinessError(ErrorCode.CONFLICT, "名称已被占用")

    @app.get("/boom")
    def boom() -> None:
        raise RuntimeError("内部细节不应返回")

    @app.get("/validate")
    def validate(q: int) -> dict[str, int]:
        return {"q": q}

    return TestClient(app, raise_server_exceptions=False)


def test_success_and_error_shape() -> None:
    assert success({"id": 1}) == {"code": 200, "message": "ok", "data": {"id": 1}}
    assert error(ErrorCode.NOT_FOUND) == {
        "code": 404,
        "message": "资源不存在",
        "data": None,
    }


def test_business_error_response() -> None:
    resp = _client().get("/business")

    assert resp.status_code == 200
    assert resp.json() == {"code": 409, "message": "名称已被占用", "data": None}


def test_validation_error_response() -> None:
    resp = _client().get("/validate")

    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 400
    assert "缺少参数: q" in body["message"]


def test_unknown_path_response() -> None:
    body = _client().get("/missing").json()

    assert body["code"] == 404
    assert body["message"] == "请求的地址不存在"


def test_method_not_allowed_response() -> None:
    resp = _client().post("/business")

    assert resp.status_code == 200
    body = resp.json()
    assert body["code"] == 405
    assert "该地址不支持 POST 方法" in body["message"]


def test_unexpected_error_hides_detail(caplog: pytest.LogCaptureFixture) -> None:
    with caplog.at_level(logging.ERROR, logger="app.core.errors"):
        resp = _client().get("/boom")

    assert resp.status_code == 200
    assert resp.json() == {"code": 500, "message": "服务内部异常", "data": None}
    assert "内部细节不应返回" not in resp.text
    assert "Traceback" not in resp.text
    assert "内部细节不应返回" in caplog.text


class InternalClientError(Exception):
    pass


class OpenAIError(Exception):
    pass


class GraphRecursionError(Exception):
    pass


def test_friendly_error_by_name() -> None:
    assert friendly_error(InternalClientError()) == (
        ErrorCode.SERVICE_UNAVAILABLE,
        "检索服务暂不可用",
    )
    assert friendly_error(OpenAIError()) == (
        ErrorCode.SERVICE_UNAVAILABLE,
        "AI 服务暂不可用",
    )
    assert friendly_error(GraphRecursionError()) == (
        ErrorCode.SERVICE_UNAVAILABLE,
        "AI 服务响应异常",
    )
    assert friendly_error(ValueError()) is None


def test_register_degraded_error() -> None:
    class CustomError(Exception):
        pass

    register_degraded_error(CustomError, ErrorCode.TOO_MANY_REQUESTS, "稍后再试")

    assert friendly_error(CustomError()) == (ErrorCode.TOO_MANY_REQUESTS, "稍后再试")
