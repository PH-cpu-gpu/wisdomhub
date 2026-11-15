"""内部 HTTP 客户端：服务发现、HMAC 签名、超时、重试与错误映射。"""

import json
import logging
import random
from dataclasses import dataclass
from typing import Any, Literal, Protocol

import httpx

from app.core.config import LimitsSettings, Settings
from app.infra.security.internal_sign import InternalSigner

log = logging.getLogger(__name__)

PATH_PREFIX = "/internal"
ErrorKind = Literal["unavailable", "auth", "not_found", "http", "business", "protocol"]


@dataclass(frozen=True, slots=True)
class ServiceInstance:
    host: str
    port: int


class ServiceResolver(Protocol):
    async def discover(self, service: str) -> list[ServiceInstance]: ...


class InternalClientError(Exception):
    """内部调用失败；kind 用于调用方区分降级策略。"""

    def __init__(
        self,
        kind: ErrorKind,
        message: str,
        *,
        service: str,
        path: str,
        status: int | None = None,
        code: int | None = None,
        data: Any = None,
        retryable: bool = False,
    ) -> None:
        super().__init__(message)
        self.kind = kind
        self.service = service
        self.path = path
        self.status = status
        self.code = code
        self.data = data
        self.retryable = retryable


class InternalUnavailable(InternalClientError):
    def __init__(self, message: str, *, service: str, path: str) -> None:
        super().__init__(
            "unavailable",
            message,
            service=service,
            path=path,
            retryable=True,
        )


class InternalAuthError(InternalClientError):
    def __init__(
        self,
        message: str,
        *,
        service: str,
        path: str,
        status: int | None = None,
        code: int | None = None,
    ) -> None:
        super().__init__(
            "auth",
            message,
            service=service,
            path=path,
            status=status,
            code=code,
        )


class InternalNotFound(InternalClientError):
    def __init__(
        self,
        message: str,
        *,
        service: str,
        path: str,
        status: int | None = None,
        code: int | None = None,
    ) -> None:
        super().__init__(
            "not_found",
            message,
            service=service,
            path=path,
            status=status,
            code=code,
        )


class InternalServerError(InternalClientError):
    def __init__(
        self,
        message: str,
        *,
        service: str,
        path: str,
        status: int | None = None,
        code: int | None = None,
        data: Any = None,
    ) -> None:
        super().__init__(
            "http",
            message,
            service=service,
            path=path,
            status=status,
            code=code,
            data=data,
            retryable=True,
        )


class InternalHttpError(InternalClientError):
    def __init__(
        self,
        message: str,
        *,
        service: str,
        path: str,
        status: int | None = None,
        code: int | None = None,
        data: Any = None,
    ) -> None:
        super().__init__(
            "http",
            message,
            service=service,
            path=path,
            status=status,
            code=code,
            data=data,
        )


class InternalBusinessError(InternalClientError):
    def __init__(
        self,
        message: str,
        *,
        service: str,
        path: str,
        status: int | None = None,
        code: int | None = None,
        data: Any = None,
    ) -> None:
        super().__init__(
            "business",
            message,
            service=service,
            path=path,
            status=status,
            code=code,
            data=data,
        )


class InternalProtocolError(InternalClientError):
    def __init__(
        self,
        message: str,
        *,
        service: str,
        path: str,
        status: int | None = None,
    ) -> None:
        super().__init__(
            "protocol",
            message,
            service=service,
            path=path,
            status=status,
        )


