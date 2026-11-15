"""Nacos 配置拉取。"""

import logging
from typing import Any, Protocol

import yaml
from v2.nacos import ConfigParam

from app.core.config import Settings

log = logging.getLogger(__name__)

SECRET_PATH = ("wisdomhub", "security", "internal-sign")
MIN_SECRET_LENGTH = 16


class NacosConfigError(RuntimeError):
    """Nacos 配置缺失或非法。"""


class ConfigClient(Protocol):
    async def get_config(self, param: ConfigParam) -> str: ...

    async def shutdown(self) -> None: ...


async def _get_text(config: ConfigClient, data_id: str, group: str) -> str:
    try:
        text = await config.get_config(ConfigParam(data_id=data_id, group=group))
    except Exception as exc:
        raise NacosConfigError(f"Nacos 配置拉取失败: {data_id}") from exc
    if not text or not text.strip():
        raise NacosConfigError(f"Nacos 缺少配置: {data_id}")
    return text


async def load_yaml_config(
    config: ConfigClient,
    data_id: str,
    group: str,
) -> dict[str, Any]:
    text = await _get_text(config, data_id, group)
    try:
        data = yaml.safe_load(text)
    except yaml.YAMLError as exc:
        raise NacosConfigError(f"Nacos 配置不是合法 YAML: {data_id}") from exc
    if not isinstance(data, dict):
        raise NacosConfigError(f"Nacos 配置不是对象: {data_id}")
    return data


async def load_common_config(config: ConfigClient, settings: Settings) -> dict[str, Any]:
    return await load_yaml_config(
        config,
        settings.nacos.common_data_id,
        settings.nacos.group,
    )


async def load_env_config(config: ConfigClient, settings: Settings) -> dict[str, Any]:
    return await load_yaml_config(
        config,
        settings.nacos.env_data_id,
        settings.nacos.group,
    )


async def load_internal_sign_secret(config: ConfigClient, settings: Settings) -> str:
    data = await load_common_config(config, settings)
    node: Any = data
    for key in (*SECRET_PATH, "secret"):
        if not isinstance(node, dict):
            node = None
            break
        node = node.get(key)
    if not isinstance(node, str) or len(node) < MIN_SECRET_LENGTH:
        raise NacosConfigError(
            "Nacos wisdomhub-common.yaml 的 wisdomhub.security.internal-sign.secret 缺失或过短"
        )
    return node
