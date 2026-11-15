"""RabbitMQ 消费循环：重连、ack/nack/DLQ 与优雅退出。"""

import asyncio
import contextlib
import logging
from collections.abc import Awaitable, Callable
from enum import StrEnum

import aio_pika
from aio_pika.abc import AbstractConnection, AbstractIncomingMessage, AbstractQueue

from app.infra.mq.topology import declare_topology

log = logging.getLogger(__name__)


class ConsumeResult(StrEnum):
    ACK = "ack"
    REQUEUE = "requeue"
    DLQ = "dlq"


Handler = Callable[[AbstractIncomingMessage], Awaitable[ConsumeResult]]


class Consumer:
    def __init__(
        self,
        url: str,
        queue_name: str,
        handler: Handler,
        *,
        reconnect_interval: float = 5.0,
        prefetch_count: int = 1,
    ) -> None:
        self._url = url
        self._queue_name = queue_name
        self._handler = handler
        self._reconnect_interval = reconnect_interval
        self._prefetch_count = prefetch_count
        self._stop_event = asyncio.Event()
        self._connection: AbstractConnection | None = None
        self._queue: AbstractQueue | None = None

    @property
    def queue(self) -> AbstractQueue | None:
        return self._queue

    async def run(self) -> None:
        while not self._stop_event.is_set():
            try:
                connection = await aio_pika.connect_robust(
                    self._url,
                    reconnect_interval=self._reconnect_interval,
                )
            except asyncio.CancelledError:
                raise
            except Exception:
                log.warning(
                    "RabbitMQ 连接失败，%ss 后重试",
                    self._reconnect_interval,
                    exc_info=True,
                )
                await self._wait_retry()
                continue

            self._connection = connection
            try:
                channel = await connection.channel(publisher_confirms=True)
                await channel.set_qos(prefetch_count=self._prefetch_count)
                topology = await declare_topology(channel)
                queue = topology.queues.get(self._queue_name)
                if queue is None:
                    raise RuntimeError(f"未知队列: {self._queue_name}")
                self._queue = queue
                await queue.consume(self._on_message, no_ack=False)
                log.info("MQ 消费者已启动 queue=%s", self._queue_name)
                await self._stop_event.wait()
                return
            except asyncio.CancelledError:
                raise
            except Exception:
                if self._stop_event.is_set():
                    return
                log.warning(
                    "MQ 消费循环异常，%ss 后重试",
                    self._reconnect_interval,
                    exc_info=True,
                )
                await self._wait_retry()
            finally:
                self._queue = None
                self._connection = None
                with contextlib.suppress(Exception):
                    await connection.close()

    async def stop(self) -> None:
        self._stop_event.set()
        connection = self._connection
        if connection is not None:
            with contextlib.suppress(Exception):
                await connection.close()

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        try:
            result = await self._handler(message)
        except asyncio.CancelledError:
            raise
        except Exception:
            log.exception("MQ 消息处理异常 queue=%s", self._queue_name)
            result = ConsumeResult.DLQ

        try:
            if result is ConsumeResult.ACK:
                await message.ack()
            elif result is ConsumeResult.REQUEUE:
                await message.nack(requeue=True)
            else:
                await message.nack(requeue=False)
        except Exception:
            log.warning("MQ ack/nack 失败 queue=%s", self._queue_name, exc_info=True)

    async def _wait_retry(self) -> None:
        with contextlib.suppress(TimeoutError):
            await asyncio.wait_for(
                self._stop_event.wait(),
                timeout=self._reconnect_interval,
            )
