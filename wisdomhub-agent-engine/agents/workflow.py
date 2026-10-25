import asyncio
import contextlib
import json
import logging
from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass
from typing import Any

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
)
from langchain_core.prompts import ChatPromptTemplate

from agents.base import ChatContext, TurnTrace
from api.sse import StreamEvent, delta_event, done_event, sources_event
from graphs.qa import RECURSION_LIMIT
from prompts.base import build_chat_prompt
from prompts.qa import QA_SYSTEM_PROMPT
from tools.java_api import SEARCH_TOOL_NAME

log = logging.getLogger(__name__)

MAX_SOURCES = 10
FALLBACK_ANSWER = "抱歉，我暂时不能生成回答，请换个问法再试一次。"
ARTICLE_URL_TEMPLATE = "/article/{article_id}"

_PROMPT = build_chat_prompt(QA_SYSTEM_PROMPT)


@dataclass(frozen=True, slots=True)
class _Text:
    text: str


@dataclass(frozen=True, slots=True)
class _Done:
    state: dict[str, Any]


@dataclass(frozen=True, slots=True)
class _Failed:
    exc: Exception


def _initial_messages(ctx: ChatContext, prompt: ChatPromptTemplate) -> list[BaseMessage]:
    history: list[BaseMessage] = []
    if ctx.summary:
        history.append(SystemMessage(f"更早对话的摘要：{ctx.summary}"))
    elif ctx.dropped_turns > 0:
        history.append(SystemMessage(f"更早的 {ctx.dropped_turns} 轮对话已省略。"))
    for message in ctx.history:
        if message.role == "user":
            history.append(HumanMessage(message.content))
        elif message.role == "assistant" and message.content:
            history.append(AIMessage(message.content))
    return prompt.format_messages(history=history, question=ctx.question)


def _chunk_text(chunk: Any) -> str:
    content = getattr(chunk, "content", "")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "".join(
            block.get("text", "")
            for block in content
            if isinstance(block, dict) and block.get("type") in (None, "text")
        )
    return ""


def _extract_steps(messages: Sequence[BaseMessage]) -> list[dict[str, Any]]:
    steps: list[dict[str, Any]] = []
    for message in messages:
        if not isinstance(message, AIMessage):
            continue
        for call in message.tool_calls or ():
            steps.append({"tool": call.get("name", ""), "args": call.get("args", {})})
    return steps


def extract_sources(messages: Sequence[BaseMessage]) -> list[dict[str, Any]]:
    sources: list[dict[str, Any]] = []
    seen: set[str] = set()
    for message in messages:
        if not isinstance(message, ToolMessage):
            continue
        if message.name != SEARCH_TOOL_NAME or getattr(message, "status", None) == "error":
            continue
        content = message.content
        if not isinstance(content, str):
            continue
        try:
            items = json.loads(content)
        except (TypeError, ValueError):
            continue
        if not isinstance(items, list):
            continue
        for item in items:
            if not isinstance(item, dict):
                continue
            article_id = str(item.get("articleId") or "").strip()
            if not article_id or article_id in seen:
                continue
            seen.add(article_id)
            sources.append({
                "articleId": article_id,
                "title": str(item.get("title") or ""),
                "url": ARTICLE_URL_TEMPLATE.format(article_id=article_id),
            })
            if len(sources) >= MAX_SOURCES:
                return sources
    return sources


class WorkflowStreamer:
    """跑 QA 工作流，逐 token 转发，最后补 sources / done。"""

    def __init__(
        self,
        graph,
        *,
        trace: TurnTrace | None = None,
        prompt: ChatPromptTemplate | None = None,
    ) -> None:
        self._graph = graph
        self._trace = trace
        self._prompt = prompt or _PROMPT

    async def stream(self, ctx: ChatContext) -> AsyncIterator[StreamEvent]:
        queue: asyncio.Queue[_Text | _Done | _Failed] = asyncio.Queue()
        state = {
            "messages": _initial_messages(ctx, self._prompt),
            "conversation_id": ctx.conversation_id,
            "user_id": ctx.user_id,
            "tool_rounds": 0,
        }
        task = asyncio.create_task(self._produce(state, queue))
        parts: list[str] = []
        try:
            while True:
                item = await queue.get()
                if isinstance(item, _Text):
                    parts.append(item.text)
                    yield delta_event(item.text)
                elif isinstance(item, _Done):
                    if not "".join(parts).strip():
                        parts.append(FALLBACK_ANSWER)
                        yield delta_event(FALLBACK_ANSWER)
                    sources = extract_sources(item.state.get("messages", []))
                    log.info(
                        "问答完成 conversation=%s chars=%d sources=%d steps=%d",
                        ctx.conversation_id,
                        len("".join(parts)),
                        len(sources),
                        len(self._trace.steps) if self._trace else 0,
                    )
                    yield sources_event(sources)
                    yield done_event()
                    return
                else:
                    raise item.exc
        finally:
            if not task.done():
                task.cancel()
            with contextlib.suppress(asyncio.CancelledError, Exception):
                await task

    async def _produce(
        self,
        state: dict[str, Any],
        queue: asyncio.Queue[_Text | _Done | _Failed],
    ) -> None:
        final_state: dict[str, Any] | None = None
        tool_responses: set[str] = set()
        agen = None
        try:
            agen = self._graph.astream(
                state,
                config={"recursion_limit": RECURSION_LIMIT},
                stream_mode=["messages", "values"],
                version="v2",
            )
            async for part in agen:
                kind = part.get("type")
                if kind == "messages":
                    chunk, metadata = part["data"]
                    if metadata.get("langgraph_node") != "agent":
                        continue
                    run_id = getattr(chunk, "id", None)
                    if run_id and run_id in tool_responses:
                        continue
                    if getattr(chunk, "tool_call_chunks", None):
                        if run_id:
                            tool_responses.add(run_id)
                        continue
                    text = _chunk_text(chunk)
                    if text:
                        await queue.put(_Text(text))
                elif kind == "values":
                    final_state = part["data"]
                    if self._trace is not None:
                        self._trace.steps = _extract_steps(final_state.get("messages", []))
            if final_state is None:
                raise RuntimeError("工作流没有产出最终状态")
            await queue.put(_Done(final_state))
        except asyncio.CancelledError:
            raise
        except Exception as exc:
            log.warning("问答工作流失败: %s", type(exc).__name__)
            await queue.put(_Failed(exc))
        finally:
            if agen is not None:
                with contextlib.suppress(Exception):
                    await agen.aclose()
