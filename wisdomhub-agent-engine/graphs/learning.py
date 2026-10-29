import json
import logging
from typing import Any

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, SystemMessage
from langchain_core.tools import ToolException
from langgraph.graph import END, START, StateGraph

from graphs.base import AgentState
from tools.internal_client import InternalClient
from tools.java_api import (
    PREREQUISITE_TOOL_NAME,
    KnowledgeServiceUnavailable,
    TechnologyNotFound,
    query_prerequisite_chain,
)

log = logging.getLogger(__name__)

_NOT_FOUND_SUFFIX = "请从标签库选择已收录的技术后重试。"
_UNAVAILABLE_REPLY = "知识图谱服务暂时不可用，请稍后重试。"
_MISSING_TECHNOLOGY_REPLY = "请先指定要学习的技术。"


class LearningState(AgentState):
    tool_rounds: int
    technology: str
    known: list[str]
    route: dict[str, Any]
    steps: list[dict[str, Any]]
    fetch_status: str
    fetch_error: str


def _build_route(
    tree: dict[str, Any],
    known_names: list[str],
    known_keys: set[str],
) -> dict[str, Any]:
    target = {
        "tagId": str(tree.get("tagId") or ""),
        "name": str(tree.get("name") or ""),
    }

    stages: list[dict[str, Any]] = []
    for level in reversed(tree.get("levels") or []):
        tags = [
            {
                "tagId": str(tag.get("tagId") or ""),
                "name": str(tag.get("name") or ""),
            }
            for tag in level.get("tags") or []
            if str(tag.get("tagId") or "").strip()
            and str(tag.get("name") or "").strip().casefold() not in known_keys
        ]
        if tags:
            stages.append({"depth": level.get("depth", 0), "tags": tags})
    stages.append({"depth": 0, "tags": [target]})

    return {
        "target": target,
        "known": known_names,
        "truncated": bool(tree.get("truncated")),
        "stages": stages,
    }


def build_learning_graph(answer_llm: BaseChatModel, client: InternalClient):
    async def fetch(state: LearningState) -> dict[str, Any]:
        technology = (state.get("technology") or "").strip()
        if not technology:
            return {"fetch_status": "invalid", "route": {}, "steps": []}

        steps: list[dict[str, Any]] = [
            {"tool": PREREQUISITE_TOOL_NAME, "args": {"technology": technology}}
        ]
        try:
            tree = await query_prerequisite_chain(client, technology)
        except TechnologyNotFound as exc:
            return {
                "fetch_status": "not_found",
                "route": {},
                "steps": steps,
                "fetch_error": str(exc),
            }
        except KnowledgeServiceUnavailable as exc:
            return {
                "fetch_status": "unavailable",
                "route": {},
                "steps": steps,
                "fetch_error": str(exc),
            }
        except ToolException as exc:
            log.warning("前置链查询失败: %s", exc)
            return {
                "fetch_status": "unavailable",
                "route": {},
                "steps": steps,
                "fetch_error": str(exc),
            }

        known_names = [name.strip() for name in (state.get("known") or []) if name.strip()]
        known_keys = {name.casefold() for name in known_names}
        route = _build_route(tree, known_names, known_keys)
        return {"fetch_status": "ok", "route": route, "steps": steps}

    async def agent(state: LearningState) -> dict[str, Any]:
        status = state.get("fetch_status") or "ok"

        if status == "not_found":
            detail = state.get("fetch_error") or "知识图谱中没有找到该技术"
            return {"messages": [AIMessage(f"{detail}。\n\n{_NOT_FOUND_SUFFIX}")]}
        if status == "unavailable":
            return {"messages": [AIMessage(_UNAVAILABLE_REPLY)]}
        if status == "invalid":
            return {"messages": [AIMessage(_MISSING_TECHNOLOGY_REPLY)]}

        data = json.dumps(state.get("route") or {}, ensure_ascii=False)
        hint = (
            "以下是知识图谱里的前置路线数据；只能使用数据里的技术，按前置关系分析：\n"
            f"{data}\n"
            "参考文章由前端单独展示给用户；不要引用或编造文章内容、文章链接，不要输出工具 JSON。"
        )
        messages = list(state["messages"])
        extra = SystemMessage(content=hint)
        if messages:
            messages.insert(len(messages) - 1, extra)
        else:
            messages.append(extra)
        response = await answer_llm.ainvoke(messages)
        return {"messages": [response]}

    builder = StateGraph(LearningState)
    builder.add_node("fetch", fetch)
    builder.add_node("agent", agent)
    builder.add_edge(START, "fetch")
    builder.add_edge("fetch", "agent")
    builder.add_edge("agent", END)
    return builder.compile()
