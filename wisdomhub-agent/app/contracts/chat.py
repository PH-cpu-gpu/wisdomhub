"""Chat 请求契约：三个端点各自的请求体。"""

from typing import Annotated
from uuid import UUID

from pydantic import Field, StringConstraints, field_validator

from app.contracts.base import WireModel

Question = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=4000)]
Technology = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=50)]
Text = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=2000)]
MessageId = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1, max_length=64)]


class ChatRequestBase(WireModel):
    conversation_id: UUID | None = None
    message_id: MessageId | None = None
    question: Question

    @field_validator("conversation_id", "message_id", mode="before")
    @classmethod
    def _blank_to_none(cls, value: object) -> object:
        if isinstance(value, str) and not value.strip():
            return None
        return value


class QaChatRequest(ChatRequestBase):
    pass


class LearningPathChatRequest(ChatRequestBase):
    technology: Technology
    known_technologies: list[Technology] = Field(default_factory=list, max_length=20)

    @field_validator("known_technologies", mode="before")
    @classmethod
    def _clean_known_technologies(cls, value: object) -> object:
        if value is None:
            return []
        if not isinstance(value, list):
            return value
        return [
            item.strip() if isinstance(item, str) else item
            for item in value
            if not (isinstance(item, str) and not item.strip())
        ]


class TextQaChatRequest(ChatRequestBase):
    text: Text
