"""initial agent schema

Revision ID: 0001_initial
Revises:
Create Date: 2026-11-14 12:00:00.000000

"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0001_initial"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "agent_conversation",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("user_id", sa.BigInteger(), nullable=False),
        sa.Column("title", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_agent_conversation_user_updated",
        "agent_conversation",
        ["user_id", "updated_at"],
    )

    op.create_table(
        "agent_message",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("conversation_id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("message_id", sa.Text(), nullable=False),
        sa.Column("user_id", sa.BigInteger(), nullable=False),
        sa.Column("role", sa.Text(), nullable=False),
        sa.Column("content", sa.Text(), server_default=sa.text("''"), nullable=False),
        sa.Column("workflow", sa.SmallInteger(), nullable=True),
        sa.Column("steps", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column("artifacts", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column("status", sa.Text(), nullable=False),
        sa.Column("seq", sa.BigInteger(), sa.Identity(always=True), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint("role IN ('user','assistant')", name="ck_agent_message_role"),
        sa.CheckConstraint(
            "workflow IS NULL OR workflow IN (1,2,3)",
            name="ck_agent_message_workflow",
        ),
        sa.CheckConstraint(
            "status IN ('in_progress','done','aborted','error')",
            name="ck_agent_message_status",
        ),
        sa.ForeignKeyConstraint(
            ["conversation_id"],
            ["agent_conversation.id"],
            ondelete="CASCADE",
        ),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint(
            "conversation_id",
            "message_id",
            "role",
            name="uq_agent_message_turn",
        ),
    )
    op.create_index(
        "ix_agent_message_conversation_seq",
        "agent_message",
        ["conversation_id", "seq"],
    )
    op.create_index(
        "ix_agent_message_in_progress_expires",
        "agent_message",
        ["expires_at"],
        postgresql_where=sa.text("status = 'in_progress'"),
    )

    op.create_table(
        "ai_analysis_result",
        sa.Column("article_id", sa.Text(), nullable=False),
        sa.Column("status", sa.Text(), nullable=False),
        sa.Column("analysis", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column("last_error", sa.Text(), nullable=True),
        sa.Column("attempts", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("source_updated_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("notified_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "status IN ('in_progress','done','error')",
            name="ck_ai_analysis_result_status",
        ),
        sa.PrimaryKeyConstraint("article_id"),
    )
    op.create_index(
        "ix_ai_analysis_result_status_expires",
        "ai_analysis_result",
        ["status", "expires_at"],
    )


def downgrade() -> None:
    op.drop_table("ai_analysis_result")
    op.drop_table("agent_message")
    op.drop_table("agent_conversation")
