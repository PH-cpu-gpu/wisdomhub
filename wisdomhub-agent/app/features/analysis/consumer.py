"""article.created / article.updated 消费接线。"""

import json
import logging
from datetime import UTC, datetime
from typing import Any

from aio_pika.abc import AbstractIncomingMessage

from app.features.analysis.job import JobResult
from app.features.analysis.service import AnalysisRunner
from app.infra.mq.consumer import Consumer, ConsumeResult
from app.infra.mq.topology import ANALYZE_QUEUE, ARTICLE_CREATED, ARTICLE_UPDATED

log = logging.getLogger(__name__)

SUPPORTED_EVENTS = frozenset({ARTICLE_CREATED, ARTICLE_UPDATED})
PUBLISHED_STATE = 2

Event = tuple[str, datetime, int, str | None]


class ArticleAnalysisHandler:
    """把文章事件映射为一次分析 Job 调用。"""

    def __init__(self, job: AnalysisRunner) -> None:
        self._job = job

    async def __call__(self, message: AbstractIncomingMessage) -> ConsumeResult:
        event = _parse(message.body)
        if event is None:
            return ConsumeResult.DLQ
        article_id, update_time, state, author_id = event
        if state != PUBLISHED_STATE:
            log.info("文章非发布态，跳过分析 article=%s state=%s", article_id, state)
            return ConsumeResult.ACK
        result = await self._job.run(article_id, update_time, author_id=author_id)
        return ConsumeResult.DLQ if result == JobResult.DLQ else ConsumeResult.ACK


def build_analyze_consumer(
    url: str,
    job: AnalysisRunner,
    *,
    reconnect_interval: float,
    prefetch_count: int = 1,
) -> Consumer:
    return Consumer(
        url,
        ANALYZE_QUEUE,
        ArticleAnalysisHandler(job),
        reconnect_interval=reconnect_interval,
        prefetch_count=prefetch_count,
    )


def _parse(body: bytes) -> Event | None:
    try:
        data = json.loads(body)
    except (TypeError, ValueError):
        log.warning("MQ 消息不是合法 JSON，转 DLQ")
        return None
    if not isinstance(data, dict):
        log.warning("MQ 消息不是 JSON 对象，转 DLQ")
        return None

    event_type = data.get("eventType")
    if event_type not in SUPPORTED_EVENTS:
        log.warning("MQ 事件类型不支持，转 DLQ eventType=%s", event_type)
        return None

    payload = data.get("payload")
    if not isinstance(payload, dict):
        log.warning("MQ 消息缺少 payload，转 DLQ eventType=%s", event_type)
        return None

    article_id = _numeric_id(payload.get("articleId"))
    update_time = _utc_datetime(payload.get("updateTime"))
    state = _state(payload.get("state"))
    if article_id is None or update_time is None or state is None:
        log.warning(
            "MQ 消息字段缺失或非法，转 DLQ eventType=%s article=%s",
            event_type,
            payload.get("articleId"),
        )
        return None
    author_id = _numeric_id(payload.get("authorId"))
    if author_id is None:
        log.warning("MQ 消息缺少合法 authorId，完成后不发通知 article=%s", article_id)
    return article_id, update_time, state, author_id


def _numeric_id(value: Any) -> str | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, int):
        return str(value) if value > 0 else None
    if isinstance(value, str):
        text = value.strip()
        if text.isascii() and text.isdigit() and int(text) > 0:
            return text
    return None


def _state(value: Any) -> int | None:
    if isinstance(value, int) and not isinstance(value, bool):
        return value
    return None


def _utc_datetime(value: Any) -> datetime | None:
    if not isinstance(value, str):
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
    return parsed if parsed.tzinfo is not None else parsed.replace(tzinfo=UTC)
