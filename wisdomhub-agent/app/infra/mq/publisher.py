"""RabbitMQ 事件发布器。"""

import json
import logging
import uuid
from datetime import UTC, datetime
from typing import Any

from aio_pika import DeliveryMode, Message
from aio_pika.abc import AbstractExchange

log = logging.getLogger(__name__)


class EventPublisher:
    def __init__(self, exchange: AbstractExchange) -> None:
        self._exchange = exchange

    async def publish(
        self,
        event_type: str,
        payload: dict[str, Any],
        *,
        event_id: str | None = None,
    ) -> str:
        event_id = event_id or str(uuid.uuid4())
        event = {
            "eventId": event_id,
            "eventType": event_type,
            "occurredAt": datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z"),
            "payload": payload,
        }
        body = json.dumps(event, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        message = Message(
            body=body,
            content_type="application/json",
            delivery_mode=DeliveryMode.PERSISTENT,
            message_id=event_id,
        )
        await self._exchange.publish(message, routing_key=event_type, mandatory=True)
        log.info("已发布事件 %s event=%s", event_type, event_id)
        return event_id
