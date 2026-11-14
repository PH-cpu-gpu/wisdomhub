"""测试替身：LLM、内部 HTTP 客户端与 MQ 发布器。"""

from collections.abc import AsyncIterator, Mapping, Sequence
from dataclasses import dataclass
from typing import Any, Self

from langchain_core.messages import AIMessage, AIMessageChunk, BaseMessage


class FakeLLM:
    """按脚本依次返回固定回答；脚本用完后重复最后一个。"""

    def __init__(self, responses: Sequence[str | AIMessage], *, chunk_size: int = 4) -> None:
        if not responses:
            raise ValueError("responses 不能为空")
        self.responses = list(responses)
        self.chunk_size = max(1, chunk_size)
        self.calls: list[list[BaseMessage]] = []
        self.stream_calls: list[list[BaseMessage]] = []
        self.bound_tools: list[Any] = []
        self._index = 0

    def bind_tools(self, tools: Sequence[Any], **_: Any) -> Self:
        self.bound_tools.extend(tools)
        return self

    async def ainvoke(
        self, messages: Sequence[BaseMessage], config: Any = None, **kwargs: Any
    ) -> AIMessage:
        self.calls.append(list(messages))
        return self._next()

    async def astream(
        self, messages: Sequence[BaseMessage], config: Any = None, **kwargs: Any
    ) -> AsyncIterator[AIMessageChunk]:
        self.stream_calls.append(list(messages))
        content = self._next().content
        if not isinstance(content, str):
            yield AIMessageChunk(content=content)
            return
        for start in range(0, len(content), self.chunk_size):
            yield AIMessageChunk(content=content[start : start + self.chunk_size])

    def _next(self) -> AIMessage:
        value = self.responses[min(self._index, len(self.responses) - 1)]
        self._index += 1
        return value if isinstance(value, AIMessage) else AIMessage(content=value)


@dataclass(frozen=True, slots=True)
class InternalCall:
    method: str
    path: str
    params: dict[str, Any]
    json: Any


class FakeInternalClient:
    """按路径返回预设响应；预设值可以是异常。"""

    def __init__(
        self, responses: Mapping[str, Any] | None = None, *, error: Exception | None = None
    ) -> None:
        self.responses = dict(responses or {})
        self.error = error
        self.calls: list[InternalCall] = []
        self.closed = False

    async def get(self, path: str, *, params: Mapping[str, Any] | None = None) -> Any:
        return self._handle("GET", path, params or {}, None)

    async def post(self, path: str, *, json: Any = None) -> Any:
        return self._handle("POST", path, {}, json)

    async def aclose(self) -> None:
        self.closed = True

    def _handle(self, method: str, path: str, params: Mapping[str, Any], json: Any) -> Any:
        self.calls.append(InternalCall(method, path, dict(params), json))
        if self.error is not None:
            raise self.error
        if path not in self.responses:
            raise KeyError(f"未配置响应: {path}")
        value = self.responses[path]
        if isinstance(value, Exception):
            raise value
        return value


@dataclass(frozen=True, slots=True)
class PublishedMessage:
    routing_key: str
    payload: dict[str, Any]


class FakePublisher:
    """记录发布内容；可注入发布异常。"""

    def __init__(self, *, error: Exception | None = None) -> None:
        self.error = error
        self.messages: list[PublishedMessage] = []
        self.closed = False

    async def publish(self, routing_key: str, payload: Mapping[str, Any]) -> None:
        if self.error is not None:
            raise self.error
        self.messages.append(PublishedMessage(routing_key, dict(payload)))

    async def aclose(self) -> None:
        self.closed = True


__all__ = [
    "FakeInternalClient",
    "FakeLLM",
    "FakePublisher",
    "InternalCall",
    "PublishedMessage",
]
