import base64
import hashlib
import hmac
import secrets
import time

import yaml
from v2.nacos import NacosConfigService
from v2.nacos.config.model.config_param import ConfigParam

HEADER_SERVICE_NAME = "X-Service-Name"
HEADER_TIMESTAMP = "X-Timestamp"
HEADER_NONCE = "X-Nonce"
HEADER_SIGNATURE = "X-Signature"

PATH_PREFIX = "/internal"
COMMON_CONFIG_DATA_ID = "wisdomhub-common.yaml"
COMMON_CONFIG_GROUP = "DEFAULT_GROUP"


class InternalSignConfigError(RuntimeError):
    """Nacos 未下发可用的签名密钥。"""


def sha256_hex(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()


def canonical_string(method: str, path_with_query: str, timestamp: str, nonce: str, body: bytes) -> str:
    return "\n".join((method.upper(), path_with_query, timestamp, nonce, sha256_hex(body or b"")))


def sign(secret: str, canonical: str) -> str:
    digest = hmac.new(secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).digest()
    return base64.b64encode(digest).decode("ascii")


class InternalSigner:
    """每次调用生成新的时间戳与 Nonce；secret 只留在对象里，不进日志。"""

    def __init__(self, secret: str, *, service_name: str) -> None:
        if not secret or len(secret) < 16:
            raise ValueError("internal-sign secret 长度不足 16")
        self._secret = secret
        self._service_name = service_name

    def headers(self, method: str, path_with_query: str, body: bytes) -> dict[str, str]:
        timestamp = str(int(time.time()))
        nonce = secrets.token_urlsafe(16)
        canonical = canonical_string(method, path_with_query, timestamp, nonce, body)
        return {
            HEADER_SERVICE_NAME: self._service_name,
            HEADER_TIMESTAMP: timestamp,
            HEADER_NONCE: nonce,
            HEADER_SIGNATURE: sign(self._secret, canonical),
        }


async def load_internal_sign_secret(config_client: NacosConfigService) -> str:
    text = await config_client.get_config(
        ConfigParam(data_id=COMMON_CONFIG_DATA_ID, group=COMMON_CONFIG_GROUP)
    )
    if not text:
        raise InternalSignConfigError(f"Nacos 缺少配置 {COMMON_CONFIG_DATA_ID}")

    data = yaml.safe_load(text) or {}
    node = data
    for key in ("wisdomhub", "security", "internal-sign"):
        node = (node or {}).get(key)
    secret = (node or {}).get("secret")
    if not isinstance(secret, str) or len(secret) < 16:
        raise InternalSignConfigError(
            "Nacos wisdomhub-common.yaml 的 wisdomhub.security.internal-sign.secret 缺失或过短"
        )
    return secret
