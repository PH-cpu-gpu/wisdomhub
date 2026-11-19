"""文章分析流水线：快照校验、截断、结构化输出与实体校验。"""

from collections.abc import Sequence
from typing import Any

import pytest
from langchain_core.exceptions import OutputParserException
from langchain_core.messages import BaseMessage, HumanMessage, SystemMessage
from pydantic import BaseModel

from app.capabilities.content.client import (
    CONTENT_SERVICE,
    ArticleContentClient,
    ArticleContentMissing,
    ArticleLocked,
    ArticleNotFound,
    ArticleNotPublished,
)
from app.features.analysis.pipeline import (
    MAX_ENTITIES,
    MAX_KNOWLEDGE_POINTS,
    ArticleAnalysis,
    ArticleAnalysisDraft,
    run_pipeline,
)
from app.features.analysis.prompt import ANALYSIS_SYSTEM_PROMPT
from app.infra.http.internal_client import InternalNotFound
from app.runtime.structured import StructuredOutputError
from app.runtime.tokenizer import count_tokens
from tests.fakes import FakeInternalClient

PATH = "/internal/content/article/1001"
BUDGET = 2000
DEFAULT_MARKDOWN = (
    "# Spring Bean 生命周期\n\n"
    "Spring 的 Bean 生命周期包括实例化和初始化。Bean 由容器管理，销毁前执行清理。"
)
DEFAULT_DRAFT = ArticleAnalysisDraft(
    topic="Spring Bean 生命周期",
    entities=["Spring", "Bean"],
    knowledge_points=["实例化与初始化", "容器负责销毁"],
)


class _FakeStructuredLLM:
    def __init__(self, drafts: Sequence[ArticleAnalysisDraft | Exception | None]) -> None:
        self.drafts = list(drafts)
        self.calls: list[list[BaseMessage]] = []
        self.schemas: list[type[BaseModel]] = []

    def with_structured_output(
        self, schema: type[BaseModel], *, method: str = "function_calling"
    ) -> "_FakeStructuredLLM":
        self.schemas.append(schema)
        return self

    async def ainvoke(
        self, input: Sequence[BaseMessage], config: Any = None, **kwargs: Any
    ) -> ArticleAnalysisDraft | None:
        self.calls.append(list(input))
        value = self.drafts[min(len(self.calls) - 1, len(self.drafts) - 1)]
        if isinstance(value, Exception):
            raise value
        return value


def _payload(**overrides: Any) -> dict[str, Any]:
    data: dict[str, Any] = {
        "articleId": "1001",
        "authorId": "2001",
        "title": "Spring Bean 生命周期",
        "summary": "Spring 容器管理 Bean 的完整过程",
        "state": 2,
        "createTime": "2026-11-15T08:00:00Z",
        "publishTime": "2026-11-15T09:00:00Z",
        "updateTime": "2026-11-15T10:00:00Z",
        "viewCount": 12,
        "tags": [{"tagId": "3001", "name": "Spring", "category": "FRAMEWORK"}],
        "markdown": DEFAULT_MARKDOWN,
        "locked": False,
    }
    data.update(overrides)
    return data


def _client(**overrides: Any) -> ArticleContentClient:
    return ArticleContentClient(
        FakeInternalClient({(CONTENT_SERVICE, PATH): _payload(**overrides)})
    )


async def _run(
    llm: _FakeStructuredLLM,
    *,
    client: ArticleContentClient | None = None,
    max_article_tokens: int = BUDGET,
    structured_retries: int = 0,
) -> ArticleAnalysis:
    return await run_pipeline(
        client or _client(),
        llm,
        "1001",
        max_article_tokens=max_article_tokens,
        structured_retries=structured_retries,
    )


def _human_prompt(llm: _FakeStructuredLLM) -> str:
    messages = llm.calls[0]
    assert isinstance(messages[1], HumanMessage)
    content = messages[1].content
    assert isinstance(content, str)
    return content


def _body(prompt: str) -> str:
    head, _, body = prompt.partition("正文：\n")
    assert head, "prompt 缺少正文标记"
    return body


async def test_returns_analysis_with_prompt_and_schema() -> None:
    llm = _FakeStructuredLLM(
        [
            ArticleAnalysisDraft(
                topic=" Spring Bean  生命周期 ",
                entities=["Spring", "Bean"],
                knowledge_points=[" 实例化与初始化 ", "容器负责销毁"],
            )
        ]
    )

    result = await _run(llm)

    assert llm.schemas == [ArticleAnalysisDraft]
    assert len(llm.calls) == 1
    assert isinstance(llm.calls[0][0], SystemMessage)
    assert llm.calls[0][0].content == ANALYSIS_SYSTEM_PROMPT
    prompt = _human_prompt(llm)
    assert "标题：Spring Bean 生命周期" in prompt
    assert "摘要：Spring 容器管理 Bean 的完整过程" in prompt
    assert "标签：Spring" in prompt
    assert "正文：\n# Spring Bean 生命周期" in prompt

    assert result.article_id == "1001"
    assert result.topic == "Spring Bean 生命周期"
    assert result.entities == ["Spring", "Bean"]
    assert result.knowledge_points == ["实例化与初始化", "容器负责销毁"]


