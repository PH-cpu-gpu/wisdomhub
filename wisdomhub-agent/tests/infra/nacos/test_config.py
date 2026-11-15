import pytest

from app.core.config import Settings
from app.infra.nacos.config import (
    NacosConfigError,
    load_common_config,
    load_env_config,
    load_internal_sign_secret,
)
from tests.infra.nacos.fakes import FakeConfig

SECRET = "0123456789abcdef0123456789abcdef"

COMMON_YAML = f"""
wisdomhub:
  security:
    internal-sign:
      secret: "{SECRET}"
"""

ENV_YAML = """
infra:
  rabbitmq-host: 127.0.0.1
"""


def _config(common: str = COMMON_YAML, env: str = ENV_YAML) -> FakeConfig:
    return FakeConfig(
        {
            ("wisdomhub-common.yaml", "DEFAULT_GROUP"): common,
            ("wisdomhub-env.yaml", "DEFAULT_GROUP"): env,
        }
    )


async def test_load_common_and_env_config(settings: Settings) -> None:
    config = _config()

    common = await load_common_config(config, settings)
    env = await load_env_config(config, settings)

    assert common["wisdomhub"]["security"]["internal-sign"]["secret"] == SECRET
    assert env["infra"]["rabbitmq-host"] == "127.0.0.1"
    assert config.requests[0].data_id == "wisdomhub-common.yaml"
    assert config.requests[0].group == settings.nacos.group
    assert config.requests[1].data_id == "wisdomhub-env.yaml"


async def test_load_internal_sign_secret(settings: Settings) -> None:
    assert await load_internal_sign_secret(_config(), settings) == SECRET


async def test_missing_config(settings: Settings) -> None:
    with pytest.raises(NacosConfigError):
        await load_common_config(FakeConfig(), settings)


async def test_invalid_yaml(settings: Settings) -> None:
    with pytest.raises(NacosConfigError):
        await load_common_config(_config(common="a: ["), settings)


async def test_non_mapping_yaml(settings: Settings) -> None:
    with pytest.raises(NacosConfigError):
        await load_common_config(_config(common="- a\n- b\n"), settings)


async def test_short_secret(settings: Settings) -> None:
    yaml = """
wisdomhub:
  security:
    internal-sign:
      secret: "short"
"""
    with pytest.raises(NacosConfigError):
        await load_internal_sign_secret(_config(common=yaml), settings)


async def test_missing_secret_path(settings: Settings) -> None:
    with pytest.raises(NacosConfigError):
        await load_internal_sign_secret(_config(common="wisdomhub: {}\n"), settings)


async def test_get_config_error_is_wrapped(settings: Settings) -> None:
    with pytest.raises(NacosConfigError):
        await load_common_config(FakeConfig(error=RuntimeError("down")), settings)
