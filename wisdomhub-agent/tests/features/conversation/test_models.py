"""会话与消息模型与迁移的一致性。"""

import asyncio
from pathlib import Path
from typing import cast

import pytest
import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from sqlalchemy import (
    BigInteger,
    Identity,
    SmallInteger,
    Text,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine
from sqlalchemy.schema import DefaultClause

from app.features.conversation.models import AgentConversation, AgentMessage
from app.infra.db.metadata import target_metadata

ROOT = Path(__file__).resolve().parents[3]


def _config(url: str) -> Config:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "app/infra/db/migrations"))
    config.set_main_option("prepend_sys_path", str(ROOT))
    config.set_main_option("sqlalchemy.url", url)
    return config


def test_metadata_includes_conversation_tables() -> None:
    assert {"agent_conversation", "agent_message"} <= set(target_metadata.tables)


def test_conversation_columns() -> None:
    columns = target_metadata.tables["agent_conversation"].columns

    assert list(columns.keys()) == ["id", "user_id", "title", "created_at", "updated_at"]
    assert isinstance(columns["id"].type, PGUUID)
    assert columns["id"].primary_key is True
    assert isinstance(columns["user_id"].type, BigInteger)
    assert columns["user_id"].nullable is False
    assert isinstance(columns["title"].type, Text)
    assert columns["title"].nullable is False


def test_message_columns() -> None:
    columns = target_metadata.tables["agent_message"].columns

    assert list(columns.keys()) == [
        "id",
        "conversation_id",
        "message_id",
        "user_id",
        "role",
        "content",
        "workflow",
        "steps",
        "artifacts",
        "status",
        "seq",
        "created_at",
        "expires_at",
    ]
    assert isinstance(columns["conversation_id"].type, PGUUID)
    assert columns["conversation_id"].nullable is False
    fk = next(iter(columns["conversation_id"].foreign_keys))
    assert fk.target_fullname == "agent_conversation.id"
    assert fk.ondelete == "CASCADE"

    assert columns["content"].nullable is False
    server_default = columns["content"].server_default
    assert server_default is not None
    assert str(cast(DefaultClause, server_default).arg) == "''"

    assert isinstance(columns["workflow"].type, SmallInteger)
    assert columns["workflow"].nullable is True
    assert isinstance(columns["steps"].type, JSONB)
    assert columns["steps"].nullable is True
    assert isinstance(columns["artifacts"].type, JSONB)
    assert columns["artifacts"].nullable is True

    assert isinstance(columns["seq"].type, BigInteger)
    assert isinstance(columns["seq"].identity, Identity)
    assert columns["seq"].identity.always is True
    assert columns["expires_at"].nullable is True


def test_message_constraints_and_indexes() -> None:
    constraints = {item.name for item in target_metadata.tables["agent_message"].constraints}
    assert {
        "ck_agent_message_ck_agent_message_role",
        "ck_agent_message_ck_agent_message_workflow",
        "ck_agent_message_ck_agent_message_status",
        "uq_agent_message_turn",
    } <= constraints

    indexes = {str(item.name): item for item in target_metadata.tables["agent_message"].indexes}
    assert {
        "ix_agent_message_conversation_seq",
        "ix_agent_message_in_progress_expires",
    } <= set(indexes)
    assert (
        indexes["ix_agent_message_in_progress_expires"].dialect_options["postgresql"]["where"]
        is not None
    )

    conversation_indexes = {
        item.name for item in target_metadata.tables["agent_conversation"].indexes
    }
    assert "ix_agent_conversation_user_updated" in conversation_indexes


@pytest.mark.pg
async def test_orm_round_trip_and_cascade(pg_database: str) -> None:
    await asyncio.to_thread(command.upgrade, _config(pg_database), "head")
    engine = create_async_engine(pg_database)
    factory = async_sessionmaker(engine, expire_on_commit=False)
    steps = [
        {
            "id": "s1",
            "tool": "query_prerequisite_chain",
            "label": "取前置链",
            "status": "done",
            "summary": None,
        }
    ]
    artifacts = {"route": {"target": "Redis", "known": True, "truncated": False, "stages": []}}

    try:
        async with factory() as session:
            conversation = AgentConversation(user_id=1, title="测试会话")
            session.add(conversation)
            await session.flush()
            assert conversation.id is not None
            assert conversation.created_at is not None
            assert conversation.updated_at is not None

            message = AgentMessage(
                conversation_id=conversation.id,
                message_id="m1",
                user_id=1,
                role="assistant",
                content="hello",
                workflow=1,
                steps=steps,
                artifacts=artifacts,
                status="done",
            )
            session.add(message)
            await session.flush()
            assert message.seq > 0
            await session.commit()

        async with factory() as session:
            loaded = await session.get(AgentMessage, message.id)
            assert loaded is not None
            assert loaded.content == "hello"
            assert loaded.workflow == 1
            assert loaded.steps == steps
            assert loaded.artifacts == artifacts
            assert loaded.created_at.tzinfo is not None

            loaded_conversation = await session.get(AgentConversation, conversation.id)
            assert loaded_conversation is not None
            await session.delete(loaded_conversation)
            await session.commit()

            remaining = await session.scalar(sa.select(sa.func.count()).select_from(AgentMessage))
            assert remaining == 0
    finally:
        await engine.dispose()
