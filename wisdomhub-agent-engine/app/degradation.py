from openai import OpenAIError

from app.response import ErrorCode
from tools.internal_client import InternalClientError


def friendly_error(exc: Exception) -> tuple[ErrorCode, str] | None:
    if isinstance(exc, InternalClientError):
        return ErrorCode.SERVICE_UNAVAILABLE, "检索服务暂不可用"
    if isinstance(exc, OpenAIError):
        return ErrorCode.SERVICE_UNAVAILABLE, "AI 服务暂不可用"
    return None
