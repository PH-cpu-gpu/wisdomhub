import uuid
from datetime import datetime
from typing import Any

from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Identity,
    Index,
    Integer,
    Text,
    UniqueConstraint,
    text,
)
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class AgentConversation(Base):
    __tablename__ = "agent_conversation"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    title: Mapped[str] = mapped_column(Text, nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        Index("ix_agent_conversation_user_updated", "user_id", "updated_at"),
    )


class AgentMessage(Base):
    __tablename__ = "agent_message"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    conversation_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True),
        ForeignKey("agent_conversation.id", name="fk_agent_message_conversation", ondelete="CASCADE"),
        nullable=False,
    )
    message_id: Mapped[str] = mapped_column(Text, nullable=False)
    user_id: Mapped[int] = mapped_column(BigInteger, nullable=False)
    role: Mapped[str] = mapped_column(Text, nullable=False)
    content: Mapped[str] = mapped_column(Text, nullable=False, default="", server_default=text("''"))
    steps: Mapped[list[dict[str, Any]] | None] = mapped_column(JSONB, nullable=True)
    status: Mapped[str] = mapped_column(Text, nullable=False)
    seq: Mapped[int] = mapped_column(BigInteger, Identity(always=True), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    __table_args__ = (
        UniqueConstraint("conversation_id", "message_id", "role", name="uq_agent_message_turn"),
        CheckConstraint("role IN ('user','assistant')", name="ck_agent_message_role"),
        CheckConstraint(
            "status IN ('in_progress','done','aborted','error')",
            name="ck_agent_message_status",
        ),
        Index("ix_agent_message_conversation_seq", "conversation_id", "seq"),
        Index(
            "ix_agent_message_in_progress_expires",
            "expires_at",
            postgresql_where=text("status = 'in_progress'"),
        ),
    )


class AiAnalysisResult(Base):
    __tablename__ = "ai_analysis_result"

    article_id: Mapped[str] = mapped_column(Text, primary_key=True)
    status: Mapped[str] = mapped_column(Text, nullable=False)
    analysis: Mapped[dict[str, Any] | None] = mapped_column(JSONB, nullable=True)
    # 分析对应的文章快照版本（事件 updateTime）；同版本不重复调 LLM
    source_updated_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    # KnowledgeExtractEvent 发布成功时间；为空说明还没进图谱，重投时只重发事件
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    last_error: Mapped[str | None] = mapped_column(Text, nullable=True)
    attempts: Mapped[int] = mapped_column(Integer, nullable=False, default=0, server_default=text("0"))
    expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)

    __table_args__ = (
        CheckConstraint(
            "status IN ('in_progress','done','error')", name="ck_ai_analysis_status"
        ),
    )
