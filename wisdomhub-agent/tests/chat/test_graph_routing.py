"""单图装配：节点结构、三分支执行、提示词注入与失败降级。"""

from typing import Any, cast

import pytest
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from langchain_core.runnables import RunnableConfig
from langgraph.graph import START

from app.capabilities.knowledge.tools import KNOWLEDGE_SERVICE, KnowledgeTools
from app.chat.graph.build import RECURSION_LIMIT, ROUTER, build_graph, select_branch
from app.chat.graph.node import (
    LEARNING_AGENT,
    LEARNING_FETCH,
    QA_AGENT,
    TEXT_QA_AGENT,
    TOOLS_UNAVAILABLE_MESSAGE,
)
from app.chat.graph.state import ChatState, FetchStatus
from app.chat.prompt import LEARNING_PATH_SYSTEM_PROMPT, QA_SYSTEM_PROMPT, TEXT_QA_SYSTEM_PROMPT
from app.contracts.workflow import WorkflowKind
from app.infra.http.internal_client import InternalNotFound
from tests.fakes import FakeInternalClient, FakeLLM

PATH = "/internal/knowledge/prerequisites"
GRAPH_CONFIG: RunnableConfig = {"recursion_limit": RECURSION_LIMIT}


def _payload() -> dict[str, Any]:
    return {
        "tagId": "3001",
        "name": "Redis",
        "depth": 0,
        "levels": [
            {"depth": 1, "tags": [{"tagId": "3002", "name": "数据结构"}]},
            {"depth": 2, "tags": [{"tagId": "3003", "name": "内存管理"}]},
        ],
        "truncated": False,
    }


def test_graph_registers_unique_nodes() -> None:
    graph = build_graph(FakeLLM(["答"]), None)

    assert set(graph.nodes) == {
        START,
        ROUTER,
        QA_AGENT,
        TEXT_QA_AGENT,
        LEARNING_FETCH,
        LEARNING_AGENT,
    }


@pytest.mark.parametrize(
    ("workflow", "expected"),
    [
        (WorkflowKind.QA, QA_AGENT),
        (WorkflowKind.TEXT_QA, TEXT_QA_AGENT),
        (WorkflowKind.LEARNING_PATH, LEARNING_FETCH),
    ],
)
def test_select_branch_maps_workflow(workflow: WorkflowKind, expected: str) -> None:
    state: ChatState = {"messages": [], "workflow": workflow}

    assert select_branch(state) == expected


def test_select_branch_rejects_unknown_workflow() -> None:
    state = cast(ChatState, cast(Any, {"messages": [], "workflow": 99}))

    with pytest.raises(KeyError):
        select_branch(state)


def test_recursion_limit_is_twelve() -> None:
    assert RECURSION_LIMIT == 12


async def test_qa_branch_adds_single_system_prompt() -> None:
    llm = FakeLLM(["Redis 支持 RDB 和 AOF。"])
    graph = build_graph(llm, None)

    result = await graph.ainvoke(
        {
            "messages": [HumanMessage("Redis 怎么持久化？")],
            "workflow": WorkflowKind.QA,
        },
        config=GRAPH_CONFIG,
    )

    messages = result["messages"]
    assert [type(message) for message in messages] == [
        SystemMessage,
        HumanMessage,
        AIMessage,
    ]
    assert messages[0].content == QA_SYSTEM_PROMPT
    assert messages[1].content == "Redis 怎么持久化？"
    assert messages[2].content == "Redis 支持 RDB 和 AOF。"
    assert len(llm.stream_calls) == 1
    assert llm.bound_tools == []


async def test_text_branch_wraps_current_question_once() -> None:
    llm = FakeLLM(["这段文本介绍了 Redis 的持久化方式。"])
    graph = build_graph(llm, None)

    result = await graph.ainvoke(
        {
            "messages": [HumanMessage("这段文本讲了什么？")],
            "workflow": WorkflowKind.TEXT_QA,
            "text": "Redis 支持 RDB 和 AOF。",
        },
        config=GRAPH_CONFIG,
    )

    messages = result["messages"]
    assert len(messages) == 3
    assert messages[0].content == TEXT_QA_SYSTEM_PROMPT
    assert (
        messages[1].content
        == "<selected_text>Redis 支持 RDB 和 AOF。</selected_text>\n\n这段文本讲了什么？"
    )
    assert messages[1].content.count("<selected_text>") == 1
    assert messages[2].content == "这段文本介绍了 Redis 的持久化方式。"
    assert len(llm.stream_calls) == 1


async def test_learning_branch_builds_route_and_explains() -> None:
    internal = FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload()})
    llm = FakeLLM(["先学数据结构，再学内存管理。"])
    graph = build_graph(llm, KnowledgeTools(internal))

    result = await graph.ainvoke(
        {
            "messages": [HumanMessage("我要学 Redis")],
            "workflow": WorkflowKind.LEARNING_PATH,
            "technology": "Redis",
            "known": ["Java"],
        },
        config=GRAPH_CONFIG,
    )

    assert result["fetch_status"] is FetchStatus.OK
    assert [stage.depth for stage in result["route"].stages] == [2, 1, 0]
    assert result["steps"][0].status == "done"
    assert result["messages"][-1].content == "先学数据结构，再学内存管理。"
    assert result["messages"][0].content == LEARNING_PATH_SYSTEM_PROMPT

    sent = llm.stream_calls[0]
    route_messages = [
        message
        for message in sent
        if isinstance(message, SystemMessage) and "<route>" in message.content
    ]
    assert len(route_messages) == 1
    assert sent[-1].content == "我要学 Redis"


async def test_learning_failure_returns_fallback_without_llm() -> None:
    tools = KnowledgeTools(
        FakeInternalClient(error=InternalNotFound("不存在", service=KNOWLEDGE_SERVICE, path=PATH))
    )
    llm = FakeLLM(["不应出现"])
    graph = build_graph(llm, tools)

    result = await graph.ainvoke(
        {
            "messages": [HumanMessage("我要学 Redis")],
            "workflow": WorkflowKind.LEARNING_PATH,
            "technology": "Redis",
        },
        config=GRAPH_CONFIG,
    )

    assert result["fetch_status"] is FetchStatus.NOT_FOUND
    assert result["messages"][-1].content == "知识图谱中没有找到技术「Redis」"
    assert llm.stream_calls == []


async def test_learning_without_tools_returns_fallback_without_llm() -> None:
    llm = FakeLLM(["不应出现"])
    graph = build_graph(llm, None)

    result = await graph.ainvoke(
        {
            "messages": [HumanMessage("我要学 Redis")],
            "workflow": WorkflowKind.LEARNING_PATH,
            "technology": "Redis",
        },
        config=GRAPH_CONFIG,
    )

    assert result["fetch_status"] is FetchStatus.UNAVAILABLE
    assert result["messages"][-1].content == TOOLS_UNAVAILABLE_MESSAGE
    assert llm.stream_calls == []
