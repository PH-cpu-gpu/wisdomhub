"""观测：turn/task 的耗时、token 用量与 trace。"""

import asyncio
import logging
from collections.abc import AsyncIterator, Mapping
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from time import perf_counter
from typing import Any

from langchain_core.messages import BaseMessage

from app.core.logging import get_trace_id, trace_context

log = logging.getLogger(__name__)


@dataclass(slots=True)
class Usage:
    input_tokens: int = 0
    output_tokens: int = 0
    total_tokens: int = 0

    @classmethod
    def from_message(cls, message: BaseMessage | None) -> "Usage | None":
        """读取 LangChain 的 usage_metadata；没有用量返回 None。"""
        if message is None:
            return None
        data = getattr(message, "usage_metadata", None)
        if not isinstance(data, Mapping):
            return None
        input_tokens = int(data.get("input_tokens") or 0)
        output_tokens = int(data.get("output_tokens") or 0)
        total_tokens = int(data.get("total_tokens") or input_tokens + output_tokens)
        if not (input_tokens or output_tokens or total_tokens):
            return None
        return cls(input_tokens, output_tokens, total_tokens)


@dataclass(slots=True)
class Observation:
    trace_id: str
    started_at: float
    usage: Usage = field(default_factory=Usage)
    llm_calls: int = 0

    def add_usage(self, usage: Usage | None) -> None:
        """每个 LLM 响应调用一次；None 忽略。"""
        if usage is None:
            return
        self.usage.input_tokens += usage.input_tokens
        self.usage.output_tokens += usage.output_tokens
        self.usage.total_tokens += usage.total_tokens
        self.llm_calls += 1

    @property
    def duration_ms(self) -> int:
        return int((perf_counter() - self.started_at) * 1000)

    def _emit(self, event: str, fields: Mapping[str, Any], *, error: bool = False) -> None:
        payload = {
            "event": event,
            "trace_id": self.trace_id,
            "duration_ms": self.duration_ms,
            "input_tokens": self.usage.input_tokens,
            "output_tokens": self.usage.output_tokens,
            "total_tokens": self.usage.total_tokens,
            "llm_calls": self.llm_calls,
            **fields,
        }
        if error:
            log.error(event, extra=payload, exc_info=True)
        else:
            log.info(event, extra=payload)


@asynccontextmanager
async def observe_turn(
    *, conversation_id: str, message_id: str, workflow: int
) -> AsyncIterator[Observation]:
    fields = {
        "conversation_id": conversation_id,
        "message_id": message_id,
        "workflow": int(workflow),
    }
    async with _observe("turn", fields) as observation:
        yield observation


@asynccontextmanager
async def observe_task(task: str, **fields: Any) -> AsyncIterator[Observation]:
    async with _observe("task", {"task": task, **fields}) as observation:
        yield observation


@asynccontextmanager
async def _observe(kind: str, fields: Mapping[str, Any]) -> AsyncIterator[Observation]:
    with trace_context(get_trace_id()) as trace_id:
        observation = Observation(trace_id=trace_id, started_at=perf_counter())
        try:
            yield observation
        except asyncio.CancelledError:
            observation._emit(f"{kind}.cancelled", fields)
            raise
        except Exception:
            observation._emit(f"{kind}.error", fields, error=True)
            raise
        else:
            observation._emit(f"{kind}.done", fields)
