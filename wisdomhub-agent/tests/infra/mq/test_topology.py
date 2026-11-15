from typing import Any

from aio_pika import ExchangeType

from app.infra.mq.topology import (
    AI_COMPLETED,
    ANALYZE_DLQ,
    ANALYZE_QUEUE,
    ARTICLE_CREATED,
    ARTICLE_UPDATED,
    DLX_EXCHANGE,
    EVENT_EXCHANGE,
    KNOWLEDGE_EXTRACT,
    declare_topology,
)
from tests.infra.mq.fakes import FakeChannel


def test_publish_routing_keys() -> None:
    assert KNOWLEDGE_EXTRACT == "knowledge.extract"
    assert AI_COMPLETED == "ai.completed"


async def test_declare_topology() -> None:
    channel: Any = FakeChannel()

    topology = await declare_topology(channel)

    events = channel.exchanges[EVENT_EXCHANGE]
    assert events.exchange_type == ExchangeType.TOPIC
    assert events.durable is True

    dead_letter = channel.exchanges[DLX_EXCHANGE]
    assert dead_letter.exchange_type == ExchangeType.DIRECT
    assert dead_letter.durable is True

    analyze = channel.queues[ANALYZE_QUEUE]
    assert analyze.durable is True
    assert analyze.arguments == {
        "x-dead-letter-exchange": DLX_EXCHANGE,
        "x-dead-letter-routing-key": ANALYZE_DLQ,
    }
    assert analyze.bindings == [
        (EVENT_EXCHANGE, ARTICLE_CREATED),
        (EVENT_EXCHANGE, ARTICLE_UPDATED),
    ]

    dlq = channel.queues[ANALYZE_DLQ]
    assert dlq.durable is True
    assert dlq.bindings == [(DLX_EXCHANGE, ANALYZE_DLQ)]

    assert topology.events is events
    assert topology.queues[ANALYZE_QUEUE] is analyze
