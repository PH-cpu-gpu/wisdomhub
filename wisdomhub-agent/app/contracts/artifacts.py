"""消息产物：与 SSE 事件解耦的持久化数据。"""

from app.contracts.base import WireModel


class TechRef(WireModel):
    tag_id: str
    name: str


class PlanStage(WireModel):
    depth: int
    tags: list[TechRef]


class RouteData(WireModel):
    target: TechRef
    known: list[str]
    truncated: bool
    stages: list[PlanStage]


class MessageArtifacts(WireModel):
    route: RouteData | None = None
