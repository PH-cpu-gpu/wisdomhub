"""会话与消息 ORM 模型。"""

from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid4

from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Identity,
    Index,
    SmallInteger,
    Text,
    UniqueConstraint,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.dialects.postgresql import UUID as PGUUID
from sqlalchemy.orm import Mapped, mapped_column

from app.infra.db.base import Base


def _utcnow() -> datetime:
    return datetime.now(UTC)


class AgentConversation(Base):
    __tablename__ = "agent_conversation"

    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    title: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow, onupdate=_utcnow
    )

    __table_args__ = (Index("ix_agent_conversation_user_updated", "user_id", "updated_at"),)


class AgentMessage(Base):
    __tablename__ = "agent_message"

    id: Mapped[UUID] = mapped_column(PGUUID(as_uuid=True), primary_key=True, default=uuid4)
    conversation_id: Mapped[UUID] = mapped_column(
        PGUUID(as_uuid=True),
        ForeignKey("agent_conversation.id", ondelete="CASCADE"),
        nullable=False,
    )
    message_id: Mapped[str] = mapped_column(Text, nullable=False)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    role: Mapped[str] = mapped_column(Text, nullable=False)
    content: Mapped[str] = mapped_column(
        Text, nullable=False, default="", server_default=text("''")
    )
    workflow: Mapped[int | None] = mapped_column(SmallInteger, nullable=True)
    steps: Mapped[list[dict[str, Any]] | None] = mapped_column(JSONB, nullable=True)
    artifacts: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
    status: Mapped[str] = mapped_column(Text, nullable=False)
    seq: Mapped[int] = mapped_column(BigInteger, Identity(always=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=_utcnow
    )
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    __table_args__ = (
        CheckConstraint("role IN ('user','assistant')", name="ck_agent_message_role"),
        CheckConstraint(
            "workflow IS NULL OR workflow IN (1,2,3)", name="ck_agent_message_workflow"
        ),
        CheckConstraint(
            "status IN ('in_progress','done','aborted','error')",
            name="ck_agent_message_status",
        ),
        UniqueConstraint("conversation_id", "message_id", "role", name="uq_agent_message_turn"),
        Index("ix_agent_message_conversation_seq", "conversation_id", "seq"),
        Index(
            "ix_agent_message_in_progress_expires",
            "expires_at",
            postgresql_where=text("status = 'in_progress'"),
        ),
    )
