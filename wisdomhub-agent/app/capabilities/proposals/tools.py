"""提案工具：标签/关系提案、自检与进程内限流。"""

import json
import logging
from collections import deque
from collections.abc import Mapping
from dataclasses import dataclass
from time import monotonic
from typing import Any, Protocol

from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import Field, ValidationError

from app.capabilities.base import CapabilityModel, InternalPoster
from app.capabilities.errors import ToolErrorKind, ToolException, from_internal_error
from app.infra.http.internal_client import (
    InternalBusinessError,
    InternalClientError,
    InternalNotFound,
)
from app.runtime.structured import StructuredLLM, invoke_structured

log = logging.getLogger(__name__)

CONTENT_SERVICE = "content-service"
TAG_PROPOSAL_PATH = "/internal/content/tag-proposal"
PROPOSER_AI = 1
TYPE_TAG = 1
TYPE_RELATION = 2
RATE_WINDOW_SECONDS = 3600.0

_REFLECTION_FAILED = "提案自检失败，请稍后重试"

_REFLECTION_PROMPT = (
    "你是知识图谱治理助手。判断给定的标签或关系提案是否具体、是否有证据支持、是否值得提交人工审核。"
)


@dataclass(frozen=True, slots=True)
class Caller:
    user_id: int


class ProposalIssue(CapabilityModel):
    field: str | None = None
    code: str | None = None
    message: str
    severity: str | None = None


class ProposalResult(CapabilityModel):
    id: str
    proposal_type: int
    status: int
    target_key: str


class SubmitResponse(CapabilityModel):
    valid: bool
    issues: list[ProposalIssue] = Field(default_factory=list)
    proposal: ProposalResult | None = None


class ProposalReflection(CapabilityModel):
    """提案自检结果：是否建议提交给人工审核，以及判断理由。"""

    approved: bool = Field(
        description="是否建议提交该提案给人工审核；true=建议提交，false=建议拒绝"
    )
    reason: str = Field(
        description="判断理由；approved=false 时说明拒绝原因，approved=true 时说明通过依据"
    )


class Reflection(Protocol):
    async def __call__(self, proposal: Mapping[str, Any]) -> ProposalReflection: ...


class LlmReflection:
    def __init__(self, llm: StructuredLLM, *, max_retries: int) -> None:
        self._llm = llm
        self._max_retries = max_retries

    async def __call__(self, proposal: Mapping[str, Any]) -> ProposalReflection:
        messages = [
            SystemMessage(content=_REFLECTION_PROMPT),
            HumanMessage(content=json.dumps(proposal, ensure_ascii=False, default=str)),
        ]
        return await invoke_structured(
            self._llm, ProposalReflection, messages, max_retries=self._max_retries
        )


class _RateLimiter:
    def __init__(self, limit: int, *, window: float = RATE_WINDOW_SECONDS) -> None:
        if limit < 1:
            raise ValueError("rate_limit_per_hour 必须大于 0")
        self._limit = limit
        self._window = window
        self._events: dict[int, deque[float]] = {}

    def allow(self, user_id: int) -> bool:
        now = monotonic()
        events = self._events.setdefault(user_id, deque())
        cutoff = now - self._window
        while events and events[0] <= cutoff:
            events.popleft()
        if len(events) >= self._limit:
            return False
        events.append(now)
        return True


