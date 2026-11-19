"""装配链：启动顺序、降级分支与关停顺序。"""

import asyncio
from types import SimpleNamespace
from typing import Any

import pytest
from tests.infra.nacos.fakes import FakeConfig, FakeNaming

from app.core import lifespan as lifespan_module
from app.core.config import Settings
from app.core.lifespan import build_deps, build_steps
from app.infra.nacos.client import NacosClients


class _FakeDatabase:
    def __init__(self, events: list[str], *, ping_error: Exception | None = None) -> None:
        self.events = events
        self.ping_error = ping_error
        self.session_factory = object()

    async def ping(self) -> None:
        if self.ping_error is not None:
            raise self.ping_error
        self.events.append("ping-db")

    async def close(self) -> None:
        self.events.append("close-db")


class _DatabaseFactory:
    def __init__(self, events: list[str], *, ping_error: Exception | None = None) -> None:
        self.events = events
        self.ping_error = ping_error

    def create(self, _: Any) -> _FakeDatabase:
        return _FakeDatabase(self.events, ping_error=self.ping_error)


class _OrderedNaming(FakeNaming):
    def __init__(self, events: list[str]) -> None:
        super().__init__()
        self.events = events

    async def shutdown(self) -> None:
        self.events.append("close-nacos")
        await super().shutdown()


class _FakeInternal:
    def __init__(self, events: list[str]) -> None:
        self.events = events

    async def aclose(self) -> None:
        self.events.append("close-internal")


class _FakeConnection:
    def __init__(self, events: list[str]) -> None:
        self.events = events

    async def channel(self, publisher_confirms: bool = True) -> object:
        return object()

    async def close(self) -> None:
        self.events.append("close-mq")


class _FakeConsumer:
    def __init__(self, events: list[str]) -> None:
        self.events = events
        self.stopped = asyncio.Event()

    async def run(self) -> None:
        await self.stopped.wait()

    async def stop(self) -> None:
        self.events.append("stop-consumer")
        self.stopped.set()


class _FakeHttp:
    async def aclose(self) -> None:
        return None


def _patch(
    monkeypatch: pytest.MonkeyPatch,
    events: list[str],
    *,
    nacos: bool = True,
    url: str | None = "amqp://guest:guest@127.0.0.1/",
    connect_error: Exception | None = None,
    ping_error: Exception | None = None,
) -> None:
    class _Registry:
        def __init__(self, *_: Any) -> None:
            self.model = object()

        def get(self, profile: Any) -> object:
            return self.model

    async def _nacos(_: Settings) -> NacosClients | None:
        if not nacos:
            return None
        return NacosClients(naming=_OrderedNaming(events), config=FakeConfig())

    async def _register(_: Any, __: Settings) -> None:
        events.append("register")

    async def _deregister(_: Any, __: Settings) -> None:
        events.append("deregister")

    async def _secret(_: Any, __: Settings) -> str:
        return "s" * 16

    async def _env(_: Any, __: Settings) -> dict[str, Any]:
        return {}

    def _build_graph(_: Any, tools: Any) -> object:
        events.append("graph")
        return SimpleNamespace(tools=tools)

    async def _connect(_: str, **__: Any) -> _FakeConnection:
        if connect_error is not None:
            raise connect_error
        return _FakeConnection(events)

    async def _topology(_: Any) -> Any:
        return SimpleNamespace(events=object())

    def _consumer(_: str, __: Any, *, reconnect_interval: float) -> _FakeConsumer:
        return _FakeConsumer(events)

    monkeypatch.setattr(lifespan_module, "LlmRegistry", _Registry)
    monkeypatch.setattr(
        lifespan_module, "Database", _DatabaseFactory(events, ping_error=ping_error)
    )
    monkeypatch.setattr(lifespan_module, "create_nacos_clients", _nacos)
    monkeypatch.setattr(lifespan_module, "register_self", _register)
    monkeypatch.setattr(lifespan_module, "deregister_self", _deregister)
    monkeypatch.setattr(lifespan_module, "load_internal_sign_secret", _secret)
    monkeypatch.setattr(lifespan_module, "load_env_config", _env)
    monkeypatch.setattr(lifespan_module, "InternalClient", lambda *args: _FakeInternal(events))
    monkeypatch.setattr(lifespan_module, "httpx", SimpleNamespace(AsyncClient=lambda: _FakeHttp()))
    monkeypatch.setattr(lifespan_module, "build_graph", _build_graph)
    monkeypatch.setattr(lifespan_module, "resolve_rabbitmq_url", lambda *_: url)
    monkeypatch.setattr(
        lifespan_module,
        "aio_pika",
        SimpleNamespace(connect_robust=_connect),
    )
    monkeypatch.setattr(lifespan_module, "declare_topology", _topology)
    monkeypatch.setattr(lifespan_module, "EventPublisher", lambda exchange: SimpleNamespace())
    monkeypatch.setattr(lifespan_module, "build_analyze_consumer", _consumer)


async def test_full_assembly_and_shutdown_order(
    settings: Settings, monkeypatch: pytest.MonkeyPatch
) -> None:
    events: list[str] = []
    _patch(monkeypatch, events)

    deps = await build_deps(settings, build_steps(settings))

    assert deps.conversation is not None
    assert deps.graph is not None
    assert deps.knowledge_tools is not None
    assert deps.analysis is not None
    assert [task.get_name() for task in deps.tasks] == ["analysis-consumer"]

    await deps.aclose()

    assert events == [
        "ping-db",
        "register",
        "graph",
        "stop-consumer",
        "close-mq",
        "close-internal",
        "deregister",
        "close-nacos",
        "close-db",
    ]


async def test_nacos_disabled_skips_tools_and_analysis(
    settings: Settings, monkeypatch: pytest.MonkeyPatch
) -> None:
    events: list[str] = []
    _patch(monkeypatch, events, nacos=False)

    deps = await build_deps(settings, build_steps(settings))

    assert deps.knowledge_tools is None
    assert deps.graph is not None
    assert deps.analysis is None
    await deps.aclose()

    assert "register" not in events
    assert "close-db" in events


async def test_missing_mq_url_skips_analysis(
    settings: Settings, monkeypatch: pytest.MonkeyPatch
) -> None:
    events: list[str] = []
    _patch(monkeypatch, events, url=None)

    deps = await build_deps(settings, build_steps(settings))

    assert deps.knowledge_tools is not None
    assert deps.analysis is None
    assert deps.tasks == set()
    await deps.aclose()

    assert "close-mq" not in events


async def test_mq_connect_failure_degrades(
    settings: Settings, monkeypatch: pytest.MonkeyPatch
) -> None:
    events: list[str] = []
    _patch(monkeypatch, events, connect_error=RuntimeError("rabbit down"))

    deps = await build_deps(settings, build_steps(settings))

    assert deps.graph is not None
    assert deps.analysis is None
    assert deps.tasks == set()


async def test_database_ping_failure_aborts_startup(
    settings: Settings, monkeypatch: pytest.MonkeyPatch
) -> None:
    events: list[str] = []
    _patch(monkeypatch, events, ping_error=RuntimeError("db down"))

    with pytest.raises(RuntimeError, match="db down"):
        await build_deps(settings, build_steps(settings))

    assert events == ["close-db"]
