"""会话图状态：一张图承载 QA、学习路线与文本问答。"""

from enum import StrEnum
from typing import NotRequired

from langgraph.graph import MessagesState

from app.contracts.artifacts import RouteData
from app.contracts.sse import Step
from app.contracts.workflow import WorkflowKind


class FetchStatus(StrEnum):
    """前置链取数结果：成功、技术不存在、上游不可用。"""

    OK = "ok"
    NOT_FOUND = "not_found"
    UNAVAILABLE = "unavailable"


class ChatState(MessagesState):
    """messages 由 add_messages 管理；业务字段按分支可选，互不读写。"""

    workflow: NotRequired[WorkflowKind]
    technology: NotRequired[str]
    known: NotRequired[list[str]]
    text: NotRequired[str]
    route: NotRequired[RouteData]
    steps: NotRequired[list[Step]]
    fetch_status: NotRequired[FetchStatus]
    fetch_error: NotRequired[str]
