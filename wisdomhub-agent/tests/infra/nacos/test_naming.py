from app.core.config import Settings
from app.infra.http.internal_client import ServiceInstance
from app.infra.nacos.naming import NacosServiceResolver, deregister_self, register_self
from tests.infra.nacos.fakes import FakeInstance, FakeNaming


async def test_register_self(settings: Settings) -> None:
    naming = FakeNaming()

    await register_self(naming, settings)

    request = naming.registered
    assert request is not None
    assert request.service_name == "wisdomhub-agent"
    assert request.group_name == settings.nacos.group
    assert request.ip == settings.nacos.register_ip
    assert request.port == settings.app.port
    assert request.ephemeral is True
    assert request.metadata == {"lang": "python", "version": "1.0.0"}


async def test_deregister_self(settings: Settings) -> None:
    naming = FakeNaming()

    await deregister_self(naming, settings)

    request = naming.deregistered
    assert request is not None
    assert request.service_name == "wisdomhub-agent"
    assert request.group_name == settings.nacos.group
    assert request.ip == settings.nacos.register_ip
    assert request.port == settings.app.port
    assert request.ephemeral is True


async def test_resolver_converts_instances() -> None:
    naming = FakeNaming([FakeInstance("10.0.0.1", 8080), FakeInstance("10.0.0.2", 8081)])
    resolver = NacosServiceResolver(naming=naming, group="DEFAULT_GROUP")

    instances = await resolver.discover("content-service")

    assert instances == [
        ServiceInstance(host="10.0.0.1", port=8080),
        ServiceInstance(host="10.0.0.2", port=8081),
    ]
    request = naming.list_request
    assert request is not None
    assert request.service_name == "content-service"
    assert request.group_name == "DEFAULT_GROUP"
    assert request.healthy_only is True


async def test_resolver_allows_empty_instances() -> None:
    resolver = NacosServiceResolver(naming=FakeNaming(), group="DEFAULT_GROUP")

    assert await resolver.discover("content-service") == []
