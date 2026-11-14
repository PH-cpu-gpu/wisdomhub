"""依赖容器：任务取消与逆序清理。"""

import asyncio
import logging
from typing import Annotated

import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.core.deps import AppDeps, get_deps


async def test_aclose_runs_cleanups_in_reverse(settings: Settings) -> None:
    deps = AppDeps(settings=settings)
    closed: list[str] = []

    async def close(name: str) -> None:
        closed.append(name)

    deps.on_close("a", lambda: close("a"))
    deps.on_close("b", lambda: close("b"))

    await deps.aclose()

    assert closed == ["b", "a"]


async def test_aclose_cancels_tasks(settings: Settings) -> None:
    deps = AppDeps(settings=settings)
    task = deps.spawn(asyncio.sleep(60), name="idle")
    await asyncio.sleep(0)

    await deps.aclose()

    assert task.cancelled()


async def test_task_exception_is_logged(
    settings: Settings, caplog: pytest.LogCaptureFixture
) -> None:
    deps = AppDeps(settings=settings)

    async def boom() -> None:
        raise RuntimeError("task failed")

    with caplog.at_level(logging.ERROR, logger="app.core.deps"):
        deps.spawn(boom(), name="boom")
        await asyncio.sleep(0)
        await asyncio.sleep(0)

    assert "task failed" in caplog.text
    assert not deps.tasks


async def test_aclose_isolates_cleanup_failure(
    settings: Settings, caplog: pytest.LogCaptureFixture
) -> None:
    deps = AppDeps(settings=settings)
    closed: list[str] = []

    async def fail() -> None:
        raise RuntimeError("close failed")

    async def ok() -> None:
        closed.append("ok")

    deps.on_close("fail", fail)
    deps.on_close("ok", ok)

    with caplog.at_level(logging.WARNING, logger="app.core.deps"):
        await deps.aclose()

    assert closed == ["ok"]
    assert "close failed" in caplog.text


async def test_aclose_is_idempotent(settings: Settings) -> None:
    deps = AppDeps(settings=settings)
    calls: list[int] = []

    async def cleanup() -> None:
        calls.append(1)

    deps.on_close("x", cleanup)

    await deps.aclose()
    await deps.aclose()

    assert calls == [1]


def test_get_deps_from_request(settings: Settings) -> None:
    deps = AppDeps(settings=settings)
    app = FastAPI()
    app.state.deps = deps

    @app.get("/env")
    def env(current: Annotated[AppDeps, Depends(get_deps)]) -> dict[str, str]:
        return {"env": current.settings.app.env}

    with TestClient(app) as client:
        assert client.get("/env").json() == {"env": "local"}
