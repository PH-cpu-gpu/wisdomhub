import asyncio
from typing import Any, cast

import aio_pika
import pytest
from aio_pika.abc import AbstractIncomingMessage

from app.infra.mq.consumer import Consumer, ConsumeResult
from app.infra.mq.topology import ANALYZE_QUEUE
from tests.infra.mq.fakes import FakeChannel, FakeConnection, FakeMessage

URL = "amqp://guest:guest@127.0.0.1/"


async def _noop(_: AbstractIncomingMessage) -> ConsumeResult:
    return ConsumeResult.ACK


async def test_ack_mapping() -> None:
    message = FakeMessage()
    consumer = Consumer(URL, ANALYZE_QUEUE, _noop)

    await consumer._on_message(cast(AbstractIncomingMessage, message))

    assert message.acked == 1
    assert message.nacked == []


async def test_requeue_mapping() -> None:
    async def handler(_: AbstractIncomingMessage) -> ConsumeResult:
        return ConsumeResult.REQUEUE

    message = FakeMessage()
    consumer = Consumer(URL, ANALYZE_QUEUE, handler)

    await consumer._on_message(cast(AbstractIncomingMessage, message))

    assert message.nacked == [True]


async def test_dlq_mapping() -> None:
    async def handler(_: AbstractIncomingMessage) -> ConsumeResult:
        return ConsumeResult.DLQ

    message = FakeMessage()
    consumer = Consumer(URL, ANALYZE_QUEUE, handler)

    await consumer._on_message(cast(AbstractIncomingMessage, message))

    assert message.nacked == [False]


async def test_handler_exception_goes_to_dlq() -> None:
    async def handler(_: AbstractIncomingMessage) -> ConsumeResult:
        raise RuntimeError("boom")

    message = FakeMessage()
    consumer = Consumer(URL, ANALYZE_QUEUE, handler)

    await consumer._on_message(cast(AbstractIncomingMessage, message))

    assert message.nacked == [False]


async def test_stop_is_idempotent() -> None:
    consumer = Consumer(URL, ANALYZE_QUEUE, _noop)

    await consumer.stop()
    await consumer.stop()


async def test_run_connects_consumes_and_stops(monkeypatch: pytest.MonkeyPatch) -> None:
    channel = FakeChannel()
    connection = FakeConnection(channel)
    connected: dict[str, Any] = {}

    async def fake_connect(url: str, *, reconnect_interval: float) -> FakeConnection:
        connected["url"] = url
        connected["reconnect_interval"] = reconnect_interval
        return connection

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect)

    consumer = Consumer(URL, ANALYZE_QUEUE, _noop, reconnect_interval=0.1)
    task = asyncio.create_task(consumer.run())
    for _ in range(50):
        queue = channel.queues.get(ANALYZE_QUEUE)
        if queue is not None and queue.callback is not None:
            break
        await asyncio.sleep(0)

    assert connected == {"url": URL, "reconnect_interval": 0.1}
    assert connection.publisher_confirms is True
    assert channel.qos == 1
    assert consumer.queue is channel.queues[ANALYZE_QUEUE]

    await consumer.stop()
    await asyncio.wait_for(task, timeout=1)

    assert connection.closed is True


async def test_initial_connection_failure_is_retried(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    channel = FakeChannel()
    connection = FakeConnection(channel)
    calls = 0

    async def fake_connect(url: str, *, reconnect_interval: float) -> FakeConnection:
        nonlocal calls
        calls += 1
        if calls == 1:
            raise ConnectionError("down")
        return connection

    monkeypatch.setattr(aio_pika, "connect_robust", fake_connect)

    consumer = Consumer(URL, ANALYZE_QUEUE, _noop, reconnect_interval=0.01)
    task = asyncio.create_task(consumer.run())
    for _ in range(100):
        queue = channel.queues.get(ANALYZE_QUEUE)
        if queue is not None and queue.callback is not None:
            break
        await asyncio.sleep(0.005)

    assert calls == 2
    await consumer.stop()
    await asyncio.wait_for(task, timeout=1)
