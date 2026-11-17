"""会话图节点：QA、文本问答、学习路线取数与讲解。"""

from collections.abc import AsyncIterator, Sequence
from typing import Any, Protocol

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    BaseMessageChunk,
    HumanMessage,
    SystemMessage,
)

from app.capabilities.errors import ToolErrorKind, ToolException
from app.capabilities.knowledge.tools import (
    QUERY_PREREQUISITE_CHAIN,
    KnowledgeTools,
    PrerequisiteTree,
)
from app.chat.graph.state import ChatState, FetchStatus
from app.contracts.artifacts import PlanStage, RouteData, TechRef
from app.contracts.sse import Step, StepStatus

QA_AGENT = "qa_agent"
TEXT_QA_AGENT = "text_qa_agent"
LEARNING_FETCH = "learning_fetch"
LEARNING_AGENT = "learning_agent"

STEP_ID = "s1"
PREREQUISITE_LABEL = "查询前置知识链"
TOOLS_UNAVAILABLE_MESSAGE = "知识图谱暂不可用，请稍后重试"
NO_ROUTE_MESSAGE = "学习路线暂时不可用，请稍后重试"

NodeUpdate = dict[str, Any]


class Node(Protocol):
    def __call__(self, state: ChatState) -> Any: ...


class ChatModel(Protocol):
    def astream(
        self, messages: Sequence[BaseMessage], **kwargs: Any
    ) -> AsyncIterator[BaseMessageChunk]: ...


async def _stream_answer(llm: ChatModel, messages: Sequence[BaseMessage]) -> AIMessage:
    accumulated: BaseMessageChunk | None = None
    async for chunk in llm.astream(messages):
        accumulated = chunk if accumulated is None else accumulated + chunk
    if accumulated is None:
        return AIMessage(content="")
    return AIMessage(
        content=message_text(accumulated),
        id=accumulated.id,
        usage_metadata=getattr(accumulated, "usage_metadata", None),
        response_metadata=accumulated.response_metadata or {},
    )


def message_text(message: BaseMessage) -> str:
    content = message.content
    if isinstance(content, str):
        return content
    parts: list[str] = []
    for block in content:
        if isinstance(block, str):
            parts.append(block)
        elif isinstance(block, dict) and block.get("type") in (None, "text"):
            text = block.get("text")
            if isinstance(text, str):
                parts.append(text)
    return "".join(parts)


def make_qa_agent(llm: ChatModel) -> Node:
    async def qa_agent(state: ChatState) -> NodeUpdate:
        return {"messages": [await _stream_answer(llm, state["messages"])]}

    return qa_agent


def make_text_qa_agent(llm: ChatModel) -> Node:
    async def text_qa_agent(state: ChatState) -> NodeUpdate:
        return {"messages": [await _stream_answer(llm, state["messages"])]}

    return text_qa_agent


def build_route(tree: PrerequisiteTree, technology: str, known: Sequence[str]) -> RouteData:
    known_names = {name.casefold() for name in known}
    stages: list[PlanStage] = []
    for level in sorted(tree.levels, key=lambda item: item.depth, reverse=True):
        tags = [
            TechRef(tag_id=tag.tag_id, name=tag.name)
            for tag in level.tags
            if tag.name.casefold() not in known_names
        ]
        if tags:
            stages.append(PlanStage(depth=level.depth, tags=tags))
    target_name = tree.name or technology
    stages.append(PlanStage(depth=0, tags=[TechRef(tag_id=tree.tag_id, name=target_name)]))
    return RouteData(
        target=TechRef(tag_id=tree.tag_id, name=target_name),
        known=list(known),
        truncated=tree.truncated,
        stages=stages,
    )


def _prerequisite_step(tree: PrerequisiteTree) -> Step:
    summary = f"返回 {len(tree.levels)} 层前置" if tree.levels else "没有前置技术"
    return Step(
        id=STEP_ID,
        tool=QUERY_PREREQUISITE_CHAIN,
        label=PREREQUISITE_LABEL,
        status=StepStatus.DONE,
        summary=summary,
    )


def _fetch_failure(status: FetchStatus, message: str) -> NodeUpdate:
    return {
        "steps": [
            Step(
                id=STEP_ID,
                tool=QUERY_PREREQUISITE_CHAIN,
                label=PREREQUISITE_LABEL,
                status=StepStatus.ERROR,
                summary=message,
            )
        ],
        "fetch_status": status,
        "fetch_error": message,
    }


def make_learning_fetch(tools: KnowledgeTools | None) -> Node:
    async def learning_fetch(state: ChatState) -> NodeUpdate:
        technology = state.get("technology", "")
        if tools is None:
            return _fetch_failure(FetchStatus.UNAVAILABLE, TOOLS_UNAVAILABLE_MESSAGE)

        try:
            tree = await tools.query_prerequisite_chain(technology)
        except ToolException as exc:
            status = (
                FetchStatus.NOT_FOUND
                if exc.kind is ToolErrorKind.NOT_FOUND
                else FetchStatus.UNAVAILABLE
            )
            return {
                "steps": [
                    exc.to_step(
                        step_id=STEP_ID,
                        tool=QUERY_PREREQUISITE_CHAIN,
                        label=PREREQUISITE_LABEL,
                    )
                ],
                "fetch_status": status,
                "fetch_error": exc.message,
            }

        return {
            "route": build_route(tree, technology, state.get("known", [])),
            "steps": [_prerequisite_step(tree)],
            "fetch_status": FetchStatus.OK,
        }

    return learning_fetch


def _with_route(messages: Sequence[BaseMessage], route: RouteData) -> list[BaseMessage]:
    route_message = SystemMessage(content=f"<route>{route.model_dump_json(by_alias=True)}</route>")
    items = list(messages)
    if items and isinstance(items[-1], HumanMessage):
        items.insert(len(items) - 1, route_message)
    else:
        items.append(route_message)
    return items


def make_learning_agent(llm: ChatModel) -> Node:
    async def learning_agent(state: ChatState) -> NodeUpdate:
        fetch_error = state.get("fetch_error")
        if fetch_error:
            return {"messages": [AIMessage(content=fetch_error)]}

        route = state.get("route")
        if route is None:
            return {"messages": [AIMessage(content=NO_ROUTE_MESSAGE)]}

        messages = _with_route(state["messages"], route)
        return {"messages": [await _stream_answer(llm, messages)]}

    return learning_agent
