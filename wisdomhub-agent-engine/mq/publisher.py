import json
import logging
import uuid
from datetime import UTC, datetime
from typing import Any

from aio_pika import DeliveryMode, Message
from aio_pika.abc import AbstractExchange

from mq.topology import KNOWLEDGE_EXTRACT

log = logging.getLogger(__name__)


class KnowledgeEventPublisher:
    def __init__(self, exchange: AbstractExchange) -> None:
        self._exchange = exchange

    async def publish(self, article_id: str, entities: list[str]) -> None:
        event_id = str(uuid.uuid4())
        event: dict[str, Any] = {
            "eventId": event_id,
            "eventType": KNOWLEDGE_EXTRACT,
            "occurredAt": datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z"),
            "payload": {
                "articleId": int(article_id),
                "entities": [{"name": name} for name in entities if name],
            },
        }
        body = json.dumps(event, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        message = Message(
            body=body,
            content_type="application/json",
            delivery_mode=DeliveryMode.PERSISTENT,
            message_id=event_id,
        )
        await self._exchange.publish(
            message, routing_key=KNOWLEDGE_EXTRACT, mandatory=True
        )
        log.info("已发布 KnowledgeExtractEvent article=%s entities=%d",
                 article_id, len(entities))
