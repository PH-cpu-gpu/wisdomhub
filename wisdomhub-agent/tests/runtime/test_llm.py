"""模型 profile 的构造、懒加载与配置校验。"""

import pytest

from app.core.config import ConfigError, Settings
from app.runtime.llm import LlmProfile, LlmRegistry, build_llm


def test_chat_profile_streams(settings: Settings) -> None:
    llm = build_llm(settings, LlmProfile.CHAT)

    assert llm.model_name == settings.llm.model
    assert llm.temperature == settings.llm.temperature
    assert llm.request_timeout == settings.llm.timeout
    assert llm.streaming is True
    assert llm.stream_usage is True
    assert llm.max_retries == 0


def test_analysis_profile_uses_long_timeout(settings: Settings) -> None:
    llm = build_llm(settings, LlmProfile.ANALYSIS)

    assert llm.request_timeout == settings.llm.analysis_timeout
    assert llm.streaming is False
    assert llm.stream_usage is False


def test_reflection_profile_uses_short_timeout(settings: Settings) -> None:
    llm = build_llm(settings, LlmProfile.REFLECTION)

    assert llm.request_timeout == settings.llm.reflection_timeout
    assert llm.streaming is False


def test_registry_creates_only_used_profiles(settings: Settings) -> None:
    registry = LlmRegistry(settings)
    assert registry.created == ()

    chat = registry.get(LlmProfile.CHAT)
    assert registry.created == (LlmProfile.CHAT,)
    assert registry.get(LlmProfile.CHAT) is chat

    registry.get(LlmProfile.ANALYSIS)
    assert registry.created == (LlmProfile.CHAT, LlmProfile.ANALYSIS)


def test_missing_config_reports_profile(settings: Settings) -> None:
    broken = settings.model_copy(update={"llm": settings.llm.model_copy(update={"model": "  "})})

    with pytest.raises(ConfigError) as excinfo:
        build_llm(broken, LlmProfile.CHAT)

    assert "chat" in str(excinfo.value)
    assert "CHAT_MODEL" in str(excinfo.value)
