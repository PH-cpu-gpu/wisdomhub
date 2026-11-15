import pytest
from v2.nacos import NacosConfigService, NacosNamingService

from app.core.config import Settings
from app.infra.nacos.client import NacosClients, build_client_config, create_nacos_clients
from tests.infra.nacos.fakes import FakeConfig, FakeNaming


def test_build_client_config(settings: Settings) -> None:
    config = build_client_config(settings.nacos)

    assert config.server_list == [settings.nacos.server_addr]
    assert config.namespace_id == settings.nacos.namespace
    assert config.log_level == "INFO"


async def test_nacos_disabled_returns_none(settings: Settings) -> None:
    disabled = settings.model_copy(
        update={"nacos": settings.nacos.model_copy(update={"enabled": False})}
    )

    assert await create_nacos_clients(disabled) is None


async def test_create_clients_with_async_factories(
    settings: Settings,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    naming = FakeNaming()
    config = FakeConfig()

    async def create_naming(_: object) -> FakeNaming:
        return naming

    async def create_config(_: object) -> FakeConfig:
        return config

    monkeypatch.setattr(NacosNamingService, "create_naming_service", create_naming)
    monkeypatch.setattr(NacosConfigService, "create_config_service", create_config)

    clients = await create_nacos_clients(settings)

    assert clients is not None
    assert clients.naming is naming
    assert clients.config is config
    await clients.aclose()
    assert naming.shutdown_called is True
    assert config.shutdown_called is True


async def test_config_factory_failure_closes_naming(
    settings: Settings,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    naming = FakeNaming()

    async def create_naming(_: object) -> FakeNaming:
        return naming

    async def create_config(_: object) -> FakeConfig:
        raise RuntimeError("config down")

    monkeypatch.setattr(NacosNamingService, "create_naming_service", create_naming)
    monkeypatch.setattr(NacosConfigService, "create_config_service", create_config)

    with pytest.raises(RuntimeError):
        await create_nacos_clients(settings)

    assert naming.shutdown_called is True


async def test_clients_aclose_closes_both() -> None:
    naming = FakeNaming()
    config = FakeConfig()
    clients = NacosClients(naming=naming, config=config)

    await clients.aclose()

    assert naming.shutdown_called is True
    assert config.shutdown_called is True
