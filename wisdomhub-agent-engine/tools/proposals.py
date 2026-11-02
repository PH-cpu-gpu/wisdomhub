import asyncio
import json
import logging
import time
from collections import deque
from typing import Annotated, Any, Literal

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_core.tools import BaseTool, ToolException, tool
from pydantic import BaseModel, Field

from app.config import Settings
from models.structured import invoke_structured
from prompts.proposals import RELATION_REFLECTION_PROMPT, TAG_REFLECTION_PROMPT
from tools.internal_client import InternalClient, InternalClientError
from tools.java_api import CONTENT_SERVICE
from tools.request_context import current_chat_user

log = logging.getLogger(__name__)

TAG_PROPOSAL_TOOL_NAME = "propose_tag"
RELATION_PROPOSAL_TOOL_NAME = "propose_relation"

TAG_RESOLVE_PATH = "/internal/content/tag/resolve"
TAG_PROPOSAL_PATH = "/internal/content/tag-proposal"

PROPOSAL_TYPE_TAG = 1
PROPOSAL_TYPE_RELATION = 2
PROPOSER_AI = 1

MAX_NAME_CHARS = 50
MAX_CATEGORY_CHARS = 20
MAX_DESCRIPTION_CHARS = 255
MAX_REASON_CHARS = 200

RELATION_TYPE_CHOICES = ("PREREQUISITE", "BASED_ON", "USES", "SIMILAR_TO", "BELONGS_TO")

# 改一改就能过的 issue，值得把问题回给模型再提交一次；TAG_EXISTS / RELATION_CYCLE 这类改了也没用
FIXABLE_ISSUES = {
    "NAME_REQUIRED",
    "NAME_TOO_LONG",
    "CATEGORY_TOO_LONG",
    "DESCRIPTION_TOO_LONG",
    "RELATION_TYPE_INVALID",
}


class TagReflection(BaseModel):
    independent_technology: bool = Field(default=False, description="是否是独立、可单独学习的技术概念")
    name: str = Field(default="", description="规范后的技术名，保留原文大小写，不超过 50 字")
    category: str = Field(default="", description="分组，不超过 20 字")
    description: str = Field(default="", description="一句话说明它是什么，不超过 80 字")
    reason: str = Field(default="", description="判断依据，不超过 100 字")


class RelationReflection(BaseModel):
    valid: bool = Field(default=False, description="这条关系是否真实成立且值得收录")
    relation_type: Literal[
        "PREREQUISITE", "BASED_ON", "USES", "SIMILAR_TO", "BELONGS_TO"
    ] = Field(default="USES", description="关系类型，必须是给定枚举之一")
    from_technology: str = Field(default="", description="关系起点技术名，方向不对时按正确方向填写")
    to_technology: str = Field(default="", description="关系终点技术名，方向不对时按正确方向填写")
    reason: str = Field(default="", description="判断依据，不超过 100 字")


def _rate_limited() -> dict[str, Any]:
    return {
        "status": "rate_limited",
        "message": "本小时申请次数已用完，提案没有提交；请如实告知用户稍后再试，不要编造已经提交",
    }


def _issues(data: Any) -> list[dict[str, str]]:
    """提案接口失败时 data 里带 issues，是 B4.8 里唯一失败也返回 data 的约定。"""
    if not isinstance(data, dict):
        return []
    raw = data.get("issues")
    if not isinstance(raw, list):
        return []
    return [
        {
            "code": str(item.get("code") or ""),
            "field": str(item.get("field") or ""),
            "message": str(item.get("message") or ""),
        }
        for item in raw
        if isinstance(item, dict)
    ]


def _fixable(issues: list[dict[str, str]]) -> bool:
    return bool(issues) and all(issue["code"] in FIXABLE_ISSUES for issue in issues)


class _RateLimiter:
    """进程内滑动窗口限流：同一用户一小时内最多提交几次提案。

    多实例部署时是「每实例」口径；提案本身还有 content 侧的 PENDING 唯一键兜底，
    这里的目标只是别让一次对话无限刷管理员通知。
    """

    def __init__(self, limit: int, window_seconds: float = 3600.0) -> None:
        self._limit = max(1, limit)
        self._window = window_seconds
        self._hits: dict[int, deque[float]] = {}
        self._lock = asyncio.Lock()

    async def allow(self, user_id: int) -> bool:
        now = time.monotonic()
        async with self._lock:
            hits = self._hits.setdefault(user_id, deque())
            while hits and now - hits[0] > self._window:
                hits.popleft()
            if len(hits) >= self._limit:
                return False
            hits.append(now)
            return True


