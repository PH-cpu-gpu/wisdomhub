from typing import Any


class FakeExchange:
    def __init__(self, name: str, exchange_type: str, durable: bool) -> None:
        self.name = name
        self.exchange_type = exchange_type
        self.durable = durable
        self.published: list[dict[str, Any]] = []

    async def publish(
        self,
        message: Any,
        routing_key: str,
        mandatory: bool,
    ) -> None:
        self.published.append(
            {"message": message, "routing_key": routing_key, "mandatory": mandatory}
        )


class FakeQueue:
    def __init__(
        self,
        name: str,
        durable: bool,
        arguments: dict[str, Any] | None,
    ) -> None:
        self.name = name
        self.durable = durable
        self.arguments = arguments
        self.bindings: list[tuple[str, str]] = []
        self.callback: Any = None
        self.no_ack = False

    async def bind(self, exchange: FakeExchange, routing_key: str) -> None:
        self.bindings.append((exchange.name, routing_key))

    async def consume(self, callback: Any, no_ack: bool = False) -> None:
        self.callback = callback
        self.no_ack = no_ack


class FakeChannel:
    def __init__(self) -> None:
        self.exchanges: dict[str, FakeExchange] = {}
        self.queues: dict[str, FakeQueue] = {}
        self.qos: int | None = None

    async def declare_exchange(
        self,
        name: str,
        exchange_type: str,
        durable: bool = False,
        **_: Any,
    ) -> FakeExchange:
        exchange = FakeExchange(name, exchange_type, durable)
        self.exchanges[name] = exchange
        return exchange

    async def declare_queue(
        self,
        name: str,
        durable: bool = False,
        arguments: dict[str, Any] | None = None,
        **_: Any,
    ) -> FakeQueue:
        queue = FakeQueue(name, durable, arguments)
        self.queues[name] = queue
        return queue

    async def set_qos(self, prefetch_count: int = 0, **_: Any) -> None:
        self.qos = prefetch_count


class FakeConnection:
    def __init__(self, channel: FakeChannel) -> None:
        self._channel = channel
        self.publisher_confirms = False
        self.closed = False

    async def channel(self, publisher_confirms: bool = True) -> FakeChannel:
        self.publisher_confirms = publisher_confirms
        return self._channel

    async def close(self) -> None:
        self.closed = True


class FakeMessage:
    def __init__(self, body: bytes = b"") -> None:
        self.body = body
        self.acked = 0
        self.nacked: list[bool] = []

    async def ack(self, multiple: bool = False) -> None:
        self.acked += 1

    async def nack(self, multiple: bool = False, requeue: bool = True) -> None:
        self.nacked.append(requeue)