class ProposalTools:
    def __init__(
        self,
        internal: InternalPoster,
        reflection: Reflection,
        *,
        rate_limit_per_hour: int,
    ) -> None:
        self._internal = internal
        self._reflection = reflection
        self._limiter = _RateLimiter(rate_limit_per_hour)

    async def propose_tag(
        self,
        caller: Caller,
        name: str,
        category: str | None = None,
        description: str | None = None,
        evidence: dict[str, Any] | None = None,
    ) -> ProposalResult:
        _require_caller(caller)
        tag_name = name.strip() if isinstance(name, str) else ""
        if not tag_name:
            raise ToolException(ToolErrorKind.INVALID_INPUT, "标签名不能为空")
        if not self._limiter.allow(caller.user_id):
            raise ToolException(ToolErrorKind.RATE_LIMITED)

        await self._check(
            {
                "type": "tag",
                "name": tag_name,
                "category": category,
                "description": description,
                "evidence": evidence,
            }
        )
        return await self._submit(
            {
                "proposalType": TYPE_TAG,
                "name": tag_name,
                "category": category,
                "description": description,
                "proposerType": PROPOSER_AI,
                "proposerId": caller.user_id,
                "evidence": evidence,
            }
        )

    async def propose_relation(
        self,
        caller: Caller,
        from_tag_id: str,
        to_tag_id: str,
        relation_type: str,
        evidence: dict[str, Any] | None = None,
    ) -> ProposalResult:
        _require_caller(caller)
        source = from_tag_id.strip() if isinstance(from_tag_id, str) else ""
        target = to_tag_id.strip() if isinstance(to_tag_id, str) else ""
        relation = relation_type.strip().upper() if isinstance(relation_type, str) else ""
        if not source or not target:
            raise ToolException(ToolErrorKind.INVALID_INPUT, "关系两端不能为空")
        if not relation:
            raise ToolException(ToolErrorKind.INVALID_INPUT, "关系类型不能为空")
        if not self._limiter.allow(caller.user_id):
            raise ToolException(ToolErrorKind.RATE_LIMITED)

        await self._check(
            {
                "type": "relation",
                "fromTagId": source,
                "toTagId": target,
                "relationType": relation,
                "evidence": evidence,
            }
        )
        return await self._submit(
            {
                "proposalType": TYPE_RELATION,
                "fromTagId": source,
                "toTagId": target,
                "relationType": relation,
                "proposerType": PROPOSER_AI,
                "proposerId": caller.user_id,
                "evidence": evidence,
            }
        )

    async def _check(self, proposal: Mapping[str, Any]) -> None:
        try:
            reflection = await self._reflection(proposal)
        except Exception as exc:
            log.warning("提案自检失败", exc_info=True)
            raise ToolException(ToolErrorKind.UNAVAILABLE, _REFLECTION_FAILED) from exc
        if not reflection.approved:
            reason = reflection.reason.strip() or "未说明原因"
            raise ToolException(ToolErrorKind.REJECTED, f"提案未通过自检：{reason}")

    async def _submit(self, body: dict[str, Any]) -> ProposalResult:
        try:
            data = await self._internal.post(CONTENT_SERVICE, TAG_PROPOSAL_PATH, json_body=body)
        except InternalBusinessError as exc:
            message = _issue_message(exc.data) or str(exc)
            raise ToolException(ToolErrorKind.REJECTED, f"提案未通过校验：{message}") from exc
        except InternalNotFound as exc:
            raise ToolException(ToolErrorKind.REJECTED, f"提案未通过校验：{exc}") from exc
        except InternalClientError as exc:
            raise from_internal_error(exc) from exc

        if not isinstance(data, dict):
            raise ToolException(ToolErrorKind.INTERNAL)
        try:
            response = SubmitResponse.model_validate(data)
        except ValidationError as exc:
            raise ToolException(ToolErrorKind.INTERNAL) from exc
        if not response.valid:
            message = _issue_message(data)
            if message:
                raise ToolException(ToolErrorKind.REJECTED, f"提案未通过校验：{message}")
            raise ToolException(ToolErrorKind.INTERNAL)
        if response.proposal is None:
            raise ToolException(ToolErrorKind.INTERNAL)
        return response.proposal


def _require_caller(caller: Caller) -> None:
    if not isinstance(caller, Caller) or not isinstance(caller.user_id, int) or caller.user_id <= 0:
        raise ToolException(ToolErrorKind.UNAUTHORIZED)


def _issue_message(data: Any) -> str:
    if not isinstance(data, dict):
        return ""
    try:
        response = SubmitResponse.model_validate(data)
    except ValidationError:
        return ""
    return "；".join(issue.message for issue in response.issues if issue.message)
