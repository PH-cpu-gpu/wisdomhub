"""提案工具：越权、限流、自检与提交。"""

from collections.abc import Mapping, Sequence
from typing import Any, Self

import pytest
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from langchain_core.utils.function_calling import convert_to_openai_tool
from pydantic import BaseModel

from app.capabilities.errors import ToolErrorKind, ToolException
from app.capabilities.proposals.tools import (
    CONTENT_SERVICE,
    TAG_PROPOSAL_PATH,
    Caller,
    LlmReflection,
    ProposalReflection,
    ProposalTools,
)
from app.infra.http.internal_client import (
    InternalBusinessError,
    InternalNotFound,
    InternalUnavailable,
)
from app.runtime.structured import Method
from tests.fakes import FakeInternalClient


class _FakeReflection:
    def __init__(
        self,
        approved: bool = True,
        reason: str = "ok",
        error: Exception | None = None,
    ) -> None:
        self.approved = approved
        self.reason = reason
        self.error = error
        self.proposals: list[dict[str, Any]] = []

    async def __call__(self, proposal: Mapping[str, Any]) -> ProposalReflection:
        self.proposals.append(dict(proposal))
        if self.error is not None:
            raise self.error
        return ProposalReflection(approved=self.approved, reason=self.reason)


class _FakeStructuredLLM:
    def __init__(self, result: ProposalReflection) -> None:
        self.result = result
        self.messages: list[list[BaseMessage]] = []
        self.schema: type[BaseModel] | None = None
        self.method: Method | None = None

    def with_structured_output(
        self, schema: type[BaseModel], *, method: Method = "function_calling"
    ) -> Self:
        self.schema = schema
        self.method = method
        return self

    async def ainvoke(self, input: Sequence[BaseMessage], config: Any = None, **kwargs: Any) -> Any:
        self.messages.append(list(input))
        return self.result


def _success(**proposal_overrides: Any) -> dict[str, Any]:
    proposal: dict[str, Any] = {
        "id": "5001",
        "proposalType": 1,
        "status": 0,
        "targetKey": "redis cluster",
        "name": "Redis Cluster",
        "proposerType": 1,
        "proposerId": 1001,
    }
    proposal.update(proposal_overrides)
    return {"valid": True, "issues": [], "proposal": proposal}


def _tools(
    internal: FakeInternalClient,
    reflection: _FakeReflection | None = None,
    *,
    limit: int = 5,
) -> ProposalTools:
    return ProposalTools(internal, reflection or _FakeReflection(), rate_limit_per_hour=limit)


async def test_propose_tag_submits_as_ai_proposer() -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): _success()})
    reflection = _FakeReflection()
    tools = _tools(internal, reflection)

    result = await tools.propose_tag(
        Caller(1001),
        "Redis Cluster",
        category="MIDDLEWARE",
        description="集群",
        evidence={"articleId": "2001"},
    )

    assert result.id == "5001"
    assert result.proposal_type == 1
    assert result.status == 0
    assert result.target_key == "redis cluster"
    call = internal.calls[0]
    assert call.service == CONTENT_SERVICE
    assert call.path == TAG_PROPOSAL_PATH
    assert call.json == {
        "proposalType": 1,
        "name": "Redis Cluster",
        "category": "MIDDLEWARE",
        "description": "集群",
        "proposerType": 1,
        "proposerId": 1001,
        "evidence": {"articleId": "2001"},
    }
    assert reflection.proposals[0]["type"] == "tag"


async def test_propose_relation_normalizes_relation_type() -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): _success(proposalType=2)})
    tools = _tools(internal)

    result = await tools.propose_relation(Caller(1001), " 3001 ", "3002", "prerequisite")

    assert result.proposal_type == 2
    body = internal.calls[0].json
    assert body is not None
    assert body["proposalType"] == 2
    assert body["fromTagId"] == "3001"
    assert body["toTagId"] == "3002"
    assert body["relationType"] == "PREREQUISITE"
    assert body["proposerType"] == 1
    assert body["proposerId"] == 1001


@pytest.mark.parametrize("user_id", [0, -1])
async def test_invalid_caller_is_rejected(user_id: int) -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): _success()})
    reflection = _FakeReflection()
    tools = _tools(internal, reflection)

    with pytest.raises(ToolException, match="未登录") as excinfo:
        await tools.propose_tag(Caller(user_id), "Redis")

    assert excinfo.value.kind is ToolErrorKind.UNAUTHORIZED
    assert reflection.proposals == []
    assert internal.calls == []


async def test_blank_tag_name_is_rejected() -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): _success()})
    reflection = _FakeReflection()
    tools = _tools(internal, reflection)

    with pytest.raises(ToolException, match="标签名不能为空") as excinfo:
        await tools.propose_tag(Caller(1), "   ")

    assert excinfo.value.kind is ToolErrorKind.INVALID_INPUT
    assert reflection.proposals == []
    assert internal.calls == []


@pytest.mark.parametrize(
    ("from_id", "to_id", "relation"),
    [("", "2", "RELATED"), ("1", "", "RELATED"), ("1", "2", "  ")],
)
async def test_blank_relation_fields_are_rejected(from_id: str, to_id: str, relation: str) -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): _success()})
    tools = _tools(internal)

    with pytest.raises(ToolException) as excinfo:
        await tools.propose_relation(Caller(1), from_id, to_id, relation)

    assert excinfo.value.kind is ToolErrorKind.INVALID_INPUT
    assert internal.calls == []


