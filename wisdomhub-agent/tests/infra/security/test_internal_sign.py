import pytest

from app.infra.security.internal_sign import (
    HEADER_NONCE,
    HEADER_SERVICE_NAME,
    HEADER_SIGNATURE,
    HEADER_TIMESTAMP,
    InternalSigner,
    canonical_string,
    sha256_hex,
    sign,
)

SECRET = "0123456789abcdef0123456789abcdef"


def test_sha256_empty_body() -> None:
    assert sha256_hex(b"") == ("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")


def test_canonical_string_is_exact() -> None:
    body = b'{"a":1}'
    expected = "\n".join(("GET", "/internal/x?a=1", "1727000000", "nonce", sha256_hex(body)))

    assert canonical_string("get", "/internal/x?a=1", "1727000000", "nonce", body) == expected


def test_headers_are_signed() -> None:
    signer = InternalSigner(SECRET, service_name="wisdomhub-agent")
    path = "/internal/knowledge/related?tech=Redis"

    headers = signer.headers("GET", path, b"")

    assert headers[HEADER_SERVICE_NAME] == "wisdomhub-agent"
    assert headers[HEADER_TIMESTAMP]
    assert headers[HEADER_NONCE]
    canonical = canonical_string(
        "GET",
        path,
        headers[HEADER_TIMESTAMP],
        headers[HEADER_NONCE],
        b"",
    )
    assert headers[HEADER_SIGNATURE] == sign(SECRET, canonical)


def test_nonce_changes_per_request() -> None:
    signer = InternalSigner(SECRET)

    first = signer.headers("GET", "/internal/x", b"")
    second = signer.headers("GET", "/internal/x", b"")

    assert first[HEADER_NONCE] != second[HEADER_NONCE]


def test_short_secret_is_rejected() -> None:
    with pytest.raises(ValueError):
        InternalSigner("short")
