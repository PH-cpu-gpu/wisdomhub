from typing import Annotated

import pytest
from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from app.core.errors import BusinessError, ErrorCode, register_exception_handlers
from app.infra.security.user import MAX_USER_ID, current_user_id, parse_user_id


@pytest.mark.parametrize("raw", ["1001", " 1001 ", "001"])
def test_parse_valid_user_id(raw: str) -> None:
    assert parse_user_id(raw) > 0


def test_parse_max_bigint() -> None:
    assert parse_user_id(str(MAX_USER_ID)) == MAX_USER_ID


@pytest.mark.parametrize(
    "raw",
    [None, "", "   ", "abc", "1.0", "+1", "-1", "0", "１００１", str(MAX_USER_ID + 1)],
)
def test_parse_invalid_user_id(raw: str | None) -> None:
    with pytest.raises(BusinessError) as exc:
        parse_user_id(raw)

    assert exc.value.code == ErrorCode.UNAUTHORIZED


def _client() -> TestClient:
    app = FastAPI()
    register_exception_handlers(app)

    @app.get("/me")
    def me(user_id: Annotated[int, Depends(current_user_id)]) -> dict[str, int]:
        return {"userId": user_id}

    @app.post("/echo")
    def echo(
        payload: dict[str, object],
        user_id: Annotated[int, Depends(current_user_id)],
    ) -> dict[str, object]:
        return {"userId": user_id, "payload": payload}

    return TestClient(app, raise_server_exceptions=False)


def test_fastapi_valid_header() -> None:
    response = _client().get("/me", headers={"X-User-Id": "1001"})

    assert response.status_code == 200
    assert response.json() == {"userId": 1001}


@pytest.mark.parametrize("headers", [{}, {"X-User-Id": "abc"}, {"X-User-Id": "0"}])
def test_fastapi_missing_or_invalid_header(headers: dict[str, str]) -> None:
    response = _client().get("/me", headers=headers)

    assert response.status_code == 200
    assert response.json()["code"] == 401


def test_body_user_id_is_ignored() -> None:
    response = _client().post("/echo", json={"userId": 999})

    assert response.status_code == 200
    assert response.json()["code"] == 401


def test_header_wins_over_body_user_id() -> None:
    response = _client().post(
        "/echo",
        json={"userId": 999},
        headers={"X-User-Id": "1001"},
    )

    assert response.status_code == 200
    assert response.json() == {"userId": 1001, "payload": {"userId": 999}}
