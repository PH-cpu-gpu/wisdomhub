"""会话回合服务：幂等三态、租约接管、标题生成与归属校验。"""

from collections.abc import Callable
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from enum import StrEnum
from typing import Literal, cast
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.contracts.workflow import WorkflowKind
from app.core.errors import BusinessError, ErrorCode
from app.features.conversation.models import AgentConversation, AgentMessage
from app.features.conversation.repository import (
    ConversationDetail,
    ConversationPage,
    ConversationRepository,
)

TITLE_MAX_LENGTH = 30
DEFAULT_TITLE = "新会话"

TakeoverStatus = Literal["in_progress", "aborted", "error"]


def _utcnow() -> datetime:
    return datetime.now(UTC)


class TurnOutcome(StrEnum):
    NEW = "new"
    REPLAY = "replay"
    IN_PROGRESS = "in_progress"


@dataclass(frozen=True, slots=True)
class PreparedTurn:
    outcome: TurnOutcome
    conversation_id: UUID
    message_id: str
    workflow: WorkflowKind
    question: str
    assistant: AgentMessage | None = None
    expires_at: datetime | None = None


def _make_title(question: str) -> str:
    """取首个非空行、折叠空白、截断到 TITLE_MAX_LENGTH。"""
    line = next((item.strip() for item in question.splitlines() if item.strip()), "")
    line = " ".join(line.split())
    if not line:
        return DEFAULT_TITLE
    if len(line) <= TITLE_MAX_LENGTH:
        return line
    return f"{line[:TITLE_MAX_LENGTH]}…"


class ConversationService:
    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        *,
        ttl_seconds: int,
        clock: Callable[[], datetime] = _utcnow,
    ) -> None:
        if ttl_seconds < 1:
            raise ValueError("ttl_seconds 必须大于 0")
        self._session_factory = session_factory
        self._ttl = timedelta(seconds=ttl_seconds)
        self._clock = clock

    async def prepare_turn(
        self,
        *,
        user_id: int,
        message_id: str,
        workflow: WorkflowKind,
        question: str,
        conversation_id: UUID | None = None,
    ) -> PreparedTurn:
        """写入或复用一轮消息，返回 NEW / REPLAY / IN_PROGRESS。"""
        async with self._session_factory() as session, session.begin():
            repo = ConversationRepository(session)
            conversation = await self._resolve_conversation(
                repo, user_id, conversation_id, question
            )
            user_insert = await repo.insert_user_message(
                conversation.id, user_id, message_id, workflow, question
            )
            effective_question = user_insert.message.content

            moment = self._clock()
            expires_at = moment + self._ttl
            assistant_insert = await repo.insert_assistant_placeholder(
                conversation.id, user_id, message_id, workflow, expires_at
            )
            assistant = assistant_insert.message

            def result(
                outcome: TurnOutcome,
                *,
                row: AgentMessage | None = None,
                lease: datetime | None = None,
            ) -> PreparedTurn:
                return PreparedTurn(
                    outcome=outcome,
                    conversation_id=conversation.id,
                    message_id=message_id,
                    workflow=workflow,
                    question=effective_question,
                    assistant=row,
                    expires_at=lease,
                )

            if assistant_insert.created:
                await repo.touch_conversation(conversation.id, when=moment)
                return result(TurnOutcome.NEW, lease=expires_at)

            if assistant.status == "done":
                return result(TurnOutcome.REPLAY, row=assistant)

            if (
                assistant.status == "in_progress"
                and assistant.expires_at is not None
                and assistant.expires_at > moment
            ):
                return result(TurnOutcome.IN_PROGRESS, row=assistant, lease=assistant.expires_at)

            if assistant.status not in ("in_progress", "aborted", "error"):
                raise RuntimeError(f"未知 assistant 状态: {assistant.status}")
            takeover_status = cast(TakeoverStatus, assistant.status)
            taken = await repo.takeover_assistant(
                conversation.id,
                message_id,
                expected_status=takeover_status,
                expected_expires_at=assistant.expires_at,
                expires_at=expires_at,
            )
            if taken:
                await repo.touch_conversation(conversation.id, when=moment)
                return result(TurnOutcome.NEW, lease=expires_at)

            # 并发接管抢输：按最新状态兜底，不再重试
            current = (await repo.get_turn(conversation.id, message_id)).assistant
            if current is None:
                raise RuntimeError("assistant 行存在但重读不到")
            if current.status == "done":
                return result(TurnOutcome.REPLAY, row=current)
            return result(TurnOutcome.IN_PROGRESS, row=current, lease=current.expires_at)

    async def list_conversations(
        self, *, user_id: int, page: int, page_size: int
    ) -> ConversationPage:
        async with self._session_factory() as session:
            return await ConversationRepository(session).list_page(user_id, page, page_size)

    async def get_conversation_detail(
        self, *, user_id: int, conversation_id: UUID
    ) -> ConversationDetail:
        async with self._session_factory() as session:
            detail = await ConversationRepository(session).get_detail(user_id, conversation_id)
        if detail is None:
            raise BusinessError(ErrorCode.NOT_FOUND, "会话不存在")
        return detail

    async def delete_conversation(self, *, user_id: int, conversation_id: UUID) -> None:
        async with self._session_factory() as session, session.begin():
            deleted = await ConversationRepository(session).delete_conversation(
                user_id, conversation_id
            )
        if not deleted:
            raise BusinessError(ErrorCode.NOT_FOUND, "会话不存在")

    async def _resolve_conversation(
        self,
        repo: ConversationRepository,
        user_id: int,
        conversation_id: UUID | None,
        question: str,
    ) -> AgentConversation:
        if conversation_id is None:
            return await repo.create_conversation(user_id, _make_title(question))
        conversation = await repo.get_conversation(user_id, conversation_id)
        if conversation is None:
            raise BusinessError(ErrorCode.NOT_FOUND, "会话不存在")
        return conversation
