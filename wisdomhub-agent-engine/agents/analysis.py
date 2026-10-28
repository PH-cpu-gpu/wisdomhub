import logging
from typing import Any

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import HumanMessage, SystemMessage
from pydantic import BaseModel, Field

from app.config import Settings
from models.structured import invoke_structured
from models.tokenizer import TokenCounter, TiktokenCounter
from prompts.analysis import ANALYSIS_SYSTEM_PROMPT
from tools.internal_client import InternalClient, InternalClientError
from tools.java_api import get_article_snapshot

log = logging.getLogger(__name__)

MAX_ENTITIES = 20
MAX_KNOWLEDGE_POINTS = 10
TRUNCATED_MARKER = "\n……（已截断）"


class ArticleAnalysisError(Exception):
    """文章分析失败；B3.6 按具体类型决定 ACK / 重试。"""


class ArticleNotFound(ArticleAnalysisError):
    pass


class ArticleNotPublished(ArticleAnalysisError):
    pass


class ArticleContentMissing(ArticleAnalysisError):
    pass


class ArticleAnalysisDraft(BaseModel):
    topic: str = Field(default="", description="一句话主题")
    entities: list[str] = Field(default_factory=list, description="文章中出现的技术实体名")
    knowledge_points: list[str] = Field(default_factory=list, description="文章讲清楚的知识点")


class ArticleAnalysis(BaseModel):
    article_id: str
    topic: str
    entities: list[str]
    knowledge_points: list[str]


def _normalize(text: Any) -> str:
    return " ".join(str(text or "").split())


def _truncate(text: str, budget: int, counter: TokenCounter) -> str:
    if budget <= 0:
        return ""
    if counter.count(text) <= budget:
        return text
    marker_tokens = counter.count(TRUNCATED_MARKER)
    if budget <= marker_tokens:
        return ""
    target = budget - marker_tokens
    low, high = 0, len(text)
    while low < high:
        mid = (low + high + 1) // 2
        if counter.count(text[:mid]) <= target:
            low = mid
        else:
            high = mid - 1
    return text[:low].rstrip() + TRUNCATED_MARKER


def _validate_entities(entities: list[str], haystack: str) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for raw in entities:
        name = _normalize(raw)
        if not name:
            continue
        key = name.casefold()
        if key in seen:
            continue
        if key not in haystack:
            log.info("丢弃文章未出现的实体: %s", name)
            continue
        seen.add(key)
        result.append(name)
        if len(result) >= MAX_ENTITIES:
            break
    return result


def _clean_points(points: list[str]) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()
    for raw in points:
        point = _normalize(raw)
        if not point:
            continue
        key = point.casefold()
        if key in seen:
            continue
        seen.add(key)
        result.append(point)
        if len(result) >= MAX_KNOWLEDGE_POINTS:
            break
    return result


def _snapshot_text(snapshot: dict[str, Any]) -> tuple[str, str, str, list[str]]:
    if str(snapshot.get("state")) != "2":
        raise ArticleNotPublished(f"文章不是已发布状态: {snapshot.get('state')}")
    markdown = snapshot.get("markdown")
    if not isinstance(markdown, str) or not markdown.strip():
        raise ArticleContentMissing("文章正文为空")
    title = _normalize(snapshot.get("title"))
    summary = _normalize(snapshot.get("summary"))
    tags = [
        _normalize(tag.get("name"))
        for tag in snapshot.get("tags") or []
        if isinstance(tag, dict) and _normalize(tag.get("name"))
    ]
    return title, summary, markdown, tags


async def analyze_article(
    client: InternalClient,
    llm: BaseChatModel,
    article_id: str,
    *,
    settings: Settings,
    counter: TokenCounter | None = None,
    snapshot: dict[str, Any] | None = None,
) -> ArticleAnalysis:
    """取文章快照 → LLM 提炼主题/技术实体/知识点；不查关联技术或相关文章。"""
    try:
        if snapshot is None:
            snapshot = await get_article_snapshot(client, article_id)
    except InternalClientError as exc:
        if exc.code == 404 or exc.status == 404:
            raise ArticleNotFound(f"文章不存在: {article_id}") from exc
        raise
    except ValueError as exc:
        raise ArticleAnalysisError("文章快照格式异常") from exc

    title, summary, markdown, tags = _snapshot_text(snapshot)
    counter = counter or TiktokenCounter()
    content = _truncate(markdown, settings.analysis_max_article_tokens, counter)

    prompt = (
        f"标题：{title}\n"
        f"摘要：{summary}\n"
        f"标签：{'、'.join(tags) if tags else '无'}\n\n"
        f"正文：\n{content}"
    )
    draft = await invoke_structured(
        llm,
        ArticleAnalysisDraft,
        [SystemMessage(content=ANALYSIS_SYSTEM_PROMPT), HumanMessage(content=prompt)],
    )

    haystack = "\n".join((title, summary, content)).casefold()
    return ArticleAnalysis(
        article_id=str(article_id),
        topic=_normalize(draft.topic) or title,
        entities=_validate_entities(draft.entities, haystack),
        knowledge_points=_clean_points(draft.knowledge_points),
    )
