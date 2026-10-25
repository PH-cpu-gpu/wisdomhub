import contextlib
import logging
from collections.abc import AsyncIterator

import anyio
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from agents.base import ChatContext, ChatStreamer, TurnTrace
from api.sse import DELTA, DONE, ERROR, StreamEvent, error_event
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
        persisted = False
        inner = self._inner.stream(ctx)
        try:
            async for event in inner:
                if event.name == DELTA:
                    parts.append(str(event.data.get("text", "")))

                if event.name == DONE:
                    try:
                        await self._save("done", "".join(parts))
                    except Exception:
                        log.exception("assistant 落库失败")
                        persisted = True
                        yield error_event(ErrorCode.SERVICE_UNAVAILABLE, "会话保存失败")
                        return
                    persisted = True
                    yield event
                    return

                if event.name == ERROR:
                    await self._save_quietly("error", "".join(parts))
                    persisted = True
                    yield event
                    return

                yield event

            await self._save_quietly("error", "".join(parts))
            persisted = True
        except Exception:
            log.exception("assistant 落库前 producer 异常")
            await self._save_quietly("error", "".join(parts))
            persisted = True
            raise
        finally:
            with anyio.CancelScope(shield=True):
                with contextlib.suppress(Exception):
                    await inner.aclose()
                if not persisted:
                    await self._save_quietly("aborted", "".join(parts))

    async def _save(self, status: str, content: str) -> None:
        async with self._session_factory() as session:
            async with session.begin():
                rowcount = await repository.finish_assistant_message(
                    session,
                    row_id=self._turn.assistant_row_id,
                    user_id=self._turn.user_id,
                    content=content,
                    status=status,
                    steps=list(self._trace.steps) if self._trace and self._trace.steps else None,
                )
        if rowcount == 0:
            log.warning("assistant 行不存在或归属不符: %s", self._turn.assistant_row_id)

    async def _save_quietly(self, status: str, content: str) -> None:
        try:
            await self._save(status, content)
        except Exception:
            log.exception("assistant 状态落库失败, status=%s", status)
