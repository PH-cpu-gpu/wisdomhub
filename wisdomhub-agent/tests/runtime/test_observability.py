"""观测：token、耗时与 trace。"""

import asyncio
import logging

import pytest
from langchain_core.messages import AIMessage, AIMessageChunk

from app.core.logging import get_trace_id, trace_context
from app.runtime.observability import Observation, Usage, observe_task, observe_turn


def _usage(input_tokens: int = 1, output_tokens: int = 2) -> Usage:
    return Usage(input_tokens, output_tokens, input_tokens + output_tokens)


def test_usage_from_message() -> None:
    message = AIMessage(
        content="",
        usage_metadata={"input_tokens": 10, "output_tokens": 5, "total_tokens": 15},
    )

    assert Usage.from_message(message) == Usage(10, 5, 15)


def test_usage_from_chunk() -> None:
    chunk = AIMessageChunk(
        content="",
        usage_metadata={"input_tokens": 3, "output_tokens": 4, "total_tokens": 7},
    )

    assert Usage.from_message(chunk) == Usage(3, 4, 7)


def test_usage_from_message_without_usage() -> None:
    assert Usage.from_message(None) is None
    assert Usage.from_message(AIMessage(content="hi")) is None

    empty = AIMessage(
        content="",
        usage_metadata={"input_tokens": 0, "output_tokens": 0, "total_tokens": 0},
    )
    assert Usage.from_message(empty) is None


def test_add_usage_accumulates_and_counts_calls() -> None:
    observation = Observation(trace_id="t", started_at=0.0)

    observation.add_usage(_usage(1, 2))
    observation.add_usage(_usage(3, 4))
    observation.add_usage(None)

    assert observation.usage == Usage(4, 6, 10)
    assert observation.llm_calls == 2


async def test_observe_turn_logs_done(caplog: pytest.LogCaptureFixture) -> None:
    with caplog.at_level(logging.INFO, logger="app.runtime.observability"):
        async with observe_turn(conversation_id="c1", message_id="m1", workflow=1) as observation:
            observation.add_usage(_usage(10, 5))

    record = caplog.records[-1]
    assert record.__dict__["event"] == "turn.done"
    assert record.__dict__["conversation_id"] == "c1"
    assert record.__dict__["message_id"] == "m1"
    assert record.__dict__["workflow"] == 1
    assert record.__dict__["input_tokens"] == 10
    assert record.__dict__["output_tokens"] == 5
    assert record.__dict__["total_tokens"] == 15
    assert record.__dict__["llm_calls"] == 1
    assert record.__dict__["trace_id"] == observation.trace_id
    assert record.__dict__["duration_ms"] >= 0


async def test_observe_turn_logs_error(caplog: pytest.LogCaptureFixture) -> None:
    with (
        caplog.at_level(logging.ERROR, logger="app.runtime.observability"),
        pytest.raises(RuntimeError, match="boom"),
    ):
        async with observe_turn(conversation_id="c", message_id="m", workflow=1):
            raise RuntimeError("boom")

    record = caplog.records[-1]
    assert record.__dict__["event"] == "turn.error"
    assert record.levelname == "ERROR"
    assert record.exc_info is not None


async def test_observe_turn_logs_cancelled(caplog: pytest.LogCaptureFixture) -> None:
    with (
        caplog.at_level(logging.INFO, logger="app.runtime.observability"),
        pytest.raises(asyncio.CancelledError),
    ):
        async with observe_turn(conversation_id="c", message_id="m", workflow=1):
            raise asyncio.CancelledError

    record = caplog.records[-1]
    assert record.__dict__["event"] == "turn.cancelled"
    assert record.levelname == "INFO"


async def test_existing_trace_is_reused() -> None:
    with trace_context("t1"):
        async with observe_turn(conversation_id="c", message_id="m", workflow=1) as observation:
            assert observation.trace_id == "t1"
            assert get_trace_id() == "t1"

        assert get_trace_id() == "t1"


async def test_task_generates_trace_and_restores() -> None:
    assert get_trace_id() is None

    async with observe_task("analysis", article_id="a1") as observation:
        assert get_trace_id() == observation.trace_id
        assert len(observation.trace_id) == 32

    assert get_trace_id() is None


async def test_observe_task_logs_fields(caplog: pytest.LogCaptureFixture) -> None:
    with caplog.at_level(logging.INFO, logger="app.runtime.observability"):
        async with observe_task("analysis", article_id="a1") as observation:
            observation.add_usage(Usage(2, 3, 5))

    record = caplog.records[-1]
    assert record.__dict__["event"] == "task.done"
    assert record.__dict__["task"] == "analysis"
    assert record.__dict__["article_id"] == "a1"
    assert record.__dict__["total_tokens"] == 5
