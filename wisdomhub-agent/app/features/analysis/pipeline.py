"""文章分析流水线：快照校验、正文截断、结构化输出与实体校验。"""

import logging

from pydantic import BaseModel, Field

from app.capabilities.content.client import ArticleContentClient, ArticleContentMissing
from app.features.analysis.prompt import build_messages
from app.runtime.structured import StructuredLLM, invoke_structured
from app.runtime.tokenizer import truncate_text

log = logging.getLogger(__name__)

MAX_ENTITIES = 20
MAX_KNOWLEDGE_POINTS = 10


class ArticleAnalysisDraft(BaseModel):
    topic: str = Field(default="", description="一句话主题")
    entities: list[str] = Field(
        default_factory=list, description="文章中出现的具体技术名，最多 20 个"
    )
    knowledge_points: list[str] = Field(
        default_factory=list, description="文章讲清楚的知识点，每条一句话，最多 10 条"
    )


class ArticleAnalysis(BaseModel):
    article_id: str
    topic: str
    entities: list[str]
    knowledge_points: list[str]


async def run_pipeline(
    client: ArticleContentClient,
    llm: StructuredLLM,
    article_id: str,
    *,
    max_article_tokens: int,
    structured_retries: int,
) -> ArticleAnalysis:
    """取快照 → 校验正文 → 截断 → 结构化提炼 → 归一与校验。

    快照类异常（未发布、正文为空、不存在）直接上抛；pipeline 不做跨步骤重试。
    """
    snapshot = await client.get_snapshot(article_id)
    markdown = snapshot.markdown or ""
    if not markdown.strip():
        raise ArticleContentMissing(article_id)

    content = truncate_text(markdown, max_article_tokens)
    if not content.strip():
        raise ArticleContentMissing(article_id)

    title = _normalize(snapshot.title)
    summary = _normalize(snapshot.summary)
    tags = [name for tag in snapshot.tags if (name := _normalize(tag.name))]
    messages = build_messages(title=title, summary=summary, tags=tags, content=content)
    draft = await invoke_structured(
        llm,
        ArticleAnalysisDraft,
        messages,
        max_retries=structured_retries,
    )

    haystack = "\n".join((title, summary, content)).casefold()
    return ArticleAnalysis(
        article_id=article_id,
        topic=_normalize(draft.topic) or title,
        entities=_validate_entities(draft.entities, haystack),
        knowledge_points=_clean_points(draft.knowledge_points),
    )


def _normalize(text: str | None) -> str:
    return " ".join((text or "").split())


def _validate_entities(entities: list[str], haystack: str) -> list[str]:
    """保留原文出现过的实体：归一、大小写去重、上限 MAX_ENTITIES。"""
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
    """归一、大小写去重、上限 MAX_KNOWLEDGE_POINTS。"""
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
