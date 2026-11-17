"""SSE 守卫：转发、空闲超时、异常 EOF、生产者异常与断开清理。"""

import asyncio
from collections.abc import AsyncIterator, Sequence

import pytest
from fastapi.sse import ServerSentEvent
from openai import OpenAIError

from app.chat.api import guard_events
from app.contracts.sse import DeltaEvent, DoneEvent, ErrorEvent, MetaEvent, SseEvent
from app.contracts.workflow import WorkflowKind


def _meta() -> MetaEvent:
    return MetaEvent(
        conversation_id="c-1",
        message_id="m-1",
        workflow=WorkflowKind.QA,
    )


def _source(
    events: Sequence[SseEvent],
    *,
    delay: float = 0.0,
    produced: list[str] | None = None,
) -> AsyncIterator[SseEvent]:
    async def iterator() -> AsyncIterator[SseEvent]:
        for event in events:
            if produced is not None:
                produced.append(event.EVENT)
            yield event
            if delay:
                await asyncio.sleep(delay)

    return iterator()


async def _collect(stream: AsyncIterator[ServerSentEvent]) -> list[ServerSentEvent]:
    return [event async for event in stream]


def _raw(event: ServerSentEvent) -> str:
    assert event.raw_data is not None
    return event.raw_data


async def test_forwards_events_and_stops_after_terminal() -> None:
    produced: list[str] = []
    source = _source(
        [_meta(), DeltaEvent(text="你好"), DoneEvent(), DeltaEvent(text="多余")],
        produced=produced,
    )

    events = await _collect(guard_events(source, idle_timeout=1.0))

    assert [event.event for event in events] == ["meta", "delta", "done"]
    assert produced == ["meta", "delta", "done"]


async def test_error_terminal_is_last() -> None:
    produced: list[str] = []
    source = _source(
        [_meta(), ErrorEvent(code=409, message="冲突"), DeltaEvent(text="多余")],
        produced=produced,
    )

    events = await _collect(guard_events(source, idle_timeout=1.0))

    assert [event.event for event in events] == ["meta", "error"]
    assert produced == ["meta", "error"]


async def test_slow_chunks_within_idle_timeout_all_pass() -> None:
    source = _source(
        [_meta(), DeltaEvent(text="a"), DeltaEvent(text="b"), DoneEvent()],
        delay=0.02,
    )

    events = await _collect(guard_events(source, idle_timeout=0.5))

    assert [event.event for event in events] == ["meta", "delta", "delta", "done"]


async def test_idle_timeout_emits_error_and_closes_producer() -> None:
    closed = False

    async def source() -> AsyncIterator[SseEvent]:
        nonlocal closed
        try:
            yield _meta()
            await asyncio.Event().wait()
        finally:
            closed = True

    events = await _collect(guard_events(source(), idle_timeout=0.05))

    assert [event.event for event in events] == ["meta", "error"]
    assert '"code":503' in _raw(events[1])
    assert "AI 服务响应超时" in _raw(events[1])
    assert closed is True


async def test_missing_terminal_sends_error() -> None:
    source = _source([_meta()])

    events = await _collect(guard_events(source, idle_timeout=1.0))

    assert [event.event for event in events] == ["meta", "error"]
    assert "AI 服务内部异常" in _raw(events[1])


@pytest.mark.parametrize(
    ("error", "code", "message"),
    [
        (OpenAIError("boom"), 503, "AI 服务暂不可用"),
        (RuntimeError("boom"), 500, "AI 服务内部异常"),
    ],
)
async def test_producer_error_maps_to_terminal_error(
    error: Exception,
    code: int,
    message: str,
) -> None:
    async def source() -> AsyncIterator[SseEvent]:
        raise error
        yield _meta()

    events = await _collect(guard_events(source(), idle_timeout=1.0))

    assert [event.event for event in events] == ["error"]
    assert f'"code":{code}' in _raw(events[0])
    assert message in _raw(events[0])


async def test_consumer_close_stops_producer() -> None:
    closed = False

    async def source() -> AsyncIterator[SseEvent]:
        nonlocal closed
        try:
            yield _meta()
            await asyncio.Event().wait()
        finally:
            closed = True

    stream = guard_events(source(), idle_timeout=10.0)
    first = await anext(stream)
    assert first.event == "meta"

    await stream.aclose()

    assert closed is True


async def test_rejects_non_positive_idle_timeout() -> None:
    with pytest.raises(ValueError):
        await _collect(guard_events(_source([_meta()]), idle_timeout=0))