class InternalClient:
    def __init__(
        self,
        limits: LimitsSettings,
        resolver: ServiceResolver,
        signer: InternalSigner,
        http: httpx.AsyncClient,
    ) -> None:
        self._limits = limits
        self._resolver = resolver
        self._signer = signer
        self._http = http

    async def get(
        self,
        service: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        read_timeout: float | None = None,
        retry: bool = True,
    ) -> Any:
        return await self._request(
            "GET",
            service,
            path,
            params=params,
            body=None,
            read_timeout=read_timeout,
            retry=retry,
        )

    async def post(
        self,
        service: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json_body: dict[str, Any] | None = None,
        read_timeout: float | None = None,
        retry: bool = False,
    ) -> Any:
        body = None
        if json_body is not None:
            body = json.dumps(
                json_body,
                ensure_ascii=False,
                separators=(",", ":"),
                allow_nan=False,
            ).encode("utf-8")
        return await self._request(
            "POST",
            service,
            path,
            params=params,
            body=body,
            read_timeout=read_timeout,
            retry=retry,
        )

    async def aclose(self) -> None:
        await self._http.aclose()

    async def _request(
        self,
        method: str,
        service: str,
        path: str,
        *,
        params: dict[str, Any] | None,
        body: bytes | None,
        read_timeout: float | None,
        retry: bool,
    ) -> Any:
        if not (path == PATH_PREFIX or path.startswith(PATH_PREFIX + "/")):
            raise ValueError(f"只允许调用 {PATH_PREFIX}/**，收到: {path}")

        attempts = 1 + (self._limits.internal_retry_times if retry else 0)
        for attempt in range(attempts):
            try:
                instance = await self._resolve(service, path)
            except InternalUnavailable:
                if attempt + 1 < attempts:
                    log.warning("内部服务发现失败，重试 service=%s %s %s", service, method, path)
                    continue
                raise

            url = self._build_url(instance, path, params)
            headers = self._signer.headers(method, url.raw_path.decode("ascii"), body or b"")
            if body is not None:
                headers["Content-Type"] = "application/json"

            try:
                response = await self._http.request(
                    method,
                    url,
                    content=body,
                    headers=headers,
                    timeout=self._timeout(read_timeout),
                )
            except httpx.TransportError as exc:
                if attempt + 1 < attempts:
                    log.warning(
                        "内部调用失败，重试 service=%s %s %s: %s",
                        service,
                        method,
                        path,
                        type(exc).__name__,
                    )
                    continue
                raise InternalUnavailable(
                    f"内部服务不可达: {service}",
                    service=service,
                    path=path,
                ) from exc

            if response.status_code >= 500 and attempt + 1 < attempts:
                log.warning(
                    "内部服务 %s 返回 %s，重试 %s %s",
                    service,
                    response.status_code,
                    method,
                    path,
                )
                continue

            return self._parse(service, path, response)

        raise InternalUnavailable(
            f"内部服务不可达: {service}",
            service=service,
            path=path,
        )

    async def _resolve(self, service: str, path: str) -> ServiceInstance:
        try:
            instances = await self._resolver.discover(service)
        except Exception as exc:
            raise InternalUnavailable(
                f"服务发现失败: {service}",
                service=service,
                path=path,
            ) from exc
        if not instances:
            raise InternalUnavailable(
                f"没有健康的实例: {service}",
                service=service,
                path=path,
            )
        return random.choice(instances)

    @staticmethod
    def _build_url(
        instance: ServiceInstance,
        path: str,
        params: dict[str, Any] | None,
    ) -> httpx.URL:
        url = httpx.URL(f"http://{instance.host}:{instance.port}{path}")
        if params:
            url = url.copy_with(params=params)
        return url

    def _timeout(self, read_timeout: float | None) -> httpx.Timeout:
        read = self._limits.internal_query_timeout if read_timeout is None else read_timeout
        return httpx.Timeout(
            connect=self._limits.internal_connect_timeout,
            read=read,
            write=read,
            pool=read,
        )

    @staticmethod
    def _parse(service: str, path: str, response: httpx.Response) -> Any:
        status = response.status_code
        payload = _payload(response)
        code = _code(payload)

        if status == 401:
            raise InternalAuthError(
                _message(payload, "服务签名校验失败"),
                service=service,
                path=path,
                status=status,
                code=code,
            )
        if status == 404:
            raise InternalNotFound(
                _message(payload, "内部资源不存在"),
                service=service,
                path=path,
                status=status,
                code=code,
            )
        if status >= 500:
            raise InternalServerError(
                _message(payload, "内部服务异常"),
                service=service,
                path=path,
                status=status,
                code=code,
                data=_data(payload),
            )
        if status != 200:
            raise InternalHttpError(
                _message(payload, "内部接口调用失败"),
                service=service,
                path=path,
                status=status,
                code=code,
                data=_data(payload),
            )
        if payload is None or code is None:
            raise InternalProtocolError(
                "内部接口响应不是 R",
                service=service,
                path=path,
                status=status,
            )
        if code == 401:
            raise InternalAuthError(
                "服务签名校验失败",
                service=service,
                path=path,
                status=status,
                code=code,
            )
        if code == 404:
            raise InternalNotFound(
                _message(payload, "内部资源不存在"),
                service=service,
                path=path,
                status=status,
                code=code,
            )
        if code != 200:
            raise InternalBusinessError(
                _message(payload, "内部接口失败"),
                service=service,
                path=path,
                status=status,
                code=code,
                data=_data(payload),
            )
        return _data(payload)


def _payload(response: httpx.Response) -> Any:
    try:
        return response.json()
    except ValueError:
        return None


def _code(payload: Any) -> int | None:
    if isinstance(payload, dict):
        code = payload.get("code")
        if isinstance(code, int):
            return code
    return None


def _data(payload: Any) -> Any:
    return payload.get("data") if isinstance(payload, dict) else None


def _message(payload: Any, default: str) -> str:
    if isinstance(payload, dict):
        message = payload.get("message")
        if isinstance(message, str) and message:
            return message
    return default


def create_internal_client(
    settings: Settings,
    resolver: ServiceResolver,
    signer: InternalSigner,
) -> InternalClient:
    http = httpx.AsyncClient(
        trust_env=False,
        follow_redirects=False,
        limits=httpx.Limits(max_connections=50, max_keepalive_connections=10),
    )
    return InternalClient(settings.limits, resolver, signer, http)
