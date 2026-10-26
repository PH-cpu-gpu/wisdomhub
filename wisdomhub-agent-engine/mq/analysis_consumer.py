import asyncio
import contextlib
import json
import logging
from datetime import UTC, datetime
from typing import Any

import aio_pika
from aio_pika.abc import AbstractConnection, AbstractIncomingMessage
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from agents.analysis_job import AnalysisJob
from app.config import Settings
from mq.publisher import KnowledgeEventPublisher
from mq.topology import (
    ANALYZE_QUEUE,
    ARTICLE_CREATED,
    ARTICLE_UPDATED,
    declare_topology,
)
from tools.internal_client import InternalClient

log = logging.getLogger(__name__)

ANALYZE_EVENTS = frozenset({ARTICLE_CREATED, ARTICLE_UPDATED})
PUBLISHED_STATE = 2


def _parse_updated_at(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        parsed = datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError:
        return None
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.astimezone(UTC)


def _parse_state(value: Any) -> int | None:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return None


class AnalysisConsumer:
    def __init__(
        self,
        *,
        settings: Settings,
        url: str,
        client: InternalClient,
        llm: Any,
        session_factory: async_sessionmaker[AsyncSession],
    ) -> None:
        self._settings = settings
        self._url = url
        self._client = client
        self._llm = llm
        self._session_factory = session_factory
        self._connection: AbstractConnection | None = None
        self._job: AnalysisJob | None = None
        self._stop_event = asyncio.Event()

    async def run(self) -> None:
        connection = await aio_pika.connect_robust(
            self._url,
            reconnect_interval=self._settings.rabbitmq_reconnect_interval,
        )
        self._connection = connection
        try:
            channel = await connection.channel(publisher_confirms=True)
            await channel.set_qos(prefetch_count=1)
            exchange, queue = await declare_topology(channel)
            self._job = AnalysisJob(
                settings=self._settings,
                session_factory=self._session_factory,
                client=self._client,
                llm=self._llm,
                publisher=KnowledgeEventPublisher(exchange),
            )
            await queue.consume(self._on_message, no_ack=False)
            log.info("文章分析消费者已启动 queue=%s", ANALYZE_QUEUE)
            await self._stop_event.wait()
        finally:
            with contextlib.suppress(Exception):
                await connection.close()
            self._connection = None

    async def stop(self) -> None:
        self._stop_event.set()
        if self._connection is not None:
            with contextlib.suppress(Exception):
                await self._connection.close()

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        try:
            body = json.loads(message.body)
            event_type = body.get("eventType") if isinstance(body, dict) else None
            if event_type not in ANALYZE_EVENTS:
                log.warning("丢弃非文章分析事件 event=%s", event_type)
                await message.nack(requeue=False)
                return

            payload = body.get("payload")
            if not isinstance(payload, dict):
                log.warning("%s 缺少 payload event=%s", event_type, body.get("eventId"))
                await message.nack(requeue=False)
                return

            article_id = str(payload.get("articleId") or "").strip()
            if not article_id:
                log.warning("%s 缺少 articleId event=%s", event_type, body.get("eventId"))
                await message.nack(requeue=False)
                return

            state = _parse_state(payload.get("state"))
            if state is None:
                log.warning("%s 缺少 state event=%s", event_type, body.get("eventId"))
                await message.nack(requeue=False)
                return
            if state != PUBLISHED_STATE:
                log.info("跳过非发布态文章 event=%s article=%s state=%d",
                         body.get("eventId"), article_id, state)
                await message.ack()
                return

            source_updated_at = _parse_updated_at(payload.get("updateTime"))
            if source_updated_at is None:
                log.warning("%s 缺少可解析的 updateTime event=%s article=%s",
                            event_type, body.get("eventId"), article_id)
                await message.nack(requeue=False)
                return

            job = self._job
            if job is None:
                log.error("文章分析 job 未初始化")
                await message.nack(requeue=False)
                return

            log.info("收到 %s event=%s article=%s version=%s",
                     event_type, body.get("eventId"), article_id,
                     source_updated_at.isoformat())
            if await job.run(article_id, source_updated_at=source_updated_at):
                await message.ack()
                log.info("文章分析消息已 ACK article=%s", article_id)
            else:
                await message.nack(requeue=False)
                log.warning("文章分析消息失败，已进 DLQ article=%s", article_id)
        except Exception:
            log.exception("文章分析消息处理异常")
            with contextlib.suppress(Exception):
                await message.nack(requeue=False)
