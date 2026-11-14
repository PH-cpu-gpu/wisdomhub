"""临时 PostgreSQL：建表写入与跨用例隔离。"""

import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession


@pytest.mark.pg
async def test_pg_session_can_write(db_session: AsyncSession) -> None:
    await db_session.execute(text("CREATE TABLE b05_sample (id integer)"))
    await db_session.execute(text("INSERT INTO b05_sample VALUES (1)"))
    await db_session.commit()

    assert await db_session.scalar(text("SELECT count(*) FROM b05_sample")) == 1


@pytest.mark.pg
async def test_pg_session_is_isolated(db_session: AsyncSession) -> None:
    assert await db_session.scalar(text("SELECT to_regclass('public.b05_sample')")) is None
