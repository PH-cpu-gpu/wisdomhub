"""配置分组：app、db、llm、nacos、mq、limits。"""

from functools import lru_cache

from pydantic import BaseModel, Field, ValidationError, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ConfigError(RuntimeError):
    """配置缺失或非法。"""


class _Group(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
        populate_by_name=True,
    )


def _required(value: str) -> str:
    value = value.strip()
    if not value:
        raise ValueError("不能为空")
    return value


class AppSettings(_Group):
    env: str = Field(default="local", validation_alias="APP_ENV")
    log_level: str = Field(default="INFO", validation_alias="LOG_LEVEL")
    log_json: bool = Field(default=True, validation_alias="LOG_JSON")
    port: int = Field(default=8000, ge=1, le=65535, validation_alias="APP_PORT")
    stream_idle_timeout: float = Field(default=90.0, gt=0, validation_alias="STREAM_IDLE_TIMEOUT")
    message_in_progress_ttl: int = Field(
        default=300, ge=1, validation_alias="MESSAGE_IN_PROGRESS_TTL"
    )

    @field_validator("log_level")
    @classmethod
    def _check_log_level(cls, value: str) -> str:
        level = value.strip().upper()
        if level not in {"CRITICAL", "ERROR", "WARNING", "INFO", "DEBUG"}:
            raise ValueError("必须是 CRITICAL/ERROR/WARNING/INFO/DEBUG")
        return level


class DbSettings(_Group):
    url: str = Field(validation_alias="DATABASE_URL")
    pool_size: int = Field(default=5, ge=1, validation_alias="DB_POOL_SIZE")
    pool_timeout: float = Field(default=30.0, gt=0, validation_alias="DB_POOL_TIMEOUT")
    echo: bool = Field(default=False, validation_alias="DB_ECHO")

    @field_validator("url")
    @classmethod
    def _check_url(cls, value: str) -> str:
        return _required(value)


class LlmSettings(_Group):
    model: str = Field(validation_alias="CHAT_MODEL")
    base_url: str = Field(validation_alias="CHAT_BASE_URL")
    api_key: str = Field(validation_alias="CHAT_API_KEY")
    temperature: float = Field(default=0.3, ge=0, le=2, validation_alias="CHAT_TEMPERATURE")
    timeout: float = Field(default=60.0, gt=0, validation_alias="CHAT_TIMEOUT")
    analysis_timeout: float = Field(default=600.0, gt=0, validation_alias="ANALYSIS_TIMEOUT")
    analysis_max_article_tokens: int = Field(
        default=24000, ge=1, validation_alias="ANALYSIS_MAX_ARTICLE_TOKENS"
    )
    reflection_timeout: float = Field(
        default=30.0, gt=0, validation_alias="PROPOSAL_REFLECTION_TIMEOUT"
    )

    @field_validator("model", "base_url", "api_key")
    @classmethod
    def _check_required(cls, value: str) -> str:
        return _required(value)


class NacosSettings(_Group):
    enabled: bool = Field(default=True, validation_alias="NACOS_ENABLED")
    server_addr: str = Field(default="127.0.0.1:8848", validation_alias="NACOS_SERVER_ADDR")
    namespace: str = Field(default="wisdomhub", validation_alias="NACOS_NAMESPACE")
    register_ip: str = Field(default="127.0.0.1", validation_alias="NACOS_REGISTER_IP")
    service_name: str = Field(default="wisdomhub-agent", validation_alias="NACOS_SERVICE_NAME")
    group: str = Field(default="DEFAULT_GROUP", validation_alias="NACOS_GROUP")
    common_data_id: str = Field(
        default="wisdomhub-common.yaml", validation_alias="NACOS_COMMON_DATA_ID"
    )
    env_data_id: str = Field(default="wisdomhub-env.yaml", validation_alias="NACOS_ENV_DATA_ID")


