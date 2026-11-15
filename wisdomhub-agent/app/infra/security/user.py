"""用户身份解析：只读网关注入的 X-User-Id。"""

from typing import Annotated

from fastapi import Header

from app.core.errors import BusinessError, ErrorCode

USER_ID_HEADER = "X-User-Id"
MAX_USER_ID = 2**63 - 1


def parse_user_id(raw: str | None) -> int:
    value = (raw or "").strip()
    if not value or not value.isascii() or not value.isdecimal():
        raise BusinessError(ErrorCode.UNAUTHORIZED)

    user_id = int(value)
    if user_id <= 0 or user_id > MAX_USER_ID:
        raise BusinessError(ErrorCode.UNAUTHORIZED)
    return user_id


def current_user_id(
    x_user_id: Annotated[
        str | None,
        Header(alias=USER_ID_HEADER, include_in_schema=False),
    ] = None,
) -> int:
    return parse_user_id(x_user_id)
