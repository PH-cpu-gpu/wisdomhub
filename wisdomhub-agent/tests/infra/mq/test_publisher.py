import json
from typing import Any, cast

from aio_pika import DeliveryMode
from aio_pika.abc import AbstractExchange

from app.infra.mq.publisher import EventPublisher
from tests.infra.mq.fakes import FakeExchange


async def test_publish_envelope() -> None:
    exchange = FakeExchange("wisdomhub.event", "topic", True)
    publisher = EventPublisher(cast(AbstractExchange, exchange))

    event_id = await publisher.publish(
        "knowledge.extract",
        {"articleId": 1, "entities": [{"name": "Spring"}]},
    )

    assert len(exchange.published) == 1
    published = exchange.published[0]
    message = published["message"]
    assert published["routing_key"] == "knowledge.extract"
    assert published["mandatory"] is True

    body: dict[str, Any] = json.loads(message.body)
    assert body["eventId"] == event_id
    assert body["eventType"] == "knowledge.extract"
    assert body["occurredAt"].endswith("Z")
    assert body["payload"] == {"articleId": 1, "entities": [{"name": "Spring"}]}

    assert message.content_type == "application/json"
    assert message.delivery_mode == DeliveryMode.PERSISTENT
    assert message.message_id == event_id


async def test_publish_uses_given_event_id() -> None:
    exchange = FakeExchange("wisdomhub.event", "topic", True)
    publisher = EventPublisher(cast(AbstractExchange, exchange))

    event_id = await publisher.publish("ai.completed", {"articleId": 1}, event_id="fixed")

    assert event_id == "fixed"
    assert json.loads(exchange.published[0]["message"].body)["eventId"] == "fixed"
