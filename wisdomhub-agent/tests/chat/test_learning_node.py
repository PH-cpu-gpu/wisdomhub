"""学习路线节点：route 构造、取数失败降级与讲解注入。"""

from typing import Any

from langchain_core.messages import AnyMessage, HumanMessage, SystemMessage

from app.capabilities.knowledge.tools import (
    KNOWLEDGE_SERVICE,
    MAX_DEPTH,
    KnowledgeTools,
    PrerequisiteLevel,
    PrerequisiteTree,
    TagBrief,
)
from app.chat.graph.node import (
    NO_ROUTE_MESSAGE,
    TOOLS_UNAVAILABLE_MESSAGE,
    build_route,
    make_learning_agent,
    make_learning_fetch,
)
from app.chat.graph.state import ChatState, FetchStatus
from app.chat.prompt import LEARNING_PATH_SYSTEM_PROMPT
from app.contracts.workflow import WorkflowKind
from app.infra.http.internal_client import InternalNotFound, InternalUnavailable
from tests.fakes import FakeInternalClient, FakeLLM

PATH = "/internal/knowledge/prerequisites"


def _tree(
    levels: list[PrerequisiteLevel] | None = None,
    *,
    truncated: bool = False,
) -> PrerequisiteTree:
    return PrerequisiteTree(
        tag_id="3001",
        name="Redis",
        depth=0,
        levels=levels
        if levels is not None
        else [
            PrerequisiteLevel(depth=1, tags=[TagBrief(tag_id="3002", name="数据结构")]),
            PrerequisiteLevel(depth=3, tags=[TagBrief(tag_id="3003", name="内存管理")]),
        ],
        truncated=truncated,
    )


def _payload(levels: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    return {
        "tagId": "3001",
        "name": "Redis",
        "depth": 0,
        "levels": levels
        if levels is not None
        else [
            {"depth": 1, "tags": [{"tagId": "3002", "name": "数据结构"}]},
            {"depth": 2, "tags": [{"tagId": "3003", "name": "内存管理"}]},
        ],
        "truncated": False,
    }


def _tools(data: Any = None, *, error: Exception | None = None) -> KnowledgeTools:
    if error is not None:
        return KnowledgeTools(FakeInternalClient(error=error))
    return KnowledgeTools(FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): data}))


def test_build_route_orders_deepest_first_with_target_last() -> None:
    route = build_route(_tree(), "Redis", ["Java"])

    assert route.target.tag_id == "3001"
    assert route.target.name == "Redis"
    assert route.known == ["Java"]
    assert route.truncated is False
    assert [(stage.depth, [tag.name for tag in stage.tags]) for stage in route.stages] == [
        (3, ["内存管理"]),
        (1, ["数据结构"]),
        (0, ["Redis"]),
    ]


def test_build_route_filters_known_and_drops_empty_levels() -> None:
    tree = _tree(
        [
            PrerequisiteLevel(
                depth=2,
                tags=[TagBrief(tag_id="1", name="JAVA"), TagBrief(tag_id="2", name="Redis")],
            ),
            PrerequisiteLevel(depth=1, tags=[TagBrief(tag_id="3", name="java")]),
        ]
    )

    route = build_route(tree, "Redis", ["Java"])

    assert [(stage.depth, [tag.tag_id for tag in stage.tags]) for stage in route.stages] == [
        (2, ["2"]),
        (0, ["3001"]),
    ]


def test_build_route_without_prerequisites_only_has_target() -> None:
    route = build_route(_tree([]), "Redis", [])

    assert [(stage.depth, [tag.name for tag in stage.tags]) for stage in route.stages] == [
        (0, ["Redis"])
    ]


def test_build_route_copies_truncated() -> None:
    assert build_route(_tree(truncated=True), "Redis", []).truncated is True