async def test_truncates_article_before_prompt() -> None:
    llm = _FakeStructuredLLM([DEFAULT_DRAFT])
    markdown = "# 正文\n" + "Spring 启动流程。" * 4000

    await _run(llm, client=_client(markdown=markdown), max_article_tokens=40)

    body = _body(_human_prompt(llm))
    assert "已截断" in body
    assert count_tokens(body) <= 40


async def test_topic_falls_back_to_title_when_blank() -> None:
    llm = _FakeStructuredLLM([ArticleAnalysisDraft(topic="   ", entities=[], knowledge_points=[])])

    result = await _run(llm)

    assert result.topic == "Spring Bean 生命周期"


async def test_entity_filter_normalizes_dedupes_and_drops_missing() -> None:
    llm = _FakeStructuredLLM(
        [
            ArticleAnalysisDraft(
                topic="Spring",
                entities=["  Spring ", "spring", "Kafka", "", "Bean"],
                knowledge_points=[],
            )
        ]
    )

    result = await _run(llm)

    assert result.entities == ["Spring", "Bean"]


async def test_entity_in_title_is_kept() -> None:
    llm = _FakeStructuredLLM(
        [ArticleAnalysisDraft(topic="Redis", entities=["Redis"], knowledge_points=[])]
    )

    result = await _run(
        llm,
        client=_client(
            title="Redis 持久化",
            summary="摘要",
            markdown="正文只讨论持久化策略，没有点名具体技术。",
        ),
    )

    assert result.entities == ["Redis"]


async def test_entity_only_in_truncated_tail_is_dropped() -> None:
    llm = _FakeStructuredLLM(
        [ArticleAnalysisDraft(topic="Spring", entities=["Spring", "Kafka"], knowledge_points=[])]
    )
    markdown = "Spring 启动流程。" + "填充内容。" * 2000 + " Kafka"

    result = await _run(llm, client=_client(markdown=markdown), max_article_tokens=20)

    assert result.entities == ["Spring"]


async def test_entity_limit() -> None:
    names = [f"Tech{i}" for i in range(MAX_ENTITIES + 5)]
    llm = _FakeStructuredLLM(
        [ArticleAnalysisDraft(topic="技术", entities=names, knowledge_points=[])]
    )
    markdown = "文章正文：" + " ".join(names) + "。"

    result = await _run(llm, client=_client(markdown=markdown))

    assert result.entities == names[:MAX_ENTITIES]


async def test_knowledge_points_dedupe_and_limit() -> None:
    points = [f"知识点{i}" for i in range(MAX_KNOWLEDGE_POINTS + 2)]
    llm = _FakeStructuredLLM(
        [
            ArticleAnalysisDraft(
                topic="主题",
                entities=[],
                knowledge_points=[*points, "  知识点0 ", ""],
            )
        ]
    )

    result = await _run(llm)

    assert len(result.knowledge_points) == MAX_KNOWLEDGE_POINTS
    assert len(set(result.knowledge_points)) == MAX_KNOWLEDGE_POINTS
    assert result.knowledge_points[0] == "知识点0"


async def test_structured_output_retries_on_parse_error() -> None:
    llm = _FakeStructuredLLM([OutputParserException("bad json"), DEFAULT_DRAFT])

    result = await _run(llm, structured_retries=1)

    assert result.topic == DEFAULT_DRAFT.topic
    assert len(llm.calls) == 2


async def test_structured_error_propagates() -> None:
    llm = _FakeStructuredLLM([OutputParserException("bad json")])

    with pytest.raises(StructuredOutputError):
        await _run(llm, structured_retries=0)

    assert len(llm.calls) == 1


async def test_non_published_state_raises() -> None:
    llm = _FakeStructuredLLM([DEFAULT_DRAFT])

    with pytest.raises(ArticleNotPublished) as excinfo:
        await _run(llm, client=_client(state=1))

    assert excinfo.value.state == 1
    assert llm.calls == []


@pytest.mark.parametrize("markdown", ["", "   ", None])
async def test_empty_markdown_raises(markdown: str | None) -> None:
    llm = _FakeStructuredLLM([DEFAULT_DRAFT])

    with pytest.raises(ArticleContentMissing):
        await _run(llm, client=_client(markdown=markdown))

    assert llm.calls == []


async def test_locked_article_skips_llm() -> None:
    llm = _FakeStructuredLLM([DEFAULT_DRAFT])

    with pytest.raises(ArticleLocked):
        await _run(llm, client=_client(locked=True))

    assert llm.calls == []


async def test_not_found_raises() -> None:
    llm = _FakeStructuredLLM([DEFAULT_DRAFT])
    error = InternalNotFound(
        "文章不存在",
        service=CONTENT_SERVICE,
        path=PATH,
        status=404,
        code=404,
    )
    client = ArticleContentClient(FakeInternalClient(error=error))

    with pytest.raises(ArticleNotFound):
        await _run(llm, client=client)

    assert llm.calls == []
