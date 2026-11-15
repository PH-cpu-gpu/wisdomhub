"""内部服务 HMAC 签名。"""

import base64
import hashlib
import hmac
import secrets
import time
from dataclasses import dataclass, field

HEADER_SERVICE_NAME = "X-Service-Name"
HEADER_TIMESTAMP = "X-Timestamp"
HEADER_NONCE = "X-Nonce"
HEADER_SIGNATURE = "X-Signature"
MIN_SECRET_LENGTH = 16


def sha256_hex(body: bytes) -> str:
    return hashlib.sha256(body).hexdigest()


def canonical_string(
    method: str,
    path_with_query: str,
    timestamp: str,
    nonce: str,
    body: bytes,
) -> str:
    return "\n".join((method.upper(), path_with_query, timestamp, nonce, sha256_hex(body)))


def sign(secret: str, canonical: str) -> str:
    digest = hmac.new(secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).digest()
    return base64.b64encode(digest).decode("ascii")


@dataclass(frozen=True, slots=True)
class InternalSigner:
    secret: str = field(repr=False)
    service_name: str = "wisdomhub-agent"

    def __post_init__(self) -> None:
        if len(self.secret) < MIN_SECRET_LENGTH:
            raise ValueError(f"internal-sign secret 长度不足 {MIN_SECRET_LENGTH}")

    def headers(self, method: str, path_with_query: str, body: bytes) -> dict[str, str]:
        timestamp = str(int(time.time()))
        nonce = secrets.token_urlsafe(16)
        canonical = canonical_string(method, path_with_query, timestamp, nonce, body)
        return {
            HEADER_SERVICE_NAME: self.service_name,
            HEADER_TIMESTAMP: timestamp,
            HEADER_NONCE: nonce,
            HEADER_SIGNATURE: sign(self.secret, canonical),
        }