async def test_reflection_rejection_blocks_submission() -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): _success()})
    reflection = _FakeReflection(approved=False, reason="证据不足")
    tools = _tools(internal, reflection)

    with pytest.raises(ToolException, match="提案未通过自检：证据不足") as excinfo:
        await tools.propose_tag(Caller(1), "Redis")

    assert excinfo.value.kind is ToolErrorKind.REJECTED
    assert internal.calls == []


async def test_reflection_timeout_blocks_submission() -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): _success()})
    reflection = _FakeReflection(error=TimeoutError())
    tools = _tools(internal, reflection)

    with pytest.raises(ToolException, match="提案自检失败") as excinfo:
        await tools.propose_tag(Caller(1), "Redis")

    assert excinfo.value.kind is ToolErrorKind.UNAVAILABLE
    assert internal.calls == []


async def test_rate_limit_is_shared_between_tools() -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): _success()})
    reflection = _FakeReflection()
    tools = _tools(internal, reflection, limit=2)

    await tools.propose_tag(Caller(1), "A")
    await tools.propose_relation(Caller(1), "1", "2", "RELATED")
    with pytest.raises(ToolException, match="请求过于频繁") as excinfo:
        await tools.propose_tag(Caller(1), "C")

    assert excinfo.value.kind is ToolErrorKind.RATE_LIMITED
    assert len(internal.calls) == 2
    assert len(reflection.proposals) == 2

    await tools.propose_tag(Caller(2), "D")
    assert len(internal.calls) == 3


async def test_java_validation_issues_become_user_message() -> None:
    error = InternalBusinessError(
        "标签或别名已存在",
        service=CONTENT_SERVICE,
        path=TAG_PROPOSAL_PATH,
        code=409,
        data={
            "valid": False,
            "issues": [
                {
                    "field": "name",
                    "code": "TAG_EXISTS",
                    "message": "标签或别名已存在",
                    "severity": "ERROR",
                },
                {
                    "field": "targetKey",
                    "code": "PENDING_EXISTS",
                    "message": "该目标已有待审提案",
                    "severity": "ERROR",
                },
            ],
        },
    )
    tools = _tools(FakeInternalClient(error=error))

    with pytest.raises(ToolException) as excinfo:
        await tools.propose_tag(Caller(1), "Redis")

    assert excinfo.value.kind is ToolErrorKind.REJECTED
    assert str(excinfo.value) == "提案未通过校验：标签或别名已存在；该目标已有待审提案"
    assert "content-service" not in str(excinfo.value)
    assert "{" not in str(excinfo.value)


async def test_java_not_found_becomes_user_message() -> None:
    error = InternalNotFound(
        "标签不存在",
        service=CONTENT_SERVICE,
        path=TAG_PROPOSAL_PATH,
        status=404,
        code=404,
    )
    tools = _tools(FakeInternalClient(error=error))

    with pytest.raises(ToolException, match="提案未通过校验：标签不存在") as excinfo:
        await tools.propose_relation(Caller(1), "1", "2", "RELATED")

    assert excinfo.value.kind is ToolErrorKind.REJECTED


async def test_upstream_error_becomes_unavailable() -> None:
    error = InternalUnavailable("down", service=CONTENT_SERVICE, path=TAG_PROPOSAL_PATH)
    tools = _tools(FakeInternalClient(error=error))

    with pytest.raises(ToolException, match="服务暂不可用") as excinfo:
        await tools.propose_tag(Caller(1), "Redis")

    assert excinfo.value.kind is ToolErrorKind.UNAVAILABLE


async def test_submit_timeout_becomes_unavailable() -> None:
    tools = _tools(FakeInternalClient(timeout=True))

    with pytest.raises(ToolException) as excinfo:
        await tools.propose_tag(Caller(1), "Redis")

    assert excinfo.value.kind is ToolErrorKind.UNAVAILABLE
    assert str(excinfo.value) == "服务暂不可用，请稍后重试"


@pytest.mark.parametrize(
    "data",
    [
        "oops",
        {"valid": True, "issues": [], "proposal": None},
        {"valid": False, "issues": []},
    ],
)
async def test_invalid_response_becomes_user_message(data: Any) -> None:
    tools = _tools(FakeInternalClient({(CONTENT_SERVICE, TAG_PROPOSAL_PATH): data}))

    with pytest.raises(ToolException, match="服务返回异常") as excinfo:
        await tools.propose_tag(Caller(1), "Redis")

    assert excinfo.value.kind is ToolErrorKind.INTERNAL


async def test_llm_reflection_builds_prompt_and_parses_result() -> None:
    llm = _FakeStructuredLLM(ProposalReflection(approved=True, reason="ok"))
    reflection = LlmReflection(llm, max_retries=1)

    result = await reflection({"type": "tag", "name": "Redis"})

    assert result.approved is True
    assert llm.schema is ProposalReflection
    assert llm.method == "function_calling"
    assert isinstance(llm.messages[0][0], SystemMessage)
    assert isinstance(llm.messages[0][1], HumanMessage)
    assert "Redis" in str(llm.messages[0][1].content)


def test_reflection_schema_documents_fields() -> None:
    tool = convert_to_openai_tool(ProposalReflection)
    function = tool["function"]
    properties = function["parameters"]["properties"]

    assert function["description"]
    assert properties["approved"]["description"]
    assert properties["reason"]["description"]
