import aio_pika
from aio_pika import ExchangeType
from aio_pika.abc import AbstractChannel, AbstractExchange, AbstractQueue

EVENT_EXCHANGE = "wisdomhub.event"
DLX_EXCHANGE = "wisdomhub.dlx"

ANALYZE_QUEUE = "agent.analyze.q"
ANALYZE_DLQ = ANALYZE_QUEUE + ".dlq"

ARTICLE_CREATED = "article.created"
ARTICLE_UPDATED = "article.updated"
KNOWLEDGE_EXTRACT = "knowledge.extract"


async def declare_topology(
    channel: AbstractChannel,
) -> tuple[AbstractExchange, AbstractQueue]:
    event_exchange = await channel.declare_exchange(
        EVENT_EXCHANGE, ExchangeType.TOPIC, durable=True
    )
    dead_letter_exchange = await channel.declare_exchange(
        DLX_EXCHANGE, ExchangeType.DIRECT, durable=True
    )

    queue = await channel.declare_queue(
        ANALYZE_QUEUE,
        durable=True,
        arguments={
            "x-dead-letter-exchange": DLX_EXCHANGE,
            "x-dead-letter-routing-key": ANALYZE_DLQ,
        },
    )
    await queue.bind(event_exchange, ARTICLE_CREATED)
    await queue.bind(event_exchange, ARTICLE_UPDATED)

    dead_letter_queue = await channel.declare_queue(ANALYZE_DLQ, durable=True)
    await dead_letter_queue.bind(dead_letter_exchange, ANALYZE_DLQ)
    return event_exchange, queue
