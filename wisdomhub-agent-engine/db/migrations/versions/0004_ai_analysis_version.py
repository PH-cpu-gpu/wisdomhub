"""ai analysis version

Revision ID: 0004_ai_analysis_version
Revises: 0003_ai_analysis_result
Create Date: 2026-10-26

"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0004_ai_analysis_version"
down_revision: str | None = "0003_ai_analysis_result"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "ai_analysis_result",
        sa.Column("source_updated_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.add_column(
        "ai_analysis_result",
        sa.Column("published_at", sa.DateTime(timezone=True), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("ai_analysis_result", "published_at")
    op.drop_column("ai_analysis_result", "source_updated_at")
