"""上下文装配：双预算、截断、摘要与省略提示。"""

from collections.abc import Sequence
from uuid import UUID, uuid4

import pytest
from langchain_core.messages import BaseMessage

from app.features.conversation.models import AgentMessage
from app.runtime.context import MESSAGE_TOKEN_OVERHEAD, build_context
from app.runtime.tokenizer import DEFAULT_MARKER, count_tokens


class _FakeSummarizer:
    def __init__(self, value: str | None = None, error: Exception | None = None) -> None:
        self.value = value
        self.error = error
        self.calls: list[list[BaseMessage]] = []

    async def summarize(self, messages: Sequence[BaseMessage]) -> str | None:
        self.calls.append(list(messages))
        if self.error is not None:
            raise self.error
        return self.value


def _msg(seq: int, message_id: str, role: str, content: str, status: str = "done") -> AgentMessage:
    return AgentMessage(
        id=uuid4(),
        conversation_id=UUID(int=0),
        message_id=message_id,
        user_id=1,
        role=role,
        content=content,
        status=status,
        seq=seq,
    )


def _turn(
    seq: int, message_id: str, question: str, answer: str | None = None
) -> list[AgentMessage]:
    rows = [_msg(seq, message_id, "user", question)]
    if answer is not None:
        rows.append(_msg(seq + 1, message_id, "assistant", answer))
    return rows


def _content(message: BaseMessage) -> str:
    content = message.content
    assert isinstance(content, str)
    return content


async def test_empty_history() -> None:
    ctx = await build_context([], "现在问题", max_turns=20, max_tokens=1000, message_max_tokens=100)

    assert ctx.history == []
    assert ctx.question == "现在问题"
    assert ctx.dropped_turns == 0
    assert ctx.summary is None
    assert ctx.tokens == 0
    assert ctx.notice is None


async def test_keeps_all_turns_and_counts_tokens() -> None:
    history = [*_turn(1, "m1", "q1", "a1"), *_turn(3, "m2", "q2", "a2")]

    ctx = await build_context(history, "q3", max_turns=20, max_tokens=1000, message_max_tokens=100)

    assert [(message.type, message.content) for message in ctx.history] == [
        ("human", "q1"),
        ("ai", "a1"),
        ("human", "q2"),
        ("ai", "a2"),
    ]
    assert ctx.tokens == sum(
        count_tokens(text) + MESSAGE_TOKEN_OVERHEAD for text in ("q1", "a1", "q2", "a2")
    )


async def test_drops_oldest_turn_by_count() -> None:
    history = [
        *_turn(1, "m1", "q1", "a1"),
        *_turn(3, "m2", "q2", "a2"),
        *_turn(5, "m3", "q3", "a3"),
    ]

    ctx = await build_context(history, "q4", max_turns=2, max_tokens=10000, message_max_tokens=100)

    assert [message.content for message in ctx.history] == ["q2", "a2", "q3", "a3"]
    assert ctx.dropped_turns == 1
    assert ctx.notice == "（已省略 1 轮更早的对话）"


async def test_drops_oldest_turns_until_single_turn_remains() -> None:
    history = [
        *_turn(1, "m1", "q1", "x " * 200),
        *_turn(3, "m2", "q2", "y " * 200),
        *_turn(5, "m3", "q3", "z " * 200),
    ]

    ctx = await build_context(history, "q4", max_turns=20, max_tokens=100, message_max_tokens=1000)

    assert ctx.dropped_turns == 2
    assert [message.content for message in ctx.history] == ["q3", "z " * 200]
    assert ctx.tokens > 100


async def test_truncates_each_message_to_message_budget() -> None:
    long_answer = "word " * 200

    ctx = await build_context(
        _turn(1, "m1", "q1", long_answer),
        "q2",
        max_turns=20,
        max_tokens=10000,
        message_max_tokens=10,
    )

    assert _content(ctx.history[1]).endswith(DEFAULT_MARKER)
    assert ctx.history[1].content != long_answer


async def test_latest_turn_over_budget_is_kept_as_is() -> None:
    question = "word " * 100
    answer = "word " * 200

    ctx = await build_context(
        _turn(1, "m1", question, answer),
        "next",
        max_turns=20,
        max_tokens=10,
        message_max_tokens=1000,
    )

    assert [message.content for message in ctx.history] == [question, answer]
    assert ctx.tokens > 10


async def test_current_question_is_never_truncated() -> None:
    question = "word " * 1000

    ctx = await build_context([], question, max_turns=20, max_tokens=10, message_max_tokens=10)

    assert ctx.question == question


async def test_skips_unfinished_assistant_but_keeps_user() -> None:
    history = [
        _msg(1, "m1", "user", "q1"),
        _msg(2, "m1", "assistant", "partial", status="aborted"),
        _msg(3, "m2", "user", "q2"),
        _msg(4, "m2", "assistant", "a2"),
    ]

    ctx = await build_context(history, "q3", max_turns=20, max_tokens=1000, message_max_tokens=100)

    assert [message.content for message in ctx.history] == ["q1", "q2", "a2"]


async def test_summarizer_called_only_when_turns_dropped() -> None:
    summarizer = _FakeSummarizer("早期摘要")
    history = [*_turn(1, "m1", "q1", "a1"), *_turn(3, "m2", "q2", "a2")]

    ctx = await build_context(
        history,
        "q3",
        max_turns=1,
        max_tokens=1000,
        message_max_tokens=100,
        summarizer=summarizer,
    )

    assert ctx.dropped_turns == 1
    assert ctx.summary == "早期摘要"
    assert ctx.notice == "早期摘要"
    assert len(summarizer.calls) == 1
    assert [message.content for message in summarizer.calls[0]] == ["q1", "a1"]


async def test_summarizer_skipped_without_drops() -> None:
    summarizer = _FakeSummarizer("早期摘要")

    ctx = await build_context(
        _turn(1, "m1", "q1", "a1"),
        "q2",
        max_turns=20,
        max_tokens=1000,
        message_max_tokens=100,
        summarizer=summarizer,
    )

    assert summarizer.calls == []
    assert ctx.summary is None
    assert ctx.notice is None


async def test_summarizer_error_falls_back_to_notice() -> None:
    summarizer = _FakeSummarizer(error=RuntimeError("boom"))
    history = [*_turn(1, "m1", "q1", "a1"), *_turn(3, "m2", "q2", "a2")]

    ctx = await build_context(
        history,
        "q3",
        max_turns=1,
        max_tokens=1000,
        message_max_tokens=100,
        summarizer=summarizer,
    )

    assert ctx.summary is None
    assert ctx.notice == "（已省略 1 轮更早的对话）"


async def test_output_is_stable() -> None:
    history = [
        *_turn(1, "m1", "q1", "x " * 50),
        *_turn(3, "m2", "q2", "y " * 50),
        *_turn(5, "m3", "q3", "z " * 50),
    ]
    first = await build_context(history, "q4", max_turns=2, max_tokens=120, message_max_tokens=100)
    second = await build_context(history, "q4", max_turns=2, max_tokens=120, message_max_tokens=100)

    assert first == second


async def test_invalid_budgets_raise() -> None:
    with pytest.raises(ValueError):
        await build_context([], "q", max_turns=0, max_tokens=100, message_max_tokens=100)
    with pytest.raises(ValueError):
        await build_context([], "q", max_turns=1, max_tokens=0, message_max_tokens=100)
    with pytest.raises(ValueError):
        await build_context([], "q", max_turns=1, max_tokens=100, message_max_tokens=0)
