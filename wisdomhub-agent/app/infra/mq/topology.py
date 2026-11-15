"""RabbitMQ 拓扑声明。"""

from collections.abc import Mapping
from dataclasses import dataclass

from aio_pika import ExchangeType
from aio_pika.abc import AbstractChannel, AbstractExchange, AbstractQueue

EVENT_EXCHANGE = "wisdomhub.event"
DLX_EXCHANGE = "wisdomhub.dlx"
ANALYZE_QUEUE = "agent.analyze.q"
ANALYZE_DLQ = "agent.analyze.q.dlq"
ARTICLE_CREATED = "article.created"
ARTICLE_UPDATED = "article.updated"
KNOWLEDGE_EXTRACT = "knowledge.extract"
AI_COMPLETED = "ai.completed"


@dataclass(frozen=True, slots=True)
class Topology:
    events: AbstractExchange
    queues: Mapping[str, AbstractQueue]


async def declare_topology(channel: AbstractChannel) -> Topology:
    events = await channel.declare_exchange(
        EVENT_EXCHANGE,
        ExchangeType.TOPIC,
        durable=True,
    )
    dead_letter = await channel.declare_exchange(
        DLX_EXCHANGE,
        ExchangeType.DIRECT,
        durable=True,
    )

    analyze_queue = await channel.declare_queue(
        ANALYZE_QUEUE,
        durable=True,
        arguments={
            "x-dead-letter-exchange": DLX_EXCHANGE,
            "x-dead-letter-routing-key": ANALYZE_DLQ,
        },
    )
    await analyze_queue.bind(events, ARTICLE_CREATED)
    await analyze_queue.bind(events, ARTICLE_UPDATED)

    dead_letter_queue = await channel.declare_queue(ANALYZE_DLQ, durable=True)
    await dead_letter_queue.bind(dead_letter, ANALYZE_DLQ)

    return Topology(
        events=events,
        queues={ANALYZE_QUEUE: analyze_queue},
    )
