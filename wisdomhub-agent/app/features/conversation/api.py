"""会话 REST：列表、详情与删除。"""

from typing import Annotated, Any, cast
from uuid import UUID

from fastapi import APIRouter, Depends, Query

from app.contracts.artifacts import MessageArtifacts
from app.contracts.conversation import (
    ConversationDetailVO,
    ConversationItemVO,
    ConversationMessageVO,
    ConversationPageVO,
    MessageRole,
    MessageStatus,
)
from app.contracts.sse import Step
from app.contracts.workflow import WorkflowKind
from app.core.deps import AppDeps, get_deps
from app.core.errors import BusinessError, ErrorCode, success
from app.features.conversation.models import AgentMessage
from app.features.conversation.repository import ConversationSummary
from app.features.conversation.service import ConversationService
from app.infra.security.user import current_user_id

router = APIRouter(prefix="/api/agent/conversations", tags=["conversations"])

DEFAULT_PAGE_SIZE = 10
MAX_PAGE_SIZE = 100


def get_conversation_service(deps: Annotated[AppDeps, Depends(get_deps)]) -> ConversationService:
    service = deps.conversation
    if service is None:
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "会话服务暂不可用")
    return service


UserId = Annotated[int, Depends(current_user_id)]
Service = Annotated[ConversationService, Depends(get_conversation_service)]
Page = Annotated[int, Query(ge=1)]
PageSize = Annotated[int, Query(alias="pageSize", ge=1, le=MAX_PAGE_SIZE)]


def _item_vo(summary: ConversationSummary) -> ConversationItemVO:
    conversation = summary.conversation
    return ConversationItemVO(
        id=conversation.id,
        title=conversation.title,
        workflow=summary.workflow,
        created_at=conversation.created_at,
        updated_at=conversation.updated_at,
    )


def _message_vo(message: AgentMessage) -> ConversationMessageVO:
    return ConversationMessageVO(
        id=message.id,
        message_id=message.message_id,
        role=cast(MessageRole, message.role),
        content=message.content,
        workflow=WorkflowKind(message.workflow) if message.workflow is not None else None,
        status=cast(MessageStatus, message.status),
        steps=(
            [Step.model_validate(item) for item in message.steps]
            if message.steps is not None
            else None
        ),
        artifacts=(
            MessageArtifacts.model_validate(message.artifacts)
            if message.artifacts is not None
            else None
        ),
        created_at=message.created_at,
    )


@router.get("")
async def list_conversations(
    user_id: UserId,
    service: Service,
    page: Page = 1,
    page_size: PageSize = DEFAULT_PAGE_SIZE,
) -> dict[str, Any]:
    result = await service.list_conversations(user_id=user_id, page=page, page_size=page_size)
    vo = ConversationPageVO(
        total=result.total,
        pages=(result.total + page_size - 1) // page_size,
        page_size=page_size,
        page=page,
        items=[_item_vo(item) for item in result.items],
    )
    return success(vo.model_dump(mode="json", by_alias=True))


@router.get("/{conversation_id}")
async def get_conversation(
    conversation_id: UUID,
    user_id: UserId,
    service: Service,
) -> dict[str, Any]:
    detail = await service.get_conversation_detail(user_id=user_id, conversation_id=conversation_id)
    vo = ConversationDetailVO(
        **_item_vo(detail.summary).model_dump(),
        messages=[_message_vo(message) for message in detail.messages],
    )
    return success(vo.model_dump(mode="json", by_alias=True))


@router.delete("/{conversation_id}")
async def delete_conversation(
    conversation_id: UUID,
    user_id: UserId,
    service: Service,
) -> dict[str, Any]:
    await service.delete_conversation(user_id=user_id, conversation_id=conversation_id)
    return success()
