import logging
from typing import Any

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.tools import BaseTool, ToolException
from langgraph.graph import END, START, StateGraph
from langgraph.prebuilt import ToolNode

from graphs.base import AgentState

log = logging.getLogger(__name__)

MAX_TOOL_ROUNDS = 3
RECURSION_LIMIT = 12


class QaState(AgentState):
    tool_rounds: int


def _tool_error(exc: Exception) -> str:
    if isinstance(exc, ToolException):
        return str(exc)
    log.exception("工具执行异常", exc_info=exc)
    return "工具调用失败，请稍后重试"


def build_qa_graph(llm: BaseChatModel, tools: list[BaseTool]):
    tool_model = llm.bind_tools(tools) if tools else None
    tool_node = ToolNode(tools, handle_tool_errors=_tool_error) if tools else None

    async def agent(state: QaState) -> dict[str, Any]:
        rounds = state.get("tool_rounds", 0)
        model = tool_model if tool_model is not None and rounds < MAX_TOOL_ROUNDS else llm
        response = await model.ainvoke(state["messages"])
        if getattr(response, "tool_calls", None):
            rounds += 1
        return {"messages": [response], "tool_rounds": rounds}

    def route(state: QaState) -> str:
        last = state["messages"][-1]
        if (
            isinstance(last, AIMessage)
            and last.tool_calls
            and state.get("tool_rounds", 0) <= MAX_TOOL_ROUNDS
        ):
            return "tools"
        return END

    builder = StateGraph(QaState)
    builder.add_node("agent", agent)
    builder.add_edge(START, "agent")
    if tool_node is not None:
        builder.add_node("tools", tool_node)
        builder.add_conditional_edges("agent", route, ["tools", END])
        builder.add_edge("tools", "agent")
    else:
        builder.add_edge("agent", END)
    return builder.compile()
