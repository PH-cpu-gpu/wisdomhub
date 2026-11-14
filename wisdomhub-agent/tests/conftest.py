"""测试公共 fixture：配置、临时 PostgreSQL。"""

import os
import uuid
import warnings
from collections.abc import AsyncIterator
from pathlib import Path

import asyncpg
import pytest
from dotenv import dotenv_values
from sqlalchemy.engine import URL, make_url
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import NullPool

from app.core.config import ENV_NAMES, Settings, load_settings

PROJECT_ROOT = Path(__file__).resolve().parents[1]

_REQUIRED = {
    "DATABASE_URL": "postgresql+asyncpg://wisdomhub:pwd@127.0.0.1:5432/wisdomhub_agent",
    "CHAT_MODEL": "test-model",
    "CHAT_BASE_URL": "http://127.0.0.1:9999/v1",
    "CHAT_API_KEY": "test-key",
}


@pytest.fixture
def settings(monkeypatch: pytest.MonkeyPatch) -> Settings:
    for name in ENV_NAMES:
        monkeypatch.delenv(name, raising=False)
    for name, value in _REQUIRED.items():
        monkeypatch.setenv(name, value)
    return load_settings(env_file=None)


def _database_url() -> str | None:
    override = os.environ.get("TEST_DATABASE_URL")
    if override:
        return override
    value = dotenv_values(PROJECT_ROOT / ".env").get("DATABASE_URL")
    return value or None


def _async_url(raw: str) -> URL:
    url = make_url(raw)
    if url.drivername in ("postgres", "postgresql"):
        return url.set(drivername="postgresql+asyncpg")
    return url


async def _connect_admin(url: URL) -> asyncpg.Connection:
    return await asyncpg.connect(
        user=url.username,
        password=url.password,
        host=url.host or "127.0.0.1",
        port=url.port or 5432,
        database="postgres",
        timeout=5,
    )


@pytest.fixture
async def pg_database() -> AsyncIterator[str]:
    raw = _database_url()
    if not raw:
        pytest.skip("未配置 TEST_DATABASE_URL 或 .env 的 DATABASE_URL")

    base = _async_url(raw)
    try:
        admin = await _connect_admin(base)
    except Exception as exc:
        pytest.skip(f"PostgreSQL 不可用: {exc}")

    name = f"wisdomhub_agent_test_{uuid.uuid4().hex[:12]}"
    try:
        await admin.execute(f'CREATE DATABASE "{name}"')
    except Exception as exc:
        await admin.close()
        pytest.skip(f"创建测试数据库失败: {exc}")

    try:
        yield base.set(database=name).render_as_string(hide_password=False)
    finally:
        try:
            await admin.execute(f'DROP DATABASE IF EXISTS "{name}" WITH (FORCE)')
        except Exception as exc:
            warnings.warn(f"删除测试数据库失败: {exc}", stacklevel=1)
        finally:
            await admin.close()


@pytest.fixture
async def pg_engine(pg_database: str) -> AsyncIterator[AsyncEngine]:
    engine = create_async_engine(pg_database, poolclass=NullPool)
    try:
        yield engine
    finally:
        await engine.dispose()


@pytest.fixture
async def db_session(pg_engine: AsyncEngine) -> AsyncIterator[AsyncSession]:
    async with pg_engine.connect() as connection:
        transaction = await connection.begin()
        factory = async_sessionmaker(
            bind=connection,
            expire_on_commit=False,
            join_transaction_mode="create_savepoint",
        )
        try:
            async with factory() as session:
                yield session
        finally:
            if transaction.is_active:
                await transaction.rollback()
