from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from typing import Any, Protocol

from api.sse import StreamEvent


@dataclass(frozen=True, slots=True)
class HistoryMessage:
    role: str
    content: str


@dataclass(frozen=True, slots=True)
class ChatContext:
    user_id: int
    conversation_id: str
    message_id: str
    question: str
    history: tuple[HistoryMessage, ...] = ()
    dropped_turns: int = 0
    summary: str | None = None


@dataclass(slots=True)
class TurnTrace:
    """一轮问答的工作流轨迹；断连时也保存已产生的部分。"""

    steps: list[dict[str, Any]] = field(default_factory=list)


class ChatStreamer(Protocol):
    """一次问答的事件来源。"""

    def stream(self, ctx: ChatContext) -> AsyncIterator[StreamEvent]: ...
