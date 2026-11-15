"""知识图谱工具：前置知识链。"""

from pydantic import ValidationError

from app.capabilities.base import CapabilityModel, InternalGetter
from app.capabilities.errors import ToolErrorKind, ToolException, from_internal_error
from app.infra.http.internal_client import InternalClientError

KNOWLEDGE_SERVICE = "knowledge-service"
QUERY_PREREQUISITE_CHAIN = "query_prerequisite_chain"
MAX_DEPTH = 5
MAX_TECH_LENGTH = 50


class TagBrief(CapabilityModel):
    tag_id: str
    name: str


class PrerequisiteLevel(CapabilityModel):
    depth: int
    tags: list[TagBrief]


class PrerequisiteTree(CapabilityModel):
    tag_id: str
    name: str
    depth: int
    levels: list[PrerequisiteLevel]
    truncated: bool


class KnowledgeTools:
    def __init__(self, internal: InternalGetter) -> None:
        self._internal = internal

    async def query_prerequisite_chain(self, technology: str) -> PrerequisiteTree:
        tech = technology.strip() if isinstance(technology, str) else ""
        if not tech:
            raise ToolException(ToolErrorKind.INVALID_INPUT, "技术名不能为空")
        if len(tech) > MAX_TECH_LENGTH:
            raise ToolException(
                ToolErrorKind.INVALID_INPUT,
                f"技术名称不能超过 {MAX_TECH_LENGTH} 个字符",
            )

        path = "/internal/knowledge/prerequisites"
        try:
            data = await self._internal.get(
                KNOWLEDGE_SERVICE,
                path,
                params={"tech": tech, "maxDepth": MAX_DEPTH},
            )
        except InternalClientError as exc:
            raise from_internal_error(exc, not_found=f"知识图谱中没有找到技术「{tech}」") from exc

        if not isinstance(data, dict):
            raise ToolException(ToolErrorKind.INTERNAL)
        try:
            return PrerequisiteTree.model_validate(data)
        except ValidationError as exc:
            raise ToolException(ToolErrorKind.INTERNAL) from exc
