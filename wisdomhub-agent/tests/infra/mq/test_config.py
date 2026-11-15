from app.core.config import Settings
from app.infra.mq.config import resolve_rabbitmq_url


def test_explicit_url_wins(settings: Settings) -> None:
    configured = settings.model_copy(
        update={"mq": settings.mq.model_copy(update={"url": "amqp://explicit:5672/"})}
    )

    assert resolve_rabbitmq_url(configured, {"infra": {"rabbitmq-host": "other"}}) == (
        "amqp://explicit:5672/"
    )


def test_env_config_builds_encoded_url(settings: Settings) -> None:
    env = {
        "infra": {
            "rabbitmq-host": "rabbit",
            "rabbitmq-port": 5673,
            "rabbitmq-username": "u@ser",
            "rabbitmq-password": "p@ss/word",
            "rabbitmq-vhost": "/",
        }
    }

    assert resolve_rabbitmq_url(settings, env) == ("amqp://u%40ser:p%40ss%2Fword@rabbit:5673/%2F")


def test_env_config_defaults(settings: Settings) -> None:
    env = {
        "infra": {
            "rabbitmq-host": "rabbit",
            "rabbitmq-username": "user",
            "rabbitmq-password": "pass",
        }
    }

    assert resolve_rabbitmq_url(settings, env) == "amqp://user:pass@rabbit:5672/%2F"


def test_missing_env_fields(settings: Settings) -> None:
    assert resolve_rabbitmq_url(settings, {"infra": {"rabbitmq-host": "rabbit"}}) is None
    assert resolve_rabbitmq_url(settings, None) is None


def test_disabled_returns_none(settings: Settings) -> None:
    disabled = settings.model_copy(
        update={"mq": settings.mq.model_copy(update={"enabled": False, "url": "amqp://x"})}
    )

    assert resolve_rabbitmq_url(disabled, None) is None
