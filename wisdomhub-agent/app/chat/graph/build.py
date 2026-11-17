"""单图装配：router 分流、三条分支与唯一 compile。"""

from collections.abc import Sequence

from langchain_core.messages import BaseMessage, HumanMessage, RemoveMessage, SystemMessage
from langgraph.graph import END, START, StateGraph
from langgraph.graph.message import REMOVE_ALL_MESSAGES
from langgraph.graph.state import CompiledStateGraph

from app.capabilities.knowledge.tools import KnowledgeTools
from app.chat.graph.node import (
    LEARNING_AGENT,
    LEARNING_FETCH,
    QA_AGENT,
    TEXT_QA_AGENT,
    ChatModel,
    NodeUpdate,
    make_learning_agent,
    make_learning_fetch,
    make_qa_agent,
    make_text_qa_agent,
)
from app.chat.graph.state import ChatState
from app.chat.prompt import system_prompt
from app.contracts.workflow import WorkflowKind

ROUTER = "router"
RECURSION_LIMIT = 12

_BRANCHES: dict[WorkflowKind, str] = {
    WorkflowKind.QA: QA_AGENT,
    WorkflowKind.LEARNING_PATH: LEARNING_FETCH,
    WorkflowKind.TEXT_QA: TEXT_QA_AGENT,
}


def select_branch(state: ChatState) -> str:
    return _BRANCHES[state["workflow"]]


def _with_selected_text(messages: Sequence[BaseMessage], text: str) -> list[BaseMessage]:
    items = list(messages)
    for index in range(len(items) - 1, -1, -1):
        message = items[index]
        if isinstance(message, HumanMessage):
            question = message.content if isinstance(message.content, str) else str(message.content)
            items[index] = HumanMessage(
                content=f"<selected_text>{text}</selected_text>\n\n{question}"
            )
            return items
    items.append(HumanMessage(content=f"<selected_text>{text}</selected_text>"))
    return items


def router_node(state: ChatState) -> NodeUpdate:
    workflow = state["workflow"]
    messages: Sequence[BaseMessage] = state["messages"]
    text = state.get("text")
    if workflow is WorkflowKind.TEXT_QA and text:
        messages = _with_selected_text(messages, text)
    return {
        "messages": [
            RemoveMessage(id=REMOVE_ALL_MESSAGES),
            SystemMessage(content=system_prompt(workflow)),
            *messages,
        ]
    }


def build_graph(llm: ChatModel, tools: KnowledgeTools | None) -> CompiledStateGraph:
    builder: StateGraph[ChatState, None, ChatState, ChatState] = StateGraph(ChatState)
    builder.add_node(ROUTER, router_node)
    builder.add_node(QA_AGENT, make_qa_agent(llm))
    builder.add_node(TEXT_QA_AGENT, make_text_qa_agent(llm))
    builder.add_node(LEARNING_FETCH, make_learning_fetch(tools))
    builder.add_node(LEARNING_AGENT, make_learning_agent(llm))

    builder.add_edge(START, ROUTER)
    builder.add_conditional_edges(ROUTER, select_branch, [*_BRANCHES.values()])
    builder.add_edge(QA_AGENT, END)
    builder.add_edge(TEXT_QA_AGENT, END)
    builder.add_edge(LEARNING_FETCH, LEARNING_AGENT)
    builder.add_edge(LEARNING_AGENT, END)
    return builder.compile()
