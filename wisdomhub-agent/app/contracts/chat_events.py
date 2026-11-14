"""聊天业务事件与端点事件集合。顺序：meta → route? → steps* → delta* → done。"""

from typing import ClassVar

from app.contracts.artifacts import RouteData
from app.contracts.sse import (
    DeltaEvent,
    DoneEvent,
    ErrorEvent,
    MetaEvent,
    SseEvent,
    StepsEvent,
)


class RouteEvent(RouteData, SseEvent):
    EVENT: ClassVar[str] = "route"


QaStreamEvent = MetaEvent | DeltaEvent | DoneEvent | ErrorEvent
TextQaStreamEvent = QaStreamEvent
LearningStreamEvent = QaStreamEvent | StepsEvent | RouteEvent