class ProposalService:
    """自检（reflection）→ 提交 → 读 issues 修正一次；全程失败只返回状态，不抛给对话。"""

    def __init__(self, client: InternalClient, llm: BaseChatModel, settings: Settings) -> None:
        self._client = client
        self._llm = llm
        self._limiter = _RateLimiter(settings.proposal_rate_limit_per_hour)

    # ---------------- propose_tag ----------------

    async def propose_tag(
        self,
        *,
        name: str,
        category: str | None,
        description: str | None,
        reason: str | None,
    ) -> dict[str, Any]:
        technology = (name or "").strip()
        if not technology:
            raise ToolException("name 不能为空")
        if len(technology) > MAX_NAME_CHARS:
            raise ToolException(f"技术名不能超过 {MAX_NAME_CHARS} 字")

        rejected = await self._check_quota()
        if rejected is not None:
            return rejected

        reflected = await self._reflect_tag(
            name=technology, category=category, description=description
        )
        if reflected is None:
            return {"status": "reflection_failed", "message": "自检没跑完，提案没有提交；请如实告知用户稍后再试"}
        if not reflected.independent_technology:
            return {
                "status": "not_a_technology",
                "reason": reflected.reason,
                "message": "自检判断这不是一项独立技术，已放弃申请；请如实告知用户，不要说已经提交",
            }

        payload = self._tag_payload(reflected, technology, reason)
        result = await self._submit(payload)
        if result["status"] == "invalid" and _fixable(result["issues"]):
            # 失败读 issues 修正：只重来一次，避免死循环
            again = await self._reflect_tag(
                name=payload["name"],
                category=payload.get("category"),
                description=payload.get("description"),
                issues=result["issues"],
            )
            if again is not None and again.independent_technology:
                payload = self._tag_payload(again, technology, reason)
                result = await self._submit(payload)
        if result["status"] == "submitted":
            result["name"] = payload["name"]
        return result

    # ---------------- propose_relation ----------------

    async def propose_relation(
        self,
        *,
        from_technology: str,
        to_technology: str,
        relation_type: str,
        reason: str | None,
    ) -> dict[str, Any]:
        source = (from_technology or "").strip()
        target = (to_technology or "").strip()
        if not source or not target:
            raise ToolException("from_technology 与 to_technology 都不能为空")
        if source.casefold() == target.casefold():
            raise ToolException("不能给同一个技术建关系")
        if len(source) > MAX_NAME_CHARS or len(target) > MAX_NAME_CHARS:
            raise ToolException(f"技术名不能超过 {MAX_NAME_CHARS} 字")

        rejected = await self._check_quota()
        if rejected is not None:
            return rejected

        resolved = await self._resolve_names([source, target])
        if resolved is None:
            return {"status": "unavailable", "message": "标签服务暂不可用，关系提案没有提交"}
        missing = [name for name in (source, target) if name.casefold() not in resolved]
        if missing:
            # 两端必须已收录，先省掉一次自检
            return {
                "status": "tag_not_found",
                "missing": missing,
                "message": "关系两端必须已收录；请先为缺失的技术申请标签",
            }

        hinted = (relation_type or "").strip().upper()
        if hinted not in RELATION_TYPE_CHOICES:
            hinted = ""
        reflected = await self._reflect_relation(source, target, hinted, reason)
        if reflected is None:
            return {"status": "reflection_failed", "message": "自检没跑完，关系提案没有提交；请如实告知用户稍后再试"}
        if not reflected.valid:
            return {
                "status": "not_valid",
                "reason": reflected.reason,
                "message": "自检判断这条关系不成立，已放弃申请；请如实告知用户，不要说已经提交",
            }

        pair = self._match_names(resolved, reflected.from_technology, reflected.to_technology, source, target)
        if pair is None:
            return {
                "status": "not_valid",
                "reason": reflected.reason,
                "message": "自检没能确认关系方向，已放弃申请；请如实告知用户",
            }
        payload = self._relation_payload(pair, reflected.relation_type, reason, reflected.reason)
        result = await self._submit(payload)
        if result["status"] == "invalid" and _fixable(result["issues"]):
            again = await self._reflect_relation(
                source, target, "", reason, issues=result["issues"]
            )
            if again is not None and again.valid:
                pair = self._match_names(
                    resolved, again.from_technology, again.to_technology, source, target
                )
                if pair is not None:
                    payload = self._relation_payload(pair, again.relation_type, reason, again.reason)
                    result = await self._submit(payload)
        if result["status"] == "submitted":
            result["relationType"] = payload["relationType"]
        return result

    # ---------------- 内部步骤 ----------------

    async def _check_quota(self) -> dict[str, Any] | None:
        user_id = current_chat_user()
        if user_id <= 0:
            return {"status": "no_user", "message": "缺少用户身份，提案没有提交"}
        if not await self._limiter.allow(user_id):
            return _rate_limited()
        return None

    async def _reflect_tag(
        self,
        *,
        name: str,
        category: str | None,
        description: str | None,
        issues: list[dict[str, str]] | None = None,
    ) -> TagReflection | None:
        context: dict[str, Any] = {
            "name": name,
            "category": category or "",
            "description": description or "",
        }
        if issues:
            context["previous_issues"] = issues
            context["note"] = "上一版字段没通过校验，请按 issues 修正后重新输出"
        return await self._reflect(TagReflection, TAG_REFLECTION_PROMPT, context, "标签")

    async def _reflect_relation(
        self,
        source: str,
        target: str,
        relation_type: str,
        reason: str | None,
        issues: list[dict[str, str]] | None = None,
    ) -> RelationReflection | None:
        context: dict[str, Any] = {
            "from": source,
            "to": target,
            "suggested_relation_type": relation_type,
            "user_reason": (reason or "")[:MAX_REASON_CHARS],
        }
        if issues:
            context["previous_issues"] = issues
            context["note"] = "上一版没通过校验，请按 issues 修正后重新输出"
        return await self._reflect(RelationReflection, RELATION_REFLECTION_PROMPT, context, "关系")

    async def _reflect(self, schema, system_prompt: str, context: dict[str, Any], label: str):
        messages = [
            SystemMessage(content=system_prompt),
            HumanMessage(content=json.dumps(context, ensure_ascii=False)),
        ]
        try:
            return await invoke_structured(self._llm, schema, messages)
        except Exception as exc:
            log.warning("%s reflection 失败: %s", label, type(exc).__name__)
            return None

    async def _resolve_names(self, names: list[str]) -> dict[str, int] | None:
        try:
            data = await self._client.post(CONTENT_SERVICE, TAG_RESOLVE_PATH, json_body=names)
        except Exception as exc:
            log.warning("标签名解析失败: %s", type(exc).__name__)
            return None
        resolved: dict[str, int] = {}
        for row in data or []:
            if not isinstance(row, dict):
                continue
            key = str(row.get("name") or "").strip()
            tag_id = row.get("tagId")
            if key and tag_id is not None:
                resolved[key.casefold()] = int(tag_id)
        return resolved

    def _match_names(
        self,
        resolved: dict[str, int],
        reflected_from: str,
        reflected_to: str,
        source: str,
        target: str,
    ) -> tuple[int, int] | None:
        from_key = (reflected_from or source).strip().casefold()
        to_key = (reflected_to or target).strip().casefold()
        from_id = resolved.get(from_key)
        to_id = resolved.get(to_key)
        if from_id is None or to_id is None or from_id == to_id:
            return None
        return from_id, to_id

    async def _submit(self, payload: dict[str, Any]) -> dict[str, Any]:
        try:
            data = await self._client.post(CONTENT_SERVICE, TAG_PROPOSAL_PATH, json_body=payload)
        except InternalClientError as exc:
            issues = _issues(exc.data)
            if issues:
                return {
                    "status": "invalid",
                    "issues": issues,
                    "message": str(exc) or "提案未通过校验",
                }
            if exc.kind == "unavailable":
                return {"status": "unavailable", "message": "内容服务暂不可用，提案没有提交成功"}
            log.warning("提案提交失败 kind=%s code=%s", exc.kind, exc.code)
            return {"status": "failed", "message": "提案提交失败，请如实告知用户稍后再试"}
        except Exception as exc:
            log.warning("提案提交异常: %s", type(exc).__name__)
            return {"status": "failed", "message": "提案提交失败，请如实告知用户稍后再试"}
        if not isinstance(data, dict):
            return {"status": "failed", "message": "提案接口返回格式异常"}
        proposal = data.get("proposal") or {}
        return {
            "status": "submitted",
            "proposalId": proposal.get("id"),
            "targetKey": proposal.get("targetKey"),
            "message": "已提交给管理员审核，当前是待审状态，还没有收录进图谱",
        }

    @staticmethod
    def _tag_payload(reflected: TagReflection, fallback_name: str, reason: str | None) -> dict[str, Any]:
        name = (reflected.name or "").strip() or fallback_name
        return {
            "proposalType": PROPOSAL_TYPE_TAG,
            "name": name[:MAX_NAME_CHARS],
            "category": (reflected.category or "").strip()[:MAX_CATEGORY_CHARS] or None,
            "description": (reflected.description or "").strip()[:MAX_DESCRIPTION_CHARS] or None,
            "proposerType": PROPOSER_AI,
            "evidence": {
                "source": "agent_chat",
                "reason": (reason or reflected.reason or "")[:MAX_REASON_CHARS],
            },
        }

    @staticmethod
    def _relation_payload(
        pair: tuple[int, int],
        relation_type: str,
        reason: str | None,
        reflected_reason: str,
    ) -> dict[str, Any]:
        return {
            "proposalType": PROPOSAL_TYPE_RELATION,
            "fromTagId": pair[0],
            "toTagId": pair[1],
            "relationType": relation_type,
            "proposerType": PROPOSER_AI,
            "evidence": {
                "source": "agent_chat",
                "reason": (reason or reflected_reason or "")[:MAX_REASON_CHARS],
            },
        }


