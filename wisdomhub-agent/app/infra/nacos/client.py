"""Nacos 客户端生命周期。"""

from dataclasses import dataclass

from v2.nacos import (
    ClientConfig,
    ClientConfigBuilder,
    GRPCConfig,
    NacosConfigService,
    NacosNamingService,
)

from app.core.config import NacosSettings, Settings
from app.infra.nacos.config import ConfigClient
from app.infra.nacos.naming import NamingClient


@dataclass(frozen=True, slots=True)
class NacosClients:
    naming: NamingClient
    config: ConfigClient

    async def aclose(self) -> None:
        try:
            await self.naming.shutdown()
        finally:
            await self.config.shutdown()


def build_client_config(settings: NacosSettings) -> ClientConfig:
    return (
        ClientConfigBuilder()
        .server_address(settings.server_addr)
        .namespace_id(settings.namespace)
        .log_level("INFO")
        .grpc_config(GRPCConfig(grpc_timeout=3000))
        .build()
    )


async def create_nacos_clients(settings: Settings) -> NacosClients | None:
    if not settings.nacos.enabled:
        return None

    client_config = build_client_config(settings.nacos)
    naming: NacosNamingService | None = None
    config: NacosConfigService | None = None
    try:
        naming = await NacosNamingService.create_naming_service(client_config)
        config = await NacosConfigService.create_config_service(client_config)
    except BaseException:
        if config is not None:
            await config.shutdown()
        if naming is not None:
            await naming.shutdown()
        raise

    return NacosClients(naming=naming, config=config)
