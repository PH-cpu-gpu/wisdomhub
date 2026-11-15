from typing import Any

from v2.nacos import (
    ConfigParam,
    DeregisterInstanceParam,
    ListInstanceParam,
    RegisterInstanceParam,
)


class FakeInstance:
    def __init__(self, ip: str, port: int) -> None:
        self.ip = ip
        self.port = port


class FakeNaming:
    def __init__(self, instances: list[FakeInstance] | None = None) -> None:
        self.instances = instances or []
        self.registered: RegisterInstanceParam | None = None
        self.deregistered: DeregisterInstanceParam | None = None
        self.list_request: ListInstanceParam | None = None
        self.shutdown_called = False

    async def register_instance(self, request: RegisterInstanceParam) -> bool:
        self.registered = request
        return True

    async def deregister_instance(self, request: DeregisterInstanceParam) -> bool:
        self.deregistered = request
        return True

    async def list_instances(self, request: ListInstanceParam) -> list[Any]:
        self.list_request = request
        return list(self.instances)

    async def shutdown(self) -> None:
        self.shutdown_called = True


class FakeConfig:
    def __init__(
        self,
        values: dict[tuple[str, str], str] | None = None,
        error: Exception | None = None,
    ) -> None:
        self.values = values or {}
        self.error = error
        self.requests: list[ConfigParam] = []
        self.shutdown_called = False

    async def get_config(self, param: ConfigParam) -> str:
        self.requests.append(param)
        if self.error is not None:
            raise self.error
        return self.values.get((param.data_id, param.group), "")

    async def shutdown(self) -> None:
        self.shutdown_called = True
