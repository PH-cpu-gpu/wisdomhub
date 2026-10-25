import contextlib
import logging
from collections.abc import AsyncIterator
from typing import Any

import anyio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from agents.base import ChatContext, ChatStreamer, TurnTrace
from api.sse import DELTA, DONE, ERROR, SOURCES, StreamEvent, error_event
from app.response import ErrorCode
from db import repository
from db.turn import TurnContext

log = logging.getLogger(__name__)


class PersistingStreamer:
    """在 producer 外层攒内容，按终态更新 assistant 行。"""

    def __init__(
        self,
        inner: ChatStreamer,
        *,
        session_factory: async_sessionmaker[AsyncSession],
        turn: TurnContext,
        trace: TurnTrace | None = None,
    ) -> None:
        self._inner = inner
        self._session_factory = session_factory
        self._turn = turn
        self._trace = trace

    async def stream(self, ctx: ChatContext) -> AsyncIterator[StreamEvent]:
        parts: list[str] = []
        sources: list[dict[str, Any]] = []
        persisted = False
        inner = self._inner.stream(ctx)
        try:
            async for event in inner:
                if event.name == DELTA:
                    parts.append(str(event.data.get("text", "")))
                elif event.name == SOURCES:
                    items = event.data.get("items")
                    sources = list(items) if isinstance(items, list) else []

                if event.name == DONE:
                    try:
                        await self._save("done", "".join(parts), sources)
                    except Exception:
                        log.exception("assistant 落库失败")
                        persisted = True
                        yield error_event(ErrorCode.SERVICE_UNAVAILABLE, "会话保存失败")
                        return
                    persisted = True
                    yield event
                    return

                if event.name == ERROR:
                    await self._save_quietly("error", "".join(parts), sources)
                    persisted = True
                    yield event
                    return

                yield event

            await self._save_quietly("error", "".join(parts), sources)
            persisted = True
        except Exception:
            log.exception("assistant 落库前 producer 异常")
            await self._save_quietly("error", "".join(parts), sources)
            persisted = True
            raise
        finally:
            with anyio.CancelScope(shield=True):
                with contextlib.suppress(Exception):
                    await inner.aclose()
                if not persisted:
                    await self._save_quietly("aborted", "".join(parts), sources)

    async def _save(self, status: str, content: str, sources: list[dict[str, Any]]) -> None:
        async with self._session_factory() as session:
            async with session.begin():
                rowcount = await repository.finish_assistant_message(
                    session,
                    row_id=self._turn.assistant_row_id,
                    user_id=self._turn.user_id,
                    content=content,
                    sources=sources,
                    status=status,
                    steps=list(self._trace.steps) if self._trace and self._trace.steps else None,
                )
        if rowcount == 0:
            log.warning("assistant 行不存在或归属不符: %s", self._turn.assistant_row_id)

    async def _save_quietly(self, status: str, content: str, sources: list[dict[str, Any]]) -> None:
        try:
            await self._save(status, content, sources)
        except Exception:
            log.exception("assistant 状态落库失败, status=%s", status)
