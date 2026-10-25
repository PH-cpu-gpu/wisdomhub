import asyncio
import contextlib
import json
import logging
from collections.abc import AsyncIterator
from dataclasses import dataclass
from typing import Any

from fastapi.sse import ServerSentEvent

from app.degradation import friendly_error
from app.response import ErrorCode

log = logging.getLogger(__name__)

META = "meta"
DELTA = "delta"
DONE = "done"
ERROR = "error"


@dataclass(frozen=True, slots=True)
class StreamEvent:
    """producer → 传输层的事件；terminal 为真时发完即关流。"""

    name: str
    data: dict[str, Any]
    terminal: bool = False


def meta_event(conversation_id: str, message_id: str) -> StreamEvent:
    return StreamEvent(META, {"conversationId": conversation_id, "messageId": message_id})


def delta_event(text: str) -> StreamEvent:
    return StreamEvent(DELTA, {"text": text})


def done_event(finish_reason: str = "stop") -> StreamEvent:
    return StreamEvent(DONE, {"finishReason": finish_reason}, terminal=True)


def error_event(code: ErrorCode | int, message: str) -> StreamEvent:
    return StreamEvent(ERROR, {"code": int(code), "message": message}, terminal=True)


def encode(event: StreamEvent) -> ServerSentEvent:
    data = json.dumps(event.data, ensure_ascii=False, separators=(",", ":"))
    return ServerSentEvent(event=event.name, raw_data=data)


async def guard_events(
    events: AsyncIterator[StreamEvent],
    *,
    idle_timeout: float,
) -> AsyncIterator[StreamEvent]:
    """逐个转发事件，并补上空闲超时、终态收口与取消清理。"""
    agen = events.__aiter__()
    try:
        while True:
            try:
                event = await asyncio.wait_for(agen.__anext__(), timeout=idle_timeout)
            except StopAsyncIteration:
                yield error_event(ErrorCode.INTERNAL_ERROR, "AI 服务响应异常")
                return
            except TimeoutError:
                log.warning("AI 流空闲超时（%ss）", idle_timeout)
                yield error_event(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务响应超时")
                return
            except Exception as exc:
                friendly = friendly_error(exc)
                if friendly is not None:
                    code, message = friendly
                    log.warning("AI 流降级 type=%s: %s", type(exc).__name__, exc)
                    yield error_event(code, message)
                else:
                    log.exception("AI 流生成失败")
                    yield error_event(ErrorCode.INTERNAL_ERROR, "AI 服务内部异常")
                return
            yield event
            if event.terminal:
                return
    finally:
        with contextlib.suppress(Exception):
            await agen.aclose()
