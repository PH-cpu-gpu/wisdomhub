"""应用启动与关停编排。"""

import logging
from collections.abc import AsyncIterator, Awaitable, Callable, Sequence
from contextlib import AbstractAsyncContextManager, asynccontextmanager
from dataclasses import dataclass

from fastapi import FastAPI

from app.core.config import Settings, get_settings
from app.core.deps import AppDeps

log = logging.getLogger(__name__)

Startup = Callable[[AppDeps], Awaitable[None]]
Lifespan = Callable[[FastAPI], AbstractAsyncContextManager[None]]


@dataclass(frozen=True, slots=True)
class BuildStep:
    name: str
    run: Startup


async def build_deps(settings: Settings, steps: Sequence[BuildStep]) -> AppDeps:
    deps = AppDeps(settings=settings)
    try:
        for step in steps:
            log.info("启动依赖: %s", step.name)
            await step.run(deps)
    except BaseException:
        await deps.aclose()
        raise
    return deps


def create_lifespan(steps: Sequence[BuildStep] = ()) -> Lifespan:
    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        deps = await build_deps(get_settings(), steps)
        app.state.deps = deps
        try:
            yield
        finally:
            await deps.aclose()

    return lifespan


lifespan = create_lifespan()
