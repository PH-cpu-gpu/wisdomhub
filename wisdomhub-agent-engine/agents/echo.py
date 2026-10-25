import asyncio
from collections.abc import AsyncIterator

from agents.base import ChatContext
from api.sse import StreamEvent, delta_event, done_event, sources_event


class EchoStreamer:
    """B3.3 联调用的临时 producer，B3.18 接入真实工作流后删除。"""

    async def stream(self, ctx: ChatContext) -> AsyncIterator[StreamEvent]:
        yield delta_event("收到问题：")
        await asyncio.sleep(0.2)
        yield delta_event(ctx.question)
        await asyncio.sleep(0.2)
        yield sources_event([])
        yield done_event()
