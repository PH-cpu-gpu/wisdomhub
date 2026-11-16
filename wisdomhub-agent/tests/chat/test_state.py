"""会话图状态：messages reducer、字段可选性与分支状态。"""

from typing import Annotated, get_args, get_origin, get_type_hints

from langchain_core.messages import AIMessage, HumanMessage, ToolMessage
from langgraph.graph.message import add_messages

from app.chat.graph.state import ChatState, FetchStatus
from app.contracts.artifacts import RouteData, TechRef
from app.contracts.sse import Step, StepStatus
from app.contracts.workflow import WorkflowKind

BUSINESS_FIELDS = {
    "workflow",
    "technology",
    "known",
    "text",
    "route",
    "steps",
    "fetch_status",
    "fetch_error",
}


def test_messages_uses_add_messages_reducer() -> None:
    annotation = get_type_hints(ChatState, include_extras=True)["messages"]

    assert get_origin(annotation) is Annotated
    args = get_args(annotation)
    assert get_origin(args[0]) is list
    assert args[1] is add_messages


def test_messages_required_and_business_fields_optional() -> None:
    assert "messages" in ChatState.__required_keys__
    assert ChatState.__optional_keys__ == BUSINESS_FIELDS


def test_qa_branch_state_is_valid() -> None:
    state: ChatState = {
        "messages": [HumanMessage("Redis 持久化怎么做？")],
        "workflow": WorkflowKind.QA,
    }

    assert state["messages"][0].type == "human"
    assert state["workflow"] is WorkflowKind.QA


def test_learning_branch_state_is_valid() -> None:
    state: ChatState = {
        "messages": [HumanMessage("我要学 Redis")],
        "workflow": WorkflowKind.LEARNING_PATH,
        "technology": "Redis",
        "known": ["Java"],
        "route": RouteData(
            target=TechRef(tag_id="10", name="Redis"),
            known=["Java"],
            truncated=False,
            stages=[],
        ),
        "steps": [
            Step(
                id="s1",
                tool="query_prerequisite_chain",
                label="查询前置知识链",
                status=StepStatus.DONE,
            )
        ],
        "fetch_status": FetchStatus.OK,
    }

    assert state["route"] is not None
    assert state["steps"][0].tool == "query_prerequisite_chain"


def test_learning_failure_state_is_valid() -> None:
    state: ChatState = {
        "messages": [HumanMessage("我要学 Redis")],
        "workflow": WorkflowKind.LEARNING_PATH,
        "steps": [
            Step(
                id="s1",
                tool="query_prerequisite_chain",
                label="查询前置知识链",
                status=StepStatus.ERROR,
                summary="知识图谱中没有找到技术「Redis」",
            )
        ],
        "fetch_status": FetchStatus.NOT_FOUND,
        "fetch_error": "知识图谱中没有找到技术「Redis」",
    }

    assert state["fetch_status"] is FetchStatus.NOT_FOUND
    assert "route" not in state


def test_text_branch_state_is_valid() -> None:
    state: ChatState = {
        "messages": [HumanMessage("这段文本讲了什么")],
        "workflow": WorkflowKind.TEXT_QA,
        "text": "Redis 支持 RDB 和 AOF 两种持久化方式。",
    }

    assert state["text"].startswith("Redis")


def test_messages_accept_three_message_types() -> None:
    state: ChatState = {
        "messages": [
            HumanMessage("问"),
            AIMessage("答"),
            ToolMessage("工具结果", tool_call_id="call-1"),
        ]
    }

    assert [message.type for message in state["messages"]] == ["human", "ai", "tool"]
