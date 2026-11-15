"""上下文装配：轮数与 token 双预算、丢弃/摘要与省略提示。"""

import logging
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol

from langchain_core.messages import AIMessage, BaseMessage, HumanMessage

from app.features.conversation.models import AgentMessage
from app.runtime.tokenizer import count_tokens, truncate_text

log = logging.getLogger(__name__)

MESSAGE_TOKEN_OVERHEAD = 4


@dataclass(frozen=True, slots=True)
class PromptContext:
    history: list[BaseMessage]
    question: str
    dropped_turns: int
    summary: str | None
    tokens: int

    @property
    def notice(self) -> str | None:
        """有摘要返回摘要，否则有丢弃返回省略提示。"""
        if self.summary:
            return self.summary
        if self.dropped_turns:
            return f"（已省略 {self.dropped_turns} 轮更早的对话）"
        return None


class Summarizer(Protocol):
    async def summarize(self, messages: Sequence[BaseMessage]) -> str | None: ...


class NoopSummarizer:
    async def summarize(self, messages: Sequence[BaseMessage]) -> str | None:
        return None


@dataclass(slots=True)
class _Message:
    role: str
    text: str
    tokens: int


@dataclass(slots=True)
class _Turn:
    messages: list[_Message]
    tokens: int


async def build_context(
    history: Sequence[AgentMessage],
    question: str,
    *,
    max_turns: int,
    max_tokens: int,
    message_max_tokens: int,
    summarizer: Summarizer | None = None,
) -> PromptContext:
    """history 需按 seq 升序；question 原样保留；最少保留最新一轮，超预算也保留。"""
    _validate(max_turns, max_tokens, message_max_tokens)

    turns = _group_turns(history, message_max_tokens)
    total = sum(turn.tokens for turn in turns)
    dropped: list[_Turn] = []

    while len(turns) > 1 and (len(turns) > max_turns or total > max_tokens):
        removed = turns.pop(0)
        total -= removed.tokens
        dropped.append(removed)

    summary = await _summarize(dropped, summarizer)
    return PromptContext(
        history=_to_messages(turns),
        question=question,
        dropped_turns=len(dropped),
        summary=summary,
        tokens=total,
    )


def _group_turns(history: Sequence[AgentMessage], message_max_tokens: int) -> list[_Turn]:
    turns: list[_Turn] = []
    positions: dict[str, int] = {}

    for message in history:
        if message.role == "assistant" and message.status != "done":
            continue
        if message.role not in ("user", "assistant"):
            continue

        text = truncate_text(message.content, message_max_tokens)
        entry = _Message(message.role, text, _message_tokens(text))
        position = positions.get(message.message_id)
        if position is None:
            positions[message.message_id] = len(turns)
            turns.append(_Turn([entry], entry.tokens))
        else:
            turns[position].messages.append(entry)
            turns[position].tokens += entry.tokens

    return turns


async def _summarize(dropped: Sequence[_Turn], summarizer: Summarizer | None) -> str | None:
    if not dropped:
        return None
    impl = summarizer or NoopSummarizer()
    try:
        value = await impl.summarize(_to_messages(dropped))
    except Exception:
        log.warning("历史摘要失败，降级为省略提示", exc_info=True)
        return None
    if value and value.strip():
        return value.strip()
    return None


def _to_messages(turns: Sequence[_Turn]) -> list[BaseMessage]:
    messages: list[BaseMessage] = []
    for turn in turns:
        for message in turn.messages:
            if message.role == "user":
                messages.append(HumanMessage(content=message.text))
            else:
                messages.append(AIMessage(content=message.text))
    return messages


def _message_tokens(text: str) -> int:
    return count_tokens(text) + MESSAGE_TOKEN_OVERHEAD


def _validate(max_turns: int, max_tokens: int, message_max_tokens: int) -> None:
    for name, value in (
        ("max_turns", max_turns),
        ("max_tokens", max_tokens),
        ("message_max_tokens", message_max_tokens),
    ):
        if value < 1:
            raise ValueError(f"{name} 必须大于 0")
