from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Protocol

from api.sse import StreamEvent


@dataclass(frozen=True, slots=True)
class ChatContext:
    user_id: int
    conversation_id: str
    message_id: str
    question: str


class ChatStreamer(Protocol):
    """TODO 一次问答的事件来源；B3.18接真实工作流时只替换实现。"""

    def stream(self, ctx: ChatContext) -> AsyncIterator[StreamEvent]: ...