def build_proposal_tools(
    client: InternalClient, llm: BaseChatModel, settings: Settings
) -> list[BaseTool]:
    """提案工具绑定 app 级 client 与自检模型；按用户限流在 ProposalService 内部。"""
    service = ProposalService(client, llm, settings)

    @tool(TAG_PROPOSAL_TOOL_NAME)
    async def propose_tag_tool(
        name: Annotated[str, Field(description="要申请收录的技术名，保留原文大小写，如 Redis、Spring Boot")],
        category: Annotated[str | None, Field(description="分组，可留空，如 数据库/中间件/前端")] = None,
        description: Annotated[str | None, Field(description="一句话说明它是什么，可留空")] = None,
        reason: Annotated[str | None, Field(description="为什么值得收录，一句话")] = None,
    ) -> dict[str, Any]:
        """向管理员申请新增一个技术标签。只有图谱里确实没有、但用户提到的技术才调用；
        提交后是待审状态，不要告诉用户“已收录”。同一轮对话不要反复申请同一个词。"""
        return await service.propose_tag(
            name=name, category=category, description=description, reason=reason
        )

    @tool(RELATION_PROPOSAL_TOOL_NAME)
    async def propose_relation_tool(
        from_technology: Annotated[str, Field(description="关系起点技术名，必须已收录，如 Spring Boot")],
        to_technology: Annotated[str, Field(description="关系终点技术名，必须已收录，如 MyBatis")],
        relation_type: Annotated[
            str, Field(description="关系类型：PREREQUISITE / BASED_ON / USES / SIMILAR_TO / BELONGS_TO")
        ],
        reason: Annotated[str | None, Field(description="为什么这条关系成立，一句话")] = None,
    ) -> dict[str, Any]:
        """向管理员申请新增一条技术关系（from -> to）。两端都必须已收录；
        提交后是待审状态，不要告诉用户“已入库”。"""
        return await service.propose_relation(
            from_technology=from_technology,
            to_technology=to_technology,
            relation_type=relation_type,
            reason=reason,
        )

    return [propose_tag_tool, propose_relation_tool]
