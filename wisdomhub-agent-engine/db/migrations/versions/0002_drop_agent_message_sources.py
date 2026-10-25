"""drop agent_message.sources

Revision ID: 0002_drop_agent_message_sources
Revises: 0001_agent_session
Create Date: 2026-10-25

"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op
from sqlalchemy.dialects import postgresql

revision: str = "0002_drop_agent_message_sources"
down_revision: str | None = "0001_agent_session"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.drop_column("agent_message", "sources")


def downgrade() -> None:
    op.add_column(
        "agent_message",
        sa.Column(
            "sources",
            postgresql.JSONB(astext_type=sa.Text()),
            server_default=sa.text("'[]'::jsonb"),
            nullable=False,
        ),
    )
