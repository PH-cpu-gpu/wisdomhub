"""能力层共享的类型：内部调用 Protocol 与响应模型基类。"""

from typing import Any, Protocol

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class InternalGetter(Protocol):
    async def get(
        self,
        service: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        read_timeout: float | None = None,
        retry: bool = True,
    ) -> Any: ...


class InternalPoster(Protocol):
    async def post(
        self,
        service: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json_body: dict[str, Any] | None = None,
        read_timeout: float | None = None,
        retry: bool = False,
    ) -> Any: ...


class CapabilityModel(BaseModel):
    model_config = ConfigDict(
        frozen=True,
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )
