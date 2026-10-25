import logging
from collections.abc import Sequence
from dataclasses import dataclass, replace
from typing import Protocol

from agents.base import HistoryMessage
from app.config import Settings
from db.models import AgentMessage
from db.turn import TurnContext
from models.tokenizer import TokenCounter

log = logging.getLogger(__name__)

TRUNCATED_MARKER = "\n……（已截断）"


class Summarizer(Protocol):
    async def summarize(self, messages: list[HistoryMessage]) -> str | None: ...


class NoopSummarizer:
    async def summarize(self, messages: list[HistoryMessage]) -> str | None:
        return None


@dataclass(frozen=True, slots=True)
class PromptContext:
    turn: TurnContext
    history: tuple[HistoryMessage, ...]
    dropped_turns: int
    summary: str | None
    history_tokens: int


class ContextAssembler:
    def __init__(
        self,
        settings: Settings,
        counter: TokenCounter,
        summarizer: Summarizer | None = None,
    ) -> None:
        self._settings = settings
        self._counter = counter
        self._summarizer = summarizer or NoopSummarizer()

    async def assemble(self, turn: TurnContext, rows: Sequence[AgentMessage]) -> PromptContext:
        grouped = self._group_turns(rows)
        prepared = [[self._truncate_message(message) for message in members] for members in grouped]

        dropped_messages: list[HistoryMessage] = []
        dropped_turns = 0
        max_turns = max(1, self._settings.context_max_turns)
        while len(prepared) > 1 and (len(prepared) > max_turns or self._tokens(prepared) > self._settings.context_max_tokens):
            dropped_messages.extend(grouped.pop(0))
            prepared.pop(0)
            dropped_turns += 1

        if prepared and self._tokens(prepared) > self._settings.context_max_tokens:
            fitted = self._fit_turn(prepared[0], self._settings.context_max_tokens)
            if fitted:
                prepared[0] = fitted
            else:
                dropped_messages.extend(grouped.pop(0))
                prepared.pop(0)
                dropped_turns += 1

        kept = tuple(message for members in prepared for message in members)
        summary = await self._summarizer.summarize(dropped_messages) if dropped_messages else None
        return PromptContext(
            turn=turn,
            history=kept,
            dropped_turns=dropped_turns,
            summary=summary,
            history_tokens=self._tokens(prepared),
        )

    @staticmethod
    def _group_turns(rows: Sequence[AgentMessage]) -> list[list[HistoryMessage]]:
        turns: list[list[HistoryMessage]] = []
        current_message_id: str | None = None
        for row in rows:
            if row.message_id != current_message_id:
                turns.append([])
                current_message_id = row.message_id
            turns[-1].append(
                HistoryMessage(
                    role=row.role,
                    content=row.content,
                )
            )
        return turns

    def _tokens(self, turns: Sequence[Sequence[HistoryMessage]]) -> int:
        return sum(
            self._counter.count_message(message.content)
            for members in turns
            for message in members
        )

    def _truncate_message(self, message: HistoryMessage) -> HistoryMessage:
        content = self._truncate_to(message.content, self._settings.context_message_max_tokens)
        return message if content == message.content else replace(message, content=content)

    def _fit_turn(self, messages: list[HistoryMessage], budget: int) -> list[HistoryMessage]:
        remaining = budget
        fitted: list[HistoryMessage] = []
        for message in messages:
            if remaining <= 0:
                break
            overhead = self._counter.count_message("") - self._counter.count("")
            content = self._truncate_to(message.content, max(0, remaining - overhead))
            if not content:
                break
            fitted.append(replace(message, content=content))
            remaining -= self._counter.count_message(content)
        return fitted

    def _truncate_to(self, text: str, budget: int) -> str:
        if self._counter.count(text) <= budget:
            return text
        marker_tokens = self._counter.count(TRUNCATED_MARKER)
        if budget <= marker_tokens:
            return ""
        target = budget - marker_tokens
        low, high = 0, len(text)
        while low < high:
            mid = (low + high + 1) // 2
            if self._counter.count(text[:mid]) <= target:
                low = mid
            else:
                high = mid - 1
        return text[:low].rstrip() + TRUNCATED_MARKER
