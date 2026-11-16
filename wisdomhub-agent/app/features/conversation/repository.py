"""会话与消息仓储：只 flush，不控制事务。"""

from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any, Literal
from uuid import UUID

from sqlalchemy import delete, func, select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from app.contracts.workflow import WorkflowKind
from app.features.conversation.models import AgentConversation, AgentMessage

TerminalStatus = Literal["done", "aborted", "error"]
Role = Literal["user", "assistant"]


def _utcnow() -> datetime:
    return datetime.now(UTC)


def _as_workflow(value: int | None) -> WorkflowKind | None:
    return WorkflowKind(value) if value is not None else None


@dataclass(frozen=True, slots=True)
class TurnInsert:
    message: AgentMessage
    created: bool


@dataclass(frozen=True, slots=True)
class TurnView:
    user: AgentMessage | None
    assistant: AgentMessage | None


@dataclass(frozen=True, slots=True)
class ConversationSummary:
    conversation: AgentConversation
    workflow: WorkflowKind | None


@dataclass(frozen=True, slots=True)
class ConversationPage:
    items: list[ConversationSummary]
    total: int


@dataclass(frozen=True, slots=True)
class ConversationDetail:
    summary: ConversationSummary
    messages: list[AgentMessage]


class ConversationRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def create_conversation(self, user_id: int, title: str) -> AgentConversation:
        conversation = AgentConversation(user_id=user_id, title=title)
        self._session.add(conversation)
        await self._session.flush()
        return conversation

    async def get_conversation(
        self, user_id: int, conversation_id: UUID
    ) -> AgentConversation | None:
        return await self._session.scalar(
            select(AgentConversation).where(
                AgentConversation.id == conversation_id,
                AgentConversation.user_id == user_id,
            )
        )

    async def touch_conversation(self, conversation_id: UUID, when: datetime | None = None) -> None:
        await self._session.execute(
            update(AgentConversation)
            .where(AgentConversation.id == conversation_id)
            .values(updated_at=when or _utcnow())
        )

    async def delete_conversation(self, user_id: int, conversation_id: UUID) -> bool:
        deleted_id = await self._session.scalar(
            delete(AgentConversation)
            .where(
                AgentConversation.id == conversation_id,
                AgentConversation.user_id == user_id,
            )
            .returning(AgentConversation.id)
        )
        return deleted_id is not None

    async def insert_user_message(
        self,
        conversation_id: UUID,
        user_id: int,
        message_id: str,
        workflow: WorkflowKind,
        question: str,
    ) -> TurnInsert:
        return await self._insert_message(
            conversation_id=conversation_id,
            user_id=user_id,
            message_id=message_id,
            role="user",
            status="done",
            content=question,
            workflow=int(workflow),
            expires_at=None,
        )

    async def insert_assistant_placeholder(
        self,
        conversation_id: UUID,
        user_id: int,
        message_id: str,
        workflow: WorkflowKind,
        expires_at: datetime,
    ) -> TurnInsert:
        return await self._insert_message(
            conversation_id=conversation_id,
            user_id=user_id,
            message_id=message_id,
            role="assistant",
            status="in_progress",
            content="",
            workflow=int(workflow),
            expires_at=expires_at,
        )

    async def get_turn(self, conversation_id: UUID, message_id: str) -> TurnView:
        rows = (
            await self._session.scalars(
                select(AgentMessage)
                .where(
                    AgentMessage.conversation_id == conversation_id,
                    AgentMessage.message_id == message_id,
                )
                .order_by(AgentMessage.seq)
            )
        ).all()
        by_role = {row.role: row for row in rows}
        return TurnView(user=by_role.get("user"), assistant=by_role.get("assistant"))

    async def renew_assistant_lease(
        self,
        conversation_id: UUID,
        message_id: str,
        *,
        expected_expires_at: datetime,
        expires_at: datetime,
    ) -> bool:
        """按观测到的租约做 CAS 续租；并发接管只有一个能拿到。"""
        renewed_id = await self._session.scalar(
            update(AgentMessage)
            .where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.message_id == message_id,
                AgentMessage.role == "assistant",
                AgentMessage.status == "in_progress",
                AgentMessage.expires_at == expected_expires_at,
            )
            .values(expires_at=expires_at)
            .returning(AgentMessage.id)
        )
        return renewed_id is not None

    async def takeover_assistant(
        self,
        conversation_id: UUID,
        message_id: str,
        *,
        expected_status: Literal["in_progress", "aborted", "error"],
        expected_expires_at: datetime | None,
        expires_at: datetime,
    ) -> bool:
        """按观测到的状态与租约做 CAS 接管；并发接管只有一个能拿到。"""
        lease_matches = (
            AgentMessage.expires_at.is_(None)
            if expected_expires_at is None
            else AgentMessage.expires_at == expected_expires_at
        )
        taken_id = await self._session.scalar(
            update(AgentMessage)
            .where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.message_id == message_id,
                AgentMessage.role == "assistant",
                AgentMessage.status == expected_status,
                lease_matches,
            )
            .values(content="", steps=None, status="in_progress", expires_at=expires_at)
            .returning(AgentMessage.id)
        )
        return taken_id is not None

    async def finish_assistant(
        self,
        conversation_id: UUID,
        message_id: str,
        *,
        status: TerminalStatus,
        content: str,
        steps: list[dict[str, Any]] | None,
        artifacts: dict[str, Any] | None,
        workflow: WorkflowKind,
        lease_expires_at: datetime,
        now: datetime | None = None,
    ) -> bool:
        """写入终态；以 lease_expires_at 做 CAS，被接管后的旧回合写入会落空。"""
        moment = now or _utcnow()
        finished_id = await self._session.scalar(
            update(AgentMessage)
            .where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.message_id == message_id,
                AgentMessage.role == "assistant",
                AgentMessage.status == "in_progress",
                AgentMessage.expires_at == lease_expires_at,
            )
            .values(
                content=content,
                steps=steps,
                artifacts=artifacts,
                workflow=int(workflow),
                status=status,
                expires_at=None,
            )
            .returning(AgentMessage.id)
        )
        if finished_id is None:
            return False
        await self.touch_conversation(conversation_id, when=moment)
        return True

    async def list_history(self, conversation_id: UUID) -> list[AgentMessage]:
        rows = await self._session.scalars(
            select(AgentMessage)
            .where(AgentMessage.conversation_id == conversation_id)
            .order_by(AgentMessage.seq)
        )
        return list(rows)

    async def list_page(self, user_id: int, page: int, page_size: int) -> ConversationPage:
        if page < 1 or page_size < 1:
            raise ValueError("page 与 page_size 必须大于 0")

        latest_workflow = (
            select(AgentMessage.workflow)
            .where(
                AgentMessage.conversation_id == AgentConversation.id,
                AgentMessage.role == "assistant",
                AgentMessage.workflow.is_not(None),
            )
            .order_by(AgentMessage.seq.desc())
            .limit(1)
            .correlate(AgentConversation)
            .scalar_subquery()
        )
        total = await self._session.scalar(
            select(func.count())
            .select_from(AgentConversation)
            .where(AgentConversation.user_id == user_id)
        )
        rows = (
            await self._session.execute(
                select(AgentConversation, latest_workflow.label("workflow"))
                .where(AgentConversation.user_id == user_id)
                .order_by(AgentConversation.updated_at.desc(), AgentConversation.id.desc())
                .offset((page - 1) * page_size)
                .limit(page_size)
            )
        ).all()
        return ConversationPage(
            items=[
                ConversationSummary(conversation=conversation, workflow=_as_workflow(workflow))
                for conversation, workflow in rows
            ],
            total=total or 0,
        )

    async def get_detail(self, user_id: int, conversation_id: UUID) -> ConversationDetail | None:
        conversation = await self.get_conversation(user_id, conversation_id)
        if conversation is None:
            return None

        workflow = await self._session.scalar(
            select(AgentMessage.workflow)
            .where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.role == "assistant",
                AgentMessage.workflow.is_not(None),
            )
            .order_by(AgentMessage.seq.desc())
            .limit(1)
        )
        return ConversationDetail(
            summary=ConversationSummary(conversation=conversation, workflow=_as_workflow(workflow)),
            messages=await self.list_history(conversation_id),
        )

    async def _insert_message(
        self,
        *,
        conversation_id: UUID,
        user_id: int,
        message_id: str,
        role: Role,
        status: str,
        content: str,
        workflow: int,
        expires_at: datetime | None,
    ) -> TurnInsert:
        created_id = await self._session.scalar(
            pg_insert(AgentMessage)
            .values(
                conversation_id=conversation_id,
                message_id=message_id,
                user_id=user_id,
                role=role,
                status=status,
                content=content,
                workflow=workflow,
                expires_at=expires_at,
            )
            .on_conflict_do_nothing(index_elements=["conversation_id", "message_id", "role"])
            .returning(AgentMessage.id)
        )
        message = await self._session.scalar(
            select(AgentMessage).where(
                AgentMessage.conversation_id == conversation_id,
                AgentMessage.message_id == message_id,
                AgentMessage.role == role,
            )
        )
        if message is None:
            raise RuntimeError("消息写入后未找到对应行")
        return TurnInsert(message=message, created=created_id is not None)
