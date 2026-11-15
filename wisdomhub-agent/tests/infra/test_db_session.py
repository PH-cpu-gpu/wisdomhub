import pytest
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncEngine, async_sessionmaker

from app.core.config import DbSettings
from app.infra.db.session import Database

DUMMY_URL = "postgresql+asyncpg://user:pass@127.0.0.1:5432/wisdomhub_agent"


def _settings(url: str) -> DbSettings:
    return DbSettings(url=url, pool_size=2, pool_timeout=5.0, echo=False)


async def test_create_builds_engine_and_session_factory() -> None:
    database = Database.create(_settings(DUMMY_URL))

    try:
        assert isinstance(database.engine, AsyncEngine)
        assert isinstance(database.session_factory, async_sessionmaker)
    finally:
        await database.close()


async def test_close_is_idempotent() -> None:
    database = Database.create(_settings(DUMMY_URL))

    await database.close()
    await database.close()


@pytest.mark.pg
async def test_ping(pg_database: str) -> None:
    database = Database.create(_settings(pg_database))

    try:
        await database.ping()
    finally:
        await database.close()


@pytest.mark.pg
async def test_session_factory(pg_database: str) -> None:
    database = Database.create(_settings(pg_database))

    try:
        async with database.session_factory() as session:
            await session.execute(text("CREATE TABLE b21_sample (id integer)"))
            await session.execute(text("INSERT INTO b21_sample VALUES (1)"))
            await session.commit()

            assert await session.scalar(text("SELECT count(*) FROM b21_sample")) == 1
    finally:
        await database.close()
