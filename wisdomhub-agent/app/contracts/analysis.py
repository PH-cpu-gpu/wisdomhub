"""文章分析 VO。"""

from typing import Literal, Self

from pydantic import AwareDatetime, model_validator

from app.contracts.base import WireModel

AnalysisStatus = Literal["pending", "done", "error"]


class AnalysisVO(WireModel):
    article_id: str
    status: AnalysisStatus
    topic: str | None = None
    entities: list[str] | None = None
    knowledge_points: list[str] | None = None
    source_updated_at: AwareDatetime | None = None
    analyzed_at: AwareDatetime | None = None
    message: str | None = None

    @model_validator(mode="after")
    def _check_status_fields(self) -> Self:
        has_result = (
            self.topic is not None
            or self.entities is not None
            or self.knowledge_points is not None
            or self.analyzed_at is not None
        )
        if self.status == "done":
            if not all(
                value is not None
                for value in (self.topic, self.entities, self.knowledge_points, self.analyzed_at)
            ):
                raise ValueError(
                    "done 状态必须包含 topic / entities / knowledgePoints / analyzedAt"
                )
            if self.message is not None:
                raise ValueError("done 状态不允许 message")
        elif self.status == "error":
            if not self.message:
                raise ValueError("error 状态必须包含 message")
            if has_result:
                raise ValueError("error 状态不允许分析结果字段")
        else:
            if has_result or self.message is not None:
                raise ValueError("pending 状态不允许分析结果或 message")
        return self
