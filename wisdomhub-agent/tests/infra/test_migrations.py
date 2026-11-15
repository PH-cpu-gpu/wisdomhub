import asyncio
from pathlib import Path

import pytest
import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from sqlalchemy.ext.asyncio import create_async_engine

ROOT = Path(__file__).resolve().parents[2]


def _config(url: str) -> Config:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "app/infra/db/migrations"))
    config.set_main_option("prepend_sys_path", str(ROOT))
    config.set_main_option("sqlalchemy.url", url)
    return config


async def _table_names(url: str) -> set[str]:
    engine = create_async_engine(url)
    try:
        async with engine.connect() as connection:
            return set(await connection.run_sync(lambda conn: sa.inspect(conn).get_table_names()))
    finally:
        await engine.dispose()


async def _index_names(url: str, table: str) -> set[str]:
    engine = create_async_engine(url)
    try:
        async with engine.connect() as connection:
            result = await connection.execute(
                sa.text(
                    "SELECT indexname FROM pg_indexes "
                    "WHERE schemaname = 'public' AND tablename = :table"
                ),
                {"table": table},
            )
            return {row[0] for row in result}
    finally:
        await engine.dispose()


async def _column_info(url: str, table: str, column: str) -> tuple[str, str]:
    engine = create_async_engine(url)
    try:
        async with engine.connect() as connection:
            result = await connection.execute(
                sa.text(
                    "SELECT data_type, is_identity FROM information_schema.columns "
                    "WHERE table_schema = 'public' AND table_name = :table "
                    "AND column_name = :column"
                ),
                {"table": table, "column": column},
            )
            return result.one()
    finally:
        await engine.dispose()


@pytest.mark.pg
async def test_upgrade_head_creates_schema(pg_database: str) -> None:
    config = _config(pg_database)
    await asyncio.to_thread(command.upgrade, config, "head")

    tables = await _table_names(pg_database)
    assert {"agent_conversation", "agent_message", "ai_analysis_result"} <= tables
    assert "alembic_version" in tables

    assert {
        "ix_agent_conversation_user_updated",
    } <= await _index_names(pg_database, "agent_conversation")
    assert {
        "ix_agent_message_conversation_seq",
        "ix_agent_message_in_progress_expires",
        "uq_agent_message_turn",
    } <= await _index_names(pg_database, "agent_message")
    assert {
        "ix_ai_analysis_result_status_expires",
    } <= await _index_names(pg_database, "ai_analysis_result")

    assert await _column_info(pg_database, "agent_message", "workflow") == ("smallint", "NO")
    assert await _column_info(pg_database, "agent_message", "seq") == ("bigint", "YES")


@pytest.mark.pg
async def test_upgrade_head_is_idempotent(pg_database: str) -> None:
    config = _config(pg_database)
    await asyncio.to_thread(command.upgrade, config, "head")
    await asyncio.to_thread(command.upgrade, config, "head")

    assert "agent_message" in await _table_names(pg_database)


@pytest.mark.pg
async def test_downgrade_base_removes_business_tables(pg_database: str) -> None:
    config = _config(pg_database)
    await asyncio.to_thread(command.upgrade, config, "head")
    await asyncio.to_thread(command.downgrade, config, "base")

    tables = await _table_names(pg_database)
    assert "agent_conversation" not in tables
    assert "agent_message" not in tables
    assert "ai_analysis_result" not in tables
