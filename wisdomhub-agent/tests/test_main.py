"""应用外壳：路由注册、文档、异常处理器与 trace。"""

from httpx import ASGITransport, AsyncClient, Response

from app import __version__
from app.core.config import Settings
from app.core.deps import AppDeps
from app.main import TITLE, create_app

EXPECTED_PATHS = {
    "/api/agent/chat",
    "/api/agent/learning-path",
    "/api/agent/text-qa",
    "/api/agent/conversations",
    "/api/agent/conversations/{conversation_id}",
    "/api/agent/analysis/{article_id}",
}
USER = {"X-User-Id": "1"}


def _client(app: object) -> AsyncClient:
    return AsyncClient(transport=ASGITransport(app=app), base_url="http://test")


async def _get(settings: Settings, path: str, headers: dict[str, str] | None = None) -> Response:
    app = create_app(settings)
    async with _client(app) as client:
        return await client.get(path, headers=headers)


async def test_openapi_exposes_agent_paths(settings: Settings) -> None:
    response = await _get(settings, "/openapi.json")

    body = response.json()
    assert set(body["paths"]) == EXPECTED_PATHS
    assert body["info"]["title"] == TITLE
    assert body["info"]["version"] == __version__


async def test_docs_page_is_available(settings: Settings) -> None:
    response = await _get(settings, "/docs")

    assert response.status_code == 200
    assert "text/html" in response.headers["content-type"]


async def test_unknown_path_uses_unified_body(settings: Settings) -> None:
    response = await _get(settings, "/api/agent/nope")

    assert response.status_code == 200
    assert response.json()["code"] == 404


async def test_wrong_method_uses_unified_body(settings: Settings) -> None:
    response = await _get(settings, "/api/agent/chat")

    assert response.status_code == 200
    assert response.json()["code"] == 405


async def test_trace_id_is_generated_and_echoed(settings: Settings) -> None:
    generated = await _get(settings, "/openapi.json")
    assert len(generated.headers["X-Trace-Id"]) == 32

    echoed = await _get(settings, "/openapi.json", headers={"X-Trace-Id": "trace-1"})
    assert echoed.headers["X-Trace-Id"] == "trace-1"

    invalid = await _get(settings, "/openapi.json", headers={"X-Trace-Id": "bad id"})
    assert invalid.headers["X-Trace-Id"] != "bad id"


async def test_chat_returns_503_when_dependencies_missing(settings: Settings) -> None:
    app = create_app(settings)
    app.state.deps = AppDeps(settings=settings)

    async with _client(app) as client:
        response = await client.post(
            "/api/agent/chat",
            headers=USER,
            json={"question": "你好"},
        )

    assert response.status_code == 200
    assert response.json()["code"] == 503


async def test_analysis_returns_503_when_service_missing(settings: Settings) -> None:
    app = create_app(settings)
    app.state.deps = AppDeps(settings=settings)

    async with _client(app) as client:
        response = await client.get("/api/agent/analysis/1001")

    assert response.status_code == 200
    assert response.json()["code"] == 503
