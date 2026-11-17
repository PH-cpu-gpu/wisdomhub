"""SSE 传输守卫与三个聊天端点。"""

import asyncio
from collections.abc import AsyncGenerator, AsyncIterator
from typing import Annotated

from fastapi import APIRouter, Depends
from fastapi.sse import EventSourceResponse, ServerSentEvent
from langgraph.graph.state import CompiledStateGraph

from app.capabilities.knowledge.tools import KnowledgeTools
from app.chat.service import ChatTurnInput, run_chat_turn
from app.contracts.chat import LearningPathChatRequest, QaChatRequest, TextQaChatRequest
from app.contracts.sse import ErrorEvent, SseEvent
from app.contracts.workflow import WorkflowKind
from app.core.config import Settings
from app.core.deps import AppDeps, get_deps
from app.core.errors import BusinessError, ErrorCode, friendly_error
from app.features.conversation.api import get_conversation_service
from app.features.conversation.service import ConversationService
from app.infra.security.user import current_user_id

IDLE_TIMEOUT_MESSAGE = "AI 服务响应超时"
INTERNAL_MESSAGE = "AI 服务内部异常"


async def guard_events(
    events: AsyncIterator[SseEvent],
    *,
    idle_timeout: float,
) -> AsyncGenerator[ServerSentEvent]:
    if idle_timeout <= 0:
        raise ValueError("idle_timeout 必须大于 0")
    iterator = events.__aiter__()
    try:
        while True:
            try:
                async with asyncio.timeout(idle_timeout):
                    event = await anext(iterator)
            except TimeoutError:
                yield ErrorEvent(
                    code=int(ErrorCode.SERVICE_UNAVAILABLE), message=IDLE_TIMEOUT_MESSAGE
                ).encode()
                return
            except StopAsyncIteration:
                yield ErrorEvent(
                    code=int(ErrorCode.INTERNAL_ERROR), message=INTERNAL_MESSAGE
                ).encode()
                return
            except Exception as exc:
                code, message = friendly_error(exc) or (
                    ErrorCode.INTERNAL_ERROR,
                    INTERNAL_MESSAGE,
                )
                yield ErrorEvent(code=int(code), message=message).encode()
                return
            yield event.encode()
            if event.TERMINAL:
                return
    finally:
        await _close(iterator)


async def _close(stream: AsyncIterator[SseEvent]) -> None:
    close = getattr(stream, "aclose", None)
    if callable(close):
        await close()


router = APIRouter(prefix="/api/agent", tags=["chat"])

Deps = Annotated[AppDeps, Depends(get_deps)]
UserId = Annotated[int, Depends(current_user_id)]
Conversation = Annotated[ConversationService, Depends(get_conversation_service)]


def get_chat_graph(deps: Deps) -> CompiledStateGraph:
    graph = deps.graph
    if graph is None:
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "AI 服务暂不可用")
    return graph


def require_learning_tools(deps: Deps) -> KnowledgeTools:
    tools = deps.knowledge_tools
    if tools is None:
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "学习路线服务暂不可用")
    return tools


Graph = Annotated[CompiledStateGraph, Depends(get_chat_graph)]
Learning = Annotated[KnowledgeTools, Depends(require_learning_tools)]


def _sse(
    turn: ChatTurnInput,
    *,
    conversation: ConversationService,
    graph: CompiledStateGraph,
    settings: Settings,
) -> AsyncIterator[ServerSentEvent]:
    events = run_chat_turn(
        turn,
        conversation=conversation,
        graph=graph,
        settings=settings,
    )
    return guard_events(events, idle_timeout=settings.app.stream_idle_timeout)


@router.post("/chat", response_class=EventSourceResponse)
async def qa_chat(
    payload: QaChatRequest,
    user_id: UserId,
    conversation: Conversation,
    graph: Graph,
    deps: Deps,
) -> AsyncIterator[ServerSentEvent]:
    turn = ChatTurnInput(
        user_id=user_id,
        workflow=WorkflowKind.QA,
        question=payload.question,
        conversation_id=payload.conversation_id,
        message_id=payload.message_id,
    )
    async for event in _sse(turn, conversation=conversation, graph=graph, settings=deps.settings):
        yield event


@router.post("/learning-path", response_class=EventSourceResponse)
async def learning_path_chat(
    payload: LearningPathChatRequest,
    user_id: UserId,
    conversation: Conversation,
    graph: Graph,
    deps: Deps,
    _learning: Learning,
) -> AsyncIterator[ServerSentEvent]:
    turn = ChatTurnInput(
        user_id=user_id,
        workflow=WorkflowKind.LEARNING_PATH,
        question=payload.question,
        conversation_id=payload.conversation_id,
        message_id=payload.message_id,
        technology=payload.technology,
        known=list(payload.known_technologies),
    )
    async for event in _sse(turn, conversation=conversation, graph=graph, settings=deps.settings):
        yield event


@router.post("/text-qa", response_class=EventSourceResponse)
async def text_qa_chat(
    payload: TextQaChatRequest,
    user_id: UserId,
    conversation: Conversation,
    graph: Graph,
    deps: Deps,
) -> AsyncIterator[ServerSentEvent]:
    turn = ChatTurnInput(
        user_id=user_id,
        workflow=WorkflowKind.TEXT_QA,
        question=payload.question,
        conversation_id=payload.conversation_id,
        message_id=payload.message_id,
        text=payload.text,
    )
    async for event in _sse(turn, conversation=conversation, graph=graph, settings=deps.settings):
        yield event
