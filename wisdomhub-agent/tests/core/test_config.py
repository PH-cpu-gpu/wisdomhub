"""配置分组：必填校验、开关、扁平变量映射。"""

import pytest

from app.core.config import ENV_NAMES, ConfigError, load_settings

_REQUIRED = {
    "DATABASE_URL": "postgresql+asyncpg://wisdomhub:pwd@127.0.0.1:5432/wisdomhub_agent",
    "CHAT_MODEL": "test-model",
    "CHAT_BASE_URL": "http://127.0.0.1:9999/v1",
    "CHAT_API_KEY": "test-key",
}


@pytest.fixture(autouse=True)
def clean_env(monkeypatch: pytest.MonkeyPatch) -> None:
    for name in ENV_NAMES:
        monkeypatch.delenv(name, raising=False)


def _set_required(monkeypatch: pytest.MonkeyPatch) -> None:
    for name, value in _REQUIRED.items():
        monkeypatch.setenv(name, value)


def test_missing_required_reports_all(monkeypatch: pytest.MonkeyPatch) -> None:
    with pytest.raises(ConfigError) as excinfo:
        load_settings(env_file=None)

    message = str(excinfo.value)
    for name in _REQUIRED:
        assert name in message
    assert "缺少必填项" in message
    assert "（db.url）" in message


def test_required_blank_is_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_required(monkeypatch)
    monkeypatch.setenv("CHAT_API_KEY", "   ")

    with pytest.raises(ConfigError) as excinfo:
        load_settings(env_file=None)

    assert "CHAT_API_KEY" in str(excinfo.value)


def test_switches_off_still_loads(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_required(monkeypatch)
    monkeypatch.setenv("NACOS_ENABLED", "false")
    monkeypatch.setenv("RABBITMQ_ENABLED", "false")

    settings = load_settings(env_file=None)

    assert settings.nacos.enabled is False
    assert settings.mq.enabled is False
    assert settings.mq.url is None
    assert settings.db.url == _REQUIRED["DATABASE_URL"]


def test_flat_env_maps_to_groups(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_required(monkeypatch)
    monkeypatch.setenv("APP_PORT", "8123")
    monkeypatch.setenv("CONTEXT_MAX_TURNS", "7")
    monkeypatch.setenv("RABBITMQ_RECONNECT_INTERVAL", "9")
    monkeypatch.setenv("DB_ECHO", "true")

    settings = load_settings(env_file=None)

    assert settings.app.port == 8123
    assert settings.limits.context_max_turns == 7
    assert settings.mq.reconnect_interval == 9
    assert settings.db.echo is True


def test_defaults(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_required(monkeypatch)

    settings = load_settings(env_file=None)

    assert settings.app.env == "local"
    assert settings.app.port == 8000
    assert settings.nacos.service_name == "wisdomhub-agent"
    assert settings.nacos.group == "DEFAULT_GROUP"
    assert settings.mq.enabled is True
    assert settings.limits.context_max_tokens == 12000


def test_invalid_int_names_variable(monkeypatch: pytest.MonkeyPatch) -> None:
    _set_required(monkeypatch)
    monkeypatch.setenv("APP_PORT", "abc")

    with pytest.raises(ConfigError) as excinfo:
        load_settings(env_file=None)

    message = str(excinfo.value)
    assert "APP_PORT" in message
    assert "不是合法整数" in message
