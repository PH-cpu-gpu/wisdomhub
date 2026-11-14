"""SSE 传输层：帧格式、终态标记与与业务无关的事件。"""

from enum import StrEnum
from typing import ClassVar

from fastapi.sse import ServerSentEvent

from app.contracts.base import WireModel
from app.contracts.workflow import WorkflowKind


class SseEvent(WireModel):
    EVENT: ClassVar[str]
    TERMINAL: ClassVar[bool] = False

    def encode(self) -> ServerSentEvent:
        return ServerSentEvent(
            event=self.EVENT,
            raw_data=self.model_dump_json(by_alias=True, exclude_none=True),
        )


class MetaEvent(SseEvent):
    EVENT: ClassVar[str] = "meta"
    conversation_id: str
    message_id: str
    workflow: WorkflowKind


class StepStatus(StrEnum):
    RUNNING = "running"
    DONE = "done"
    ERROR = "error"


class Step(WireModel):
    id: str
    tool: str
    label: str
    status: StepStatus
    summary: str | None = None


class StepsEvent(SseEvent):
    EVENT: ClassVar[str] = "steps"
    steps: list[Step]


class DeltaEvent(SseEvent):
    EVENT: ClassVar[str] = "delta"
    text: str


class DoneEvent(SseEvent):
    EVENT: ClassVar[str] = "done"
    TERMINAL: ClassVar[bool] = True
    finish_reason: str = "stop"


class ErrorEvent(SseEvent):
    EVENT: ClassVar[str] = "error"
    TERMINAL: ClassVar[bool] = True
    code: int
    message: str