class MqSettings(_Group):
    enabled: bool = Field(default=True, validation_alias="RABBITMQ_ENABLED")
    url: str | None = Field(default=None, validation_alias="RABBITMQ_URL")
    reconnect_interval: float = Field(
        default=5.0, gt=0, validation_alias="RABBITMQ_RECONNECT_INTERVAL"
    )
    analysis_retry_times: int = Field(default=2, ge=0, validation_alias="ANALYSIS_RETRY_TIMES")
    article_analysis_ttl: int = Field(default=1800, ge=1, validation_alias="ARTICLE_ANALYSIS_TTL")

    @field_validator("url", mode="before")
    @classmethod
    def _blank_url_to_none(cls, value: str | None) -> str | None:
        if value is None:
            return None
        value = value.strip()
        return value or None


class LimitsSettings(_Group):
    context_max_turns: int = Field(default=20, ge=1, validation_alias="CONTEXT_MAX_TURNS")
    context_max_tokens: int = Field(default=12000, ge=1, validation_alias="CONTEXT_MAX_TOKENS")
    context_message_max_tokens: int = Field(
        default=2000, ge=1, validation_alias="CONTEXT_MESSAGE_MAX_TOKENS"
    )
    proposal_rate_limit_per_hour: int = Field(
        default=5, ge=1, validation_alias="PROPOSAL_RATE_LIMIT_PER_HOUR"
    )
    internal_connect_timeout: float = Field(
        default=1.0, gt=0, validation_alias="INTERNAL_CONNECT_TIMEOUT"
    )
    internal_query_timeout: float = Field(
        default=3.0, gt=0, validation_alias="INTERNAL_QUERY_TIMEOUT"
    )
    internal_long_timeout: float = Field(
        default=600.0, gt=0, validation_alias="INTERNAL_LONG_TIMEOUT"
    )
    internal_retry_times: int = Field(default=1, ge=0, validation_alias="INTERNAL_RETRY_TIMES")


_GROUPS: tuple[tuple[str, type[_Group]], ...] = (
    ("app", AppSettings),
    ("db", DbSettings),
    ("llm", LlmSettings),
    ("nacos", NacosSettings),
    ("mq", MqSettings),
    ("limits", LimitsSettings),
)

ENV_NAMES = frozenset(
    alias
    for _, group_cls in _GROUPS
    for info in group_cls.model_fields.values()
    if isinstance(alias := info.validation_alias, str)
)

_REASONS = {
    "missing": "缺少必填项",
    "int_parsing": "不是合法整数",
    "int_type": "必须是整数",
    "float_parsing": "不是合法数字",
    "float_type": "必须是数字",
    "bool_parsing": "不是合法布尔值（true/false）",
    "bool_type": "必须是布尔值",
    "string_type": "必须是字符串",
}


class Settings(BaseModel):
    app: AppSettings
    db: DbSettings
    llm: LlmSettings
    nacos: NacosSettings
    mq: MqSettings
    limits: LimitsSettings


def _field_and_alias(group_cls: type[_Group], key: str) -> tuple[str, str]:
    info = group_cls.model_fields.get(key)
    if info is not None:
        alias = info.validation_alias
        return key, alias if isinstance(alias, str) else key.upper()
    for field, info in group_cls.model_fields.items():
        alias = info.validation_alias
        if isinstance(alias, str) and alias == key:
            return field, alias
    return key, key.upper()


def _describe(group: str, group_cls: type[_Group], exc: ValidationError) -> list[str]:
    items: list[str] = []
    for err in exc.errors():
        key = str(err["loc"][0]) if err["loc"] else "?"
        field, alias = _field_and_alias(group_cls, key)
        reason = _REASONS.get(err["type"], str(err["msg"]).removeprefix("Value error, "))
        items.append(f"{alias}（{group}.{field}）: {reason}")
    return items


def load_settings(env_file: str | None = ".env") -> Settings:
    groups: dict[str, _Group] = {}
    problems: list[str] = []
    for name, group_cls in _GROUPS:
        try:
            groups[name] = group_cls(_env_file=env_file)  # type: ignore[call-arg]
        except ValidationError as exc:
            problems.extend(_describe(name, group_cls, exc))
    if problems:
        raise ConfigError("配置错误：\n" + "\n".join(f"- {item}" for item in problems))
    return Settings.model_validate(groups)


@lru_cache
def get_settings() -> Settings:
    return load_settings()
