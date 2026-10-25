from typing import Annotated

from fastapi import Header

from app.response import BusinessError, ErrorCode


def current_user_id(
    x_user_id: Annotated[str | None, Header(alias="X-User-Id", include_in_schema=False)] = None,
) -> int:
    try:
        user_id = int(x_user_id) if x_user_id is not None else 0
    except ValueError:
        user_id = 0
    if user_id <= 0:
        raise BusinessError(ErrorCode.UNAUTHORIZED)
    return user_id
