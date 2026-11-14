"""依赖容器与关停栈。"""

import asyncio
import logging
from collections.abc import Awaitable, Callable, Coroutine
from dataclasses import dataclass, field
from typing import Any

from fastapi import Request

from app.core.config import Settings

log = logging.getLogger(__name__)

Cleanup = Callable[[], Awaitable[None]]


@dataclass(slots=True)
class AppDeps:
    settings: Settings
    tasks: set[asyncio.Task[Any]] = field(default_factory=set)
    cleanups: list[tuple[str, Cleanup]] = field(default_factory=list)

    def spawn(self, coro: Coroutine[Any, Any, Any], *, name: str) -> asyncio.Task[Any]:
        task = asyncio.create_task(coro, name=name)
        self.tasks.add(task)
        task.add_done_callback(self._task_done)
        return task

    def _task_done(self, task: asyncio.Task[Any]) -> None:
        self.tasks.discard(task)
        if task.cancelled():
            return
        exc = task.exception()
        if exc is not None:
            log.error("后台任务异常: %s", task.get_name(), exc_info=exc)

    def on_close(self, name: str, cleanup: Cleanup) -> None:
        self.cleanups.append((name, cleanup))

    async def aclose(self) -> None:
        tasks = list(self.tasks)
        self.tasks.clear()
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)

        cleanups = self.cleanups
        self.cleanups = []
        for name, cleanup in reversed(cleanups):
            try:
                await cleanup()
            except Exception:
                log.warning("资源关闭失败: %s", name, exc_info=True)


def get_deps(request: Request) -> AppDeps:
    deps = getattr(request.app.state, "deps", None)
    if not isinstance(deps, AppDeps):
        raise RuntimeError("AppDeps 未初始化")
    return deps
