"""模型 profile：chat、analysis、reflection 的构造与懒加载。"""

from collections.abc import Callable
from dataclasses import dataclass
from enum import StrEnum

from langchain_openai import ChatOpenAI
from pydantic import SecretStr

from app.core.config import ConfigError, LlmSettings, Settings


class LlmProfile(StrEnum):
    """模型用途。"""

    CHAT = "chat"
    ANALYSIS = "analysis"
    REFLECTION = "reflection"


@dataclass(frozen=True, slots=True)
class _ProfileSpec:
    streaming: bool
    stream_usage: bool
    timeout: Callable[[LlmSettings], float]


_SPECS: dict[LlmProfile, _ProfileSpec] = {
    LlmProfile.CHAT: _ProfileSpec(True, True, lambda llm: llm.timeout),
    LlmProfile.ANALYSIS: _ProfileSpec(False, False, lambda llm: llm.analysis_timeout),
    LlmProfile.REFLECTION: _ProfileSpec(False, False, lambda llm: llm.reflection_timeout),
}

_REQUIRED = (
    ("CHAT_MODEL", "model"),
    ("CHAT_BASE_URL", "base_url"),
    ("CHAT_API_KEY", "api_key"),
)


def _require_config(llm: LlmSettings, profile: LlmProfile) -> None:
    missing = [name for name, attr in _REQUIRED if not getattr(llm, attr).strip()]
    if missing:
        raise ConfigError(f"LLM 配置缺失（{profile.value}）: {', '.join(missing)}")


def build_llm(settings: Settings, profile: LlmProfile) -> ChatOpenAI:
    """按 profile 构造模型实例，重试与温度统一由调用方决定。"""
    llm = settings.llm
    _require_config(llm, profile)
    spec = _SPECS[profile]
    return ChatOpenAI(
        model=llm.model,
        base_url=llm.base_url,
        api_key=SecretStr(llm.api_key),
        temperature=llm.temperature,
        timeout=spec.timeout(llm),
        max_retries=0,
        streaming=spec.streaming,
        stream_usage=spec.stream_usage,
    )


class LlmRegistry:
    """按 profile 懒创建并复用模型实例。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._models: dict[LlmProfile, ChatOpenAI] = {}

    def get(self, profile: LlmProfile) -> ChatOpenAI:
        model = self._models.get(profile)
        if model is None:
            model = build_llm(self._settings, profile)
            self._models[profile] = model
        return model

    @property
    def created(self) -> tuple[LlmProfile, ...]:
        return tuple(self._models)
