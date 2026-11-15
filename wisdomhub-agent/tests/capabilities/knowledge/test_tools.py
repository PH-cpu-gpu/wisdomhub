"""知识图谱工具：参数校验、解析与错误收敛。"""

from typing import Any

import pytest

from app.capabilities.errors import ToolErrorKind, ToolException
from app.capabilities.knowledge.tools import (
    KNOWLEDGE_SERVICE,
    MAX_DEPTH,
    QUERY_PREREQUISITE_CHAIN,
    KnowledgeTools,
)
from app.infra.http.internal_client import (
    InternalAuthError,
    InternalBusinessError,
    InternalNotFound,
    InternalProtocolError,
    InternalServerError,
    InternalUnavailable,
)
from tests.fakes import FakeInternalClient

PATH = "/internal/knowledge/prerequisites"


def _payload(**overrides: Any) -> dict[str, Any]:
    data: dict[str, Any] = {
        "tagId": "3001",
        "name": "Redis",
        "depth": 2,
        "levels": [
            {"depth": 1, "tags": [{"tagId": "3002", "name": "数据结构"}]},
            {"depth": 2, "tags": [{"tagId": "3003", "name": "内存管理"}]},
        ],
        "truncated": False,
    }
    data.update(overrides)
    return data


def _tools(
    data: Any = None,
    *,
    error: Exception | None = None,
    timeout: bool = False,
) -> KnowledgeTools:
    if error is not None:
        return KnowledgeTools(FakeInternalClient(error=error))
    if timeout:
        return KnowledgeTools(FakeInternalClient(timeout=True))
    return KnowledgeTools(FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): data}))


async def test_queries_prerequisite_chain() -> None:
    internal = FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload()})

    tree = await KnowledgeTools(internal).query_prerequisite_chain("Redis")

    call = internal.calls[0]
    assert call.service == KNOWLEDGE_SERVICE
    assert call.path == PATH
    assert call.params == {"tech": "Redis", "maxDepth": MAX_DEPTH}
    assert tree.tag_id == "3001"
    assert tree.name == "Redis"
    assert tree.depth == 2
    assert tree.levels[0].depth == 1
    assert tree.levels[0].tags[0].tag_id == "3002"
    assert tree.levels[0].tags[0].name == "数据结构"
    assert tree.truncated is False


@pytest.mark.parametrize("technology", ["", "   "])
async def test_blank_technology_is_rejected(technology: str) -> None:
    tools = _tools(_payload())

    with pytest.raises(ToolException, match="技术名不能为空") as excinfo:
        await tools.query_prerequisite_chain(technology)

    assert excinfo.value.kind is ToolErrorKind.INVALID_INPUT


async def test_too_long_technology_is_rejected() -> None:
    tools = _tools(_payload())

    with pytest.raises(ToolException, match="50") as excinfo:
        await tools.query_prerequisite_chain("x" * 51)

    assert excinfo.value.kind is ToolErrorKind.INVALID_INPUT


async def test_not_found_becomes_user_message() -> None:
    error = InternalNotFound(
        "技术不存在: Foo",
        service=KNOWLEDGE_SERVICE,
        path=PATH,
        status=404,
        code=404,
    )

    with pytest.raises(ToolException) as excinfo:
        await _tools(error=error).query_prerequisite_chain("Foo")

    message = str(excinfo.value)
    assert message == "知识图谱中没有找到技术「Foo」"
    assert excinfo.value.kind is ToolErrorKind.NOT_FOUND
    assert PATH not in message
    assert "404" not in message
    assert isinstance(excinfo.value.__cause__, InternalNotFound)


@pytest.mark.parametrize(
    ("error", "kind", "message"),
    [
        (
            InternalUnavailable("down", service=KNOWLEDGE_SERVICE, path=PATH),
            ToolErrorKind.UNAVAILABLE,
            "服务暂不可用，请稍后重试",
        ),
        (
            InternalServerError("boom", service=KNOWLEDGE_SERVICE, path=PATH),
            ToolErrorKind.UNAVAILABLE,
            "服务暂不可用，请稍后重试",
        ),
        (
            InternalAuthError("bad sign", service=KNOWLEDGE_SERVICE, path=PATH),
            ToolErrorKind.UNAUTHORIZED,
            "未登录或登录已过期",
        ),
        (
            InternalProtocolError("bad payload", service=KNOWLEDGE_SERVICE, path=PATH),
            ToolErrorKind.INTERNAL,
            "服务返回异常，请稍后重试",
        ),
        (
            InternalBusinessError("bad", service=KNOWLEDGE_SERVICE, path=PATH, code=400),
            ToolErrorKind.UNAVAILABLE,
            "服务暂不可用，请稍后重试",
        ),
    ],
)
async def test_upstream_errors_are_mapped(
    error: Exception, kind: ToolErrorKind, message: str
) -> None:
    with pytest.raises(ToolException) as excinfo:
        await _tools(error=error).query_prerequisite_chain("Redis")

    assert excinfo.value.kind is kind
    assert str(excinfo.value) == message


async def test_timeout_becomes_unavailable() -> None:
    with pytest.raises(ToolException) as excinfo:
        await _tools(timeout=True).query_prerequisite_chain("Redis")

    assert excinfo.value.kind is ToolErrorKind.UNAVAILABLE
    assert str(excinfo.value) == "服务暂不可用，请稍后重试"


@pytest.mark.parametrize("data", ["oops", {"tagId": "3001"}])
async def test_invalid_payload_becomes_user_message(data: Any) -> None:
    with pytest.raises(ToolException) as excinfo:
        await _tools(data).query_prerequisite_chain("Redis")

    assert excinfo.value.kind is ToolErrorKind.INTERNAL
    assert str(excinfo.value) == "服务返回异常，请稍后重试"


async def test_isolated_tag_returns_empty_levels() -> None:
    tree = await _tools(_payload(depth=0, levels=[], truncated=True)).query_prerequisite_chain(
        "Isolated"
    )

    assert tree.levels == []
    assert tree.truncated is True


@pytest.mark.parametrize("technology", ["Redis", "redis"])
async def test_technology_case_is_preserved(technology: str) -> None:
    internal = FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload(name=technology)})

    await KnowledgeTools(internal).query_prerequisite_chain(technology)

    assert internal.calls[0].params == {"tech": technology, "maxDepth": MAX_DEPTH}


async def test_ignores_unknown_fields() -> None:
    tree = await _tools(_payload(extraField="x")).query_prerequisite_chain("Redis")

    assert tree.tag_id == "3001"


def test_tool_name_constant() -> None:
    assert QUERY_PREREQUISITE_CHAIN == "query_prerequisite_chain"
