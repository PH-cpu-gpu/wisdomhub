import logging

from v2.nacos import DeregisterInstanceParam, NacosNamingService, RegisterInstanceParam

from app.config import Settings

log = logging.getLogger(__name__)

SERVICE_NAME = "agent-engine"
GROUP_NAME = "DEFAULT_GROUP"
METADATA = {"lang": "python", "version": "1.0.0"}

async def register_self(naming: NacosNamingService, settings: Settings) -> None:
    ok = await naming.register_instance(RegisterInstanceParam(
        service_name=SERVICE_NAME,
        group_name=GROUP_NAME,
        ip=settings.nacos_register_ip,
        port=settings.app_port,
        ephemeral=True,
        metadata=METADATA,
    ))
    log.info("Nacos 注册 %s group=%s ip=%s port=%s ephemeral=true: %s",
             SERVICE_NAME, GROUP_NAME, settings.nacos_register_ip, settings.app_port, ok)


async def deregister_self(naming: NacosNamingService, settings: Settings) -> None:
    ok = await naming.deregister_instance(DeregisterInstanceParam(
        service_name=SERVICE_NAME,
        group_name=GROUP_NAME,
        ip=settings.nacos_register_ip,
        port=settings.app_port,
        ephemeral=True,
    ))
    log.info("Nacos 注销 %s ip=%s port=%s: %s",
             SERVICE_NAME, settings.nacos_register_ip, settings.app_port, ok)
