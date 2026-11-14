"""生命周期：启动顺序、关停顺序与失败回滚。"""

from typing import Annotated

import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from app.core.config import Settings
from app.core.deps import AppDeps, get_deps
from app.core.lifespan import BuildStep, build_deps, create_lifespan


def _step(name: str, events: list[str]) -> BuildStep:
    async def run(deps: AppDeps) -> None:
        events.append(f"start-{name}")

        async def close() -> None:
            events.append(f"stop-{name}")

        deps.on_close(name, close)

    return BuildStep(name, run)


async def test_build_runs_steps_in_order(settings: Settings) -> None:
    events: list[str] = []

    await build_deps(settings, [_step("a", events), _step("b", events)])

    assert events == ["start-a", "start-b"]


async def test_build_failure_rolls_back(settings: Settings) -> None:
    events: list[str] = []

    async def first(deps: AppDeps) -> None:
        events.append("start-first")

        async def close() -> None:
            events.append("stop-first")

        deps.on_close("first", close)

    async def second(_: AppDeps) -> None:
        events.append("start-second")
        raise RuntimeError("startup failed")

    with pytest.raises(RuntimeError, match="startup failed"):
        await build_deps(settings, [BuildStep("first", first), BuildStep("second", second)])

    assert events == ["start-first", "start-second", "stop-first"]


def test_lifespan_closes_in_reverse_order(
    settings: Settings, monkeypatch: pytest.MonkeyPatch
) -> None:
    events: list[str] = []
    monkeypatch.setattr("app.core.lifespan.get_settings", lambda: settings)
    app = FastAPI(lifespan=create_lifespan([_step("a", events), _step("b", events)]))

    @app.get("/deps")
    def deps_route(current: Annotated[AppDeps, Depends(get_deps)]) -> dict[str, str]:
        return {"env": current.settings.app.env}

    with TestClient(app) as client:
        assert client.get("/deps").json() == {"env": "local"}
        assert events == ["start-a", "start-b"]

    assert events == ["start-a", "start-b", "stop-b", "stop-a"]
