"""ai analysis result

Revision ID: 0003_ai_analysis_result
Revises: 0002_drop_agent_message_sources
Create Date: 2026-10-26

"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0003_ai_analysis_result"
down_revision: str | None = "0002_drop_agent_message_sources"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "ai_analysis_result",
        sa.Column("article_id", sa.Text(), nullable=False),
        sa.Column("status", sa.Text(), nullable=False),
        sa.Column("analysis", postgresql.JSONB(astext_type=sa.Text()), nullable=True),
        sa.Column("last_error", sa.Text(), nullable=True),
        sa.Column("attempts", sa.Integer(), server_default=sa.text("0"), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "status IN ('in_progress','done','error')", name="ck_ai_analysis_status"
        ),
        sa.PrimaryKeyConstraint("article_id"),
    )


def downgrade() -> None:
    op.drop_table("ai_analysis_result")
