from fastapi import Request
from langchain_openai import ChatOpenAI

from app.config import Settings


def create_chat_model(settings: Settings, *, streaming: bool = False) -> ChatOpenAI:
    return ChatOpenAI(
        model=settings.chat_model,
        base_url=settings.chat_base_url,
        api_key=settings.chat_api_key,
        temperature=settings.chat_temperature,
        timeout=settings.chat_timeout,
        max_retries=0,
        streaming=streaming,
    )


def get_chat_model(request: Request) -> ChatOpenAI:
    model = getattr(request.app.state, "chat_model", None)
    if model is None:
        raise RuntimeError("chat model 未初始化")
    return model
