"""Nacos 注册、注销与服务发现。"""

import logging
from dataclasses import dataclass
from typing import Any, Protocol

from v2.nacos import (
    DeregisterInstanceParam,
    ListInstanceParam,
    RegisterInstanceParam,
)

from app.core.config import Settings
from app.infra.http.internal_client import ServiceInstance

log = logging.getLogger(__name__)

METADATA = {"lang": "python", "version": "1.0.0"}


class NamingClient(Protocol):
    async def register_instance(self, request: RegisterInstanceParam) -> bool: ...

    async def deregister_instance(self, request: DeregisterInstanceParam) -> bool: ...

    async def list_instances(self, request: ListInstanceParam) -> list[Any]: ...

    async def shutdown(self) -> None: ...


async def register_self(naming: NamingClient, settings: Settings) -> None:
    request = RegisterInstanceParam(
        service_name=settings.nacos.service_name,
        group_name=settings.nacos.group,
        ip=settings.nacos.register_ip,
        port=settings.app.port,
        ephemeral=True,
        metadata=dict(METADATA),
    )
    ok = await naming.register_instance(request)
    log.info(
        "Nacos 注册 %s group=%s ip=%s port=%s ephemeral=true: %s",
        settings.nacos.service_name,
        settings.nacos.group,
        settings.nacos.register_ip,
        settings.app.port,
        ok,
    )


async def deregister_self(naming: NamingClient, settings: Settings) -> None:
    request = DeregisterInstanceParam(
        service_name=settings.nacos.service_name,
        group_name=settings.nacos.group,
        ip=settings.nacos.register_ip,
        port=settings.app.port,
        ephemeral=True,
    )
    ok = await naming.deregister_instance(request)
    log.info(
        "Nacos 注销 %s group=%s ip=%s port=%s: %s",
        settings.nacos.service_name,
        settings.nacos.group,
        settings.nacos.register_ip,
        settings.app.port,
        ok,
    )


@dataclass(frozen=True, slots=True)
class NacosServiceResolver:
    naming: NamingClient
    group: str

    async def discover(self, service: str) -> list[ServiceInstance]:
        instances = await self.naming.list_instances(
            ListInstanceParam(
                service_name=service,
                group_name=self.group,
                healthy_only=True,
            )
        )
        return [
            ServiceInstance(host=str(instance.ip), port=int(instance.port))
            for instance in instances
        ]
