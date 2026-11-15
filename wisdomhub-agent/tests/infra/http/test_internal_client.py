import json
from collections.abc import Callable

import httpx
import pytest

from app.core.config import LimitsSettings
from app.infra.http.internal_client import (
    InternalAuthError,
    InternalBusinessError,
    InternalClient,
    InternalClientError,
    InternalHttpError,
    InternalNotFound,
    InternalProtocolError,
    InternalServerError,
    InternalUnavailable,
    ServiceInstance,
)
from app.infra.security.internal_sign import (
    HEADER_NONCE,
    HEADER_SERVICE_NAME,
    HEADER_SIGNATURE,
    HEADER_TIMESTAMP,
    InternalSigner,
    canonical_string,
    sign,
)

SECRET = "0123456789abcdef0123456789abcdef"
INSTANCE = ServiceInstance(host="127.0.0.1", port=8080)
Handler = Callable[[httpx.Request], httpx.Response]


class FakeResolver:
    def __init__(self, instances: list[ServiceInstance] | None = None) -> None:
        self.instances = [INSTANCE] if instances is None else instances
        self.calls = 0

    async def discover(self, service: str) -> list[ServiceInstance]:
        self.calls += 1
        return self.instances


class Harness:
    def __init__(
        self,
        handler: Handler,
        *,
        resolver: FakeResolver | None = None,
        retry_times: int = 1,
    ) -> None:
        self.resolver = resolver or FakeResolver()
        self.client = InternalClient(
            LimitsSettings(internal_retry_times=retry_times),
            self.resolver,
            InternalSigner(SECRET),
            httpx.AsyncClient(transport=httpx.MockTransport(handler), trust_env=False),
        )

    async def __aenter__(self) -> InternalClient:
        return self.client

    async def __aexit__(self, *_: object) -> None:
        await self.client.aclose()


def _json(code: int, message: str = "ok", data: object = None) -> httpx.Response:
    return httpx.Response(200, json={"code": code, "message": message, "data": data})


def _assert_signature(request: httpx.Request) -> None:
    canonical = canonical_string(
        request.method,
        request.url.raw_path.decode("ascii"),
        request.headers[HEADER_TIMESTAMP],
        request.headers[HEADER_NONCE],
        request.content,
    )
    assert request.headers[HEADER_SIGNATURE] == sign(SECRET, canonical)


async def test_get_success_and_signature() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert str(request.url) == "http://127.0.0.1:8080/internal/content/article/1?x=1"
        assert request.headers[HEADER_SERVICE_NAME] == "wisdomhub-agent"
        _assert_signature(request)
        return _json(200, data={"id": "1"})

    async with Harness(handler) as client:
        assert await client.get(
            "content-service",
            "/internal/content/article/1",
            params={"x": "1"},
        ) == {"id": "1"}


async def test_post_serializes_body_once_and_signs_it() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["Content-Type"] == "application/json"
        assert json.loads(request.content) == {"a": 1, "中文": "值"}
        _assert_signature(request)
        return _json(200, data={"ok": True})

    async with Harness(handler) as client:
        assert await client.post(
            "content-service",
            "/internal/x",
            json_body={"a": 1, "中文": "值"},
        ) == {"ok": True}


async def test_not_found() -> None:
    response = httpx.Response(404, json={"code": 404, "message": "文章不存在", "data": None})
    async with Harness(lambda _: response) as client:
        with pytest.raises(InternalNotFound) as exc:
            await client.get("content-service", "/internal/article/1")

    assert exc.value.status == 404
    assert exc.value.retryable is False


async def test_auth_error_is_not_retried() -> None:
    resolver = FakeResolver()

    async with Harness(lambda _: _json(401, "签名失败"), resolver=resolver) as client:
        with pytest.raises(InternalAuthError):
            await client.get("content-service", "/internal/x")

    assert resolver.calls == 1


async def test_server_error_is_retried_then_succeeds() -> None:
    requests: list[httpx.Request] = []

    def handler(request: httpx.Request) -> httpx.Response:
        requests.append(request)
        if len(requests) == 1:
            return httpx.Response(500, json={"code": 500, "message": "boom", "data": None})
        return _json(200, data={"ok": True})

    async with Harness(handler) as client:
        assert await client.get("content-service", "/internal/x") == {"ok": True}

    assert len(requests) == 2
    assert requests[0].headers[HEADER_NONCE] != requests[1].headers[HEADER_NONCE]


async def test_server_error_exhausts_retries() -> None:
    async with Harness(lambda _: httpx.Response(500, text="boom")) as client:
        with pytest.raises(InternalServerError) as exc:
            await client.get("content-service", "/internal/x")

    assert exc.value.retryable is True


async def test_post_is_not_retried_by_default() -> None:
    resolver = FakeResolver()

    async with Harness(lambda _: httpx.Response(500, text="boom"), resolver=resolver) as client:
        with pytest.raises(InternalServerError):
            await client.post("content-service", "/internal/x")

    assert resolver.calls == 1


async def test_other_http_error_is_not_retried() -> None:
    resolver = FakeResolver()

    async with Harness(lambda _: httpx.Response(400, text="bad"), resolver=resolver) as client:
        with pytest.raises(InternalHttpError):
            await client.post("content-service", "/internal/x")

    assert resolver.calls == 1


async def test_business_error() -> None:
    async with Harness(lambda _: _json(409, "冲突", {"issues": ["x"]})) as client:
        with pytest.raises(InternalBusinessError) as exc:
            await client.get("content-service", "/internal/x")

    assert exc.value.code == 409
    assert exc.value.data == {"issues": ["x"]}


async def test_protocol_error() -> None:
    async with Harness(lambda _: httpx.Response(200, text="not-json")) as client:
        with pytest.raises(InternalProtocolError):
            await client.get("content-service", "/internal/x")


async def test_transport_error_is_retried_for_get() -> None:
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        raise httpx.ConnectError("boom", request=request)

    async with Harness(handler) as client:
        with pytest.raises(InternalUnavailable):
            await client.get("content-service", "/internal/x")

    assert calls == 2


async def test_transport_error_is_not_retried_for_post() -> None:
    calls = 0

    def handler(request: httpx.Request) -> httpx.Response:
        nonlocal calls
        calls += 1
        raise httpx.ConnectError("boom", request=request)

    async with Harness(handler) as client:
        with pytest.raises(InternalUnavailable):
            await client.post("content-service", "/internal/x")

    assert calls == 1


async def test_no_healthy_instance() -> None:
    resolver = FakeResolver([])

    async with Harness(lambda _: _json(200), resolver=resolver) as client:
        with pytest.raises(InternalUnavailable):
            await client.get("content-service", "/internal/x")

    assert resolver.calls == 2


async def test_non_internal_path_is_rejected() -> None:
    async with Harness(lambda _: _json(200)) as client:
        with pytest.raises(ValueError):
            await client.get("content-service", "/api/x")


def test_error_hierarchy_has_kinds() -> None:
    errors: list[type[InternalClientError]] = [
        InternalUnavailable,
        InternalAuthError,
        InternalNotFound,
        InternalServerError,
        InternalHttpError,
        InternalBusinessError,
        InternalProtocolError,
    ]
    assert len({error.__name__ for error in errors}) == len(errors)