async def test_learning_fetch_builds_route_and_done_step() -> None:
    internal = FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload()})
    state: ChatState = {
        "messages": [],
        "workflow": WorkflowKind.LEARNING_PATH,
        "technology": "Redis",
        "known": ["Java"],
    }

    update = await make_learning_fetch(KnowledgeTools(internal))(state)

    route = update["route"]
    assert route.target.tag_id == "3001"
    assert [stage.depth for stage in route.stages] == [2, 1, 0]
    assert update["fetch_status"] is FetchStatus.OK
    step = update["steps"][0]
    assert step.status == "done"
    assert step.summary == "返回 2 层前置"
    assert internal.calls[0].params == {"tech": "Redis", "maxDepth": MAX_DEPTH}


async def test_learning_fetch_maps_not_found_to_fallback() -> None:
    tools = _tools(error=InternalNotFound("不存在", service=KNOWLEDGE_SERVICE, path=PATH))
    state: ChatState = {
        "messages": [],
        "workflow": WorkflowKind.LEARNING_PATH,
        "technology": "Redis",
    }

    update = await make_learning_fetch(tools)(state)

    assert update["fetch_status"] is FetchStatus.NOT_FOUND
    assert update["fetch_error"] == "知识图谱中没有找到技术「Redis」"
    assert update["steps"][0].status == "error"
    assert "route" not in update


async def test_learning_fetch_maps_upstream_error_to_unavailable() -> None:
    tools = _tools(error=InternalUnavailable("超时", service=KNOWLEDGE_SERVICE, path=PATH))

    update = await make_learning_fetch(tools)(
        {
            "messages": [],
            "workflow": WorkflowKind.LEARNING_PATH,
            "technology": "Redis",
        }
    )

    assert update["fetch_status"] is FetchStatus.UNAVAILABLE
    assert update["fetch_error"] == "服务暂不可用，请稍后重试"
    assert update["steps"][0].status == "error"


async def test_learning_fetch_without_tools_returns_fallback() -> None:
    update = await make_learning_fetch(None)(
        {
            "messages": [],
            "workflow": WorkflowKind.LEARNING_PATH,
            "technology": "Redis",
        }
    )

    assert update["fetch_status"] is FetchStatus.UNAVAILABLE
    assert update["fetch_error"] == TOOLS_UNAVAILABLE_MESSAGE
    assert update["steps"][0].summary == TOOLS_UNAVAILABLE_MESSAGE


async def test_learning_agent_injects_route_before_question() -> None:
    route = build_route(_tree(), "Redis", ["Java"])
    messages: list[AnyMessage] = [
        SystemMessage(LEARNING_PATH_SYSTEM_PROMPT),
        HumanMessage("我要学 Redis"),
    ]
    state: ChatState = {
        "messages": messages,
        "workflow": WorkflowKind.LEARNING_PATH,
        "route": route,
        "fetch_status": FetchStatus.OK,
    }
    llm = FakeLLM(["先学数据结构，再学内存管理。"])

    result = await make_learning_agent(llm)(state)

    sent = llm.stream_calls[0]
    assert len(sent) == 3
    assert sent[0] is messages[0]
    route_message = sent[1]
    assert isinstance(route_message, SystemMessage)
    assert isinstance(route_message.content, str)
    assert "<route>" in route_message.content
    assert '"tagId":"3001"' in route_message.content
    assert sent[2] is messages[1]
    assert result["messages"][0].content == "先学数据结构，再学内存管理。"
    assert llm.bound_tools == []


async def test_learning_agent_returns_fetch_error_without_llm() -> None:
    llm = FakeLLM(["不应出现"])
    state: ChatState = {
        "messages": [HumanMessage("我要学 Foo")],
        "workflow": WorkflowKind.LEARNING_PATH,
        "fetch_status": FetchStatus.NOT_FOUND,
        "fetch_error": "知识图谱中没有找到技术「Foo」",
    }

    result = await make_learning_agent(llm)(state)

    assert llm.stream_calls == []
    assert result["messages"][0].content == "知识图谱中没有找到技术「Foo」"


async def test_learning_agent_without_route_returns_fallback() -> None:
    llm = FakeLLM(["不应出现"])
    state: ChatState = {
        "messages": [HumanMessage("我要学 Redis")],
        "workflow": WorkflowKind.LEARNING_PATH,
    }

    result = await make_learning_agent(llm)(state)

    assert llm.stream_calls == []
    assert result["messages"][0].content == NO_ROUTE_MESSAGE
