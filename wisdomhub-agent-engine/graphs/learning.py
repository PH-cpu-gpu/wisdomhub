import json
import logging
from typing import Any

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import AIMessage, SystemMessage
from langchain_core.tools import ToolException
from langgraph.graph import END, START, StateGraph
from pydantic import BaseModel, Field

from graphs.base import AgentState
from models.structured import invoke_structured
from tools.internal_client import InternalClient
from tools.java_api import PREREQUISITE_TOOL_NAME, query_prerequisite_chain

log = logging.getLogger(__name__)


class LearningState(AgentState):
    tool_rounds: int
    technology: str
    known: list[str]
    route: dict[str, Any]
    steps: list[dict[str, Any]]
    fetch_error: str


class LearningIntent(BaseModel):
    technology: str = Field(default="", description="用户想学的目标技术名")
    known_technologies: list[str] = Field(
        default_factory=list, description="用户明确说已经掌握的技术"
    )


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


def build_learning_graph(
    intent_llm: BaseChatModel,
    answer_llm: BaseChatModel,
    client: InternalClient,
):
    async def intent(state: LearningState) -> dict[str, Any]:
        technology = (state.get("technology") or "").strip()
        known = [name.strip() for name in (state.get("known") or []) if name and name.strip()]
        if technology:
            return {"technology": technology, "known": known}

        try:
            result = await invoke_structured(intent_llm, LearningIntent, state["messages"])
        except Exception:
            log.warning("学习意图识别失败", exc_info=True)
            return {
                "technology": "",
                "known": known,
                "fetch_error": "未能识别出您想学的技术，请在页面上选择技术或说明技术名。",
            }
        return {
            "technology": (result.technology or "").strip(),
            "known": known or [
                name.strip() for name in result.known_technologies if name and name.strip()
            ],
        }

    async def fetch(state: LearningState) -> dict[str, Any]:
        technology = (state.get("technology") or "").strip()
        if not technology:
            return {
                "fetch_error": state.get("fetch_error") or "未能识别出您想学的技术，请在页面上选择技术或说明技术名。",
                "route": {},
                "steps": [],
            }

        steps: list[dict[str, Any]] = [
            {"tool": PREREQUISITE_TOOL_NAME, "args": {"technology": technology}}
        ]
        try:
            tree = await query_prerequisite_chain(client, technology)
        except ToolException as exc:
            return {
                "fetch_error": str(exc),
                "route": {},
                "steps": steps,
            }

        known_names = [name.strip() for name in (state.get("known") or []) if name.strip()]
        known_keys = {name.casefold() for name in known_names}
        route = _build_route(tree, known_names, known_keys)
        return {"route": route, "steps": steps}

    async def agent(state: LearningState) -> dict[str, Any]:
        if state.get("fetch_error"):
            return {
                "messages": [AIMessage(
                    content=f"{state['fetch_error']}\n\n请稍后重试或尝试换一个技术名。"
                )]
            }

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
    builder.add_node("intent", intent)
    builder.add_node("fetch", fetch)
    builder.add_node("agent", agent)
    builder.add_edge(START, "intent")
    builder.add_edge("intent", "fetch")
    builder.add_edge("fetch", "agent")
    builder.add_edge("agent", END)
    return builder.compile()
