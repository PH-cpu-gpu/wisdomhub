import json
import logging
import random
from typing import Any

import httpx
from fastapi import Request
from v2.nacos import NacosNamingService
from v2.nacos.naming.model.naming_param import ListInstanceParam

from app.config import Settings
from app.response import BusinessError, ErrorCode
from registry.naming import GROUP_NAME
from security.internal_sign import PATH_PREFIX, InternalSigner

log = logging.getLogger(__name__)


class InternalClientError(Exception):
    """kind: unavailable / auth / http / business / protocol。"""

    def __init__(
        self,
        kind: str,
        message: str,
        *,
        service: str,
        path: str,
        status: int | None = None,
        code: int | None = None,
    ) -> None:
        super().__init__(message)
        self.kind = kind
        self.service = service
        self.path = path
        self.status = status
        self.code = code


class InternalUnavailable(InternalClientError):
    def __init__(self, message: str, *, service: str, path: str) -> None:
        super().__init__("unavailable", message, service=service, path=path)


class InternalClient:
    def __init__(
        self,
        settings: Settings,
        naming: NacosNamingService,
        signer: InternalSigner,
        http: httpx.AsyncClient,
    ) -> None:
        self._settings = settings
        self._naming = naming
        self._signer = signer
        self._http = http

    async def get(
        self,
        service: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        timeout: float | None = None,
        retry: bool = True,
    ) -> Any:
        return await self._request("GET", service, path, params=params, body=None,
                                   timeout=timeout, retry=retry)

    async def post(
        self,
        service: str,
        path: str,
        *,
        params: dict[str, Any] | None = None,
        json_body: dict[str, Any] | None = None,
        timeout: float | None = None,
        retry: bool = False,
    ) -> Any:
        body = None
        if json_body is not None:
            body = json.dumps(
                json_body, ensure_ascii=False, separators=(",", ":"), allow_nan=False
            ).encode("utf-8")
        return await self._request("POST", service, path, params=params, body=body,
                                   timeout=timeout, retry=retry)

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
        timeout: float | None,
        retry: bool,
    ) -> Any:
        if not (path == PATH_PREFIX or path.startswith(PATH_PREFIX + "/")):
            raise ValueError(f"只允许调用 {PATH_PREFIX}/**，收到: {path}")

        attempts = 1 + (self._settings.internal_retry_times if retry else 0)
        for attempt in range(attempts):
            try:
                url = await self._resolve_url(service, path, params)
            except InternalUnavailable:
                if attempt + 1 < attempts:
                    continue
                raise
            headers = self._signer.headers(
                method, url.raw_path.decode("ascii"), body or b""
            )
            if body is not None:
                headers["Content-Type"] = "application/json"

            try:
                response = await self._http.request(
                    method, url, content=body, headers=headers, timeout=self._timeout(timeout)
                )
            except httpx.TransportError as exc:
                if attempt + 1 < attempts:
                    log.warning("内部调用失败，重试 service=%s %s %s: %s",
                                service, method, path, type(exc).__name__)
                    continue
                raise InternalUnavailable(
                    f"内部服务不可达: {service}", service=service, path=path
                ) from exc

            if response.status_code >= 500 and attempt + 1 < attempts:
                log.warning("内部服务 %s 返回 %s，重试 %s %s",
                            service, response.status_code, method, path)
                continue

            return self._parse(service, path, response)

        raise InternalUnavailable(f"内部服务不可达: {service}", service=service, path=path)

    async def _resolve_url(
        self, service: str, path: str, params: dict[str, Any] | None
    ) -> httpx.URL:
        try:
            instances = await self._naming.list_instances(
                ListInstanceParam(service_name=service, group_name=GROUP_NAME, healthy_only=True)
            )
        except Exception as exc:
            raise InternalUnavailable(
                f"服务发现失败: {service}", service=service, path=path
            ) from exc
        if not instances:
            raise InternalUnavailable(
                f"没有健康的实例: {service}", service=service, path=path
            )

        instance = random.choice(instances)
        url = httpx.URL(f"http://{instance.ip}:{instance.port}{path}")
        if params:
            url = url.copy_with(params=params)
        return url

    def _timeout(self, timeout: float | None) -> httpx.Timeout:
        read = self._settings.internal_query_timeout if timeout is None else timeout
        return httpx.Timeout(
            connect=self._settings.internal_connect_timeout,
            read=read,
            write=read,
            pool=read,
        )

    @staticmethod
    def _parse(service: str, path: str, response: httpx.Response) -> Any:
        try:
            payload = response.json()
        except ValueError as exc:
            raise InternalClientError(
                "protocol",
                f"内部接口响应不是 JSON: HTTP {response.status_code}",
                service=service,
                path=path,
                status=response.status_code,
            ) from exc

        if not isinstance(payload, dict) or not isinstance(payload.get("code"), int):
            raise InternalClientError(
                "protocol",
                f"内部接口响应不是 R: HTTP {response.status_code}",
                service=service,
                path=path,
                status=response.status_code,
            )

        code = payload["code"]
        message = payload.get("message") or "内部接口失败"
        if response.status_code == 401 or code == 401:
            log.warning("内部签名被拒 service=%s path=%s，检查 Nacos 密钥是否一致", service, path)
            raise InternalClientError(
                "auth", "服务签名校验失败", service=service, path=path,
                status=response.status_code, code=code,
            )
        if response.status_code != 200:
            raise InternalClientError(
                "http", message, service=service, path=path,
                status=response.status_code, code=code,
            )
        if code != 200:
            raise InternalClientError(
                "business", message, service=service, path=path, code=code
            )
        return payload.get("data")


def create_internal_client(
    settings: Settings, naming: NacosNamingService, signer: InternalSigner
) -> InternalClient:
    http = httpx.AsyncClient(
        trust_env=False,
        follow_redirects=False,
        limits=httpx.Limits(max_connections=50, max_keepalive_connections=10),
    )
    return InternalClient(settings, naming, signer, http)


def get_internal_client(request: Request) -> InternalClient:
    client = getattr(request.app.state, "internal_client", None)
    if client is None:
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "内部服务调用未启用")
    return client
