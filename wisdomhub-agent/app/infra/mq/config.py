"""RabbitMQ 连接配置。"""

from collections.abc import Mapping
from typing import Any
from urllib.parse import quote

from app.core.config import Settings


def resolve_rabbitmq_url(
    settings: Settings,
    env_config: Mapping[str, Any] | None = None,
) -> str | None:
    if not settings.mq.enabled:
        return None
    if settings.mq.url:
        return settings.mq.url

    if env_config is None:
        return None
    infra = env_config.get("infra")
    if not isinstance(infra, Mapping):
        return None

    host = infra.get("rabbitmq-host")
    user = infra.get("rabbitmq-username")
    password = infra.get("rabbitmq-password")
    if not host or not user or not password:
        return None

    port = infra.get("rabbitmq-port", 5672)
    vhost = infra.get("rabbitmq-vhost", "/")
    return (
        f"amqp://{quote(str(user), safe='')}:{quote(str(password), safe='')}"
        f"@{host}:{port}/{quote(str(vhost), safe='')}"
    )
