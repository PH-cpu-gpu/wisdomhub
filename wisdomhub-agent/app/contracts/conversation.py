"""会话 VO：列表、详情与消息快照。"""

from typing import Literal
from uuid import UUID

from pydantic import AwareDatetime

from app.contracts.artifacts import MessageArtifacts
from app.contracts.base import WireModel
from app.contracts.sse import Step
from app.contracts.workflow import WorkflowKind

MessageRole = Literal["user", "assistant"]
MessageStatus = Literal["in_progress", "done", "aborted", "error"]


class ConversationMessageVO(WireModel):
    id: UUID
    message_id: str
    role: MessageRole
    content: str
    workflow: WorkflowKind | None
    status: MessageStatus
    steps: list[Step] | None = None
    artifacts: MessageArtifacts | None = None
    created_at: AwareDatetime


class ConversationItemVO(WireModel):
    id: UUID
    title: str
    workflow: WorkflowKind | None
    created_at: AwareDatetime
    updated_at: AwareDatetime


class ConversationDetailVO(ConversationItemVO):
    messages: list[ConversationMessageVO]


class ConversationPageVO(WireModel):
    total: int
    pages: int
    page_size: int
    page: int
    items: list[ConversationItemVO]
