import logging
from urllib.parse import quote

import yaml
from v2.nacos import NacosConfigService
from v2.nacos.config.model.config_param import ConfigParam

from app.config import Settings
from security.internal_sign import COMMON_CONFIG_DATA_ID, COMMON_CONFIG_GROUP

log = logging.getLogger(__name__)

ENV_CONFIG_DATA_ID = "wisdomhub-env.yaml"


async def resolve_rabbitmq_url(
    settings: Settings, config_client: NacosConfigService | None
) -> str | None:
    """优先 .env 的 RABBITMQ_URL，否则从 Nacos common 配置拼。"""
    if settings.rabbitmq_url and settings.rabbitmq_url.strip():
        return settings.rabbitmq_url.strip()
    if config_client is None:
        return None

    infra: dict = {}
    for data_id in (ENV_CONFIG_DATA_ID, COMMON_CONFIG_DATA_ID):
        try:
            text = await config_client.get_config(
                ConfigParam(data_id=data_id, group=COMMON_CONFIG_GROUP)
            )
        except Exception:
            log.warning("读取 Nacos 配置失败 data_id=%s", data_id, exc_info=True)
            continue
        data = yaml.safe_load(text or "") or {}
        infra = data.get("infra") or {}
        if infra.get("rabbitmq-host"):
            break

    host = infra.get("rabbitmq-host")
    port = infra.get("rabbitmq-port", 5672)
    user = infra.get("rabbitmq-username")
    password = infra.get("rabbitmq-password")
    vhost = infra.get("rabbitmq-vhost", "/")
    if not host or not user or not password:
        log.warning("Nacos 缺少 RabbitMQ 配置（infra.rabbitmq-*）")
        return None

    return (
        f"amqp://{quote(str(user), safe='')}:{quote(str(password), safe='')}"
        f"@{host}:{port}/{quote(str(vhost), safe='')}"
    )
