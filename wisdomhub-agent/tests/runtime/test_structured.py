"""结构化输出：with_structured_output 与解析失败重试。"""

from collections.abc import Sequence
from typing import Any

import pytest
from langchain_core.exceptions import OutputParserException
from langchain_core.messages import BaseMessage, HumanMessage
from pydantic import BaseModel, ValidationError

from app.runtime.structured import Method, StructuredOutputError, invoke_structured


class Article(BaseModel):
    topic: str
    entities: list[str]


ARTICLE = Article(topic="Spring", entities=["Spring"])


class _FakeLLM:
    def __init__(self, results: Sequence[Article | Exception | None]) -> None:
        self.results = list(results)
        self.calls: list[Sequence[BaseMessage]] = []
        self.schema: type[BaseModel] | None = None
        self.method: Method | None = None

    def with_structured_output(
        self, schema: type[BaseModel], *, method: Method = "function_calling"
    ) -> "_FakeLLM":
        self.schema = schema
        self.method = method
        return self

    async def ainvoke(self, input: Sequence[BaseMessage], config: Any = None, **kwargs: Any) -> Any:
        self.calls.append(input)
        result = self.results[min(len(self.calls) - 1, len(self.results) - 1)]
        if isinstance(result, Exception):
            raise result
        return result


def _messages() -> list[BaseMessage]:
    return [HumanMessage(content="分析")]


def _validation_error() -> ValidationError:
    try:
        Article.model_validate({"topic": 1})
    except ValidationError as exc:
        return exc
    raise AssertionError("预期 ValidationError")


async def test_returns_pydantic_model() -> None:
    llm = _FakeLLM([ARTICLE])

    result = await invoke_structured(llm, Article, _messages(), max_retries=1)

    assert result is ARTICLE
    assert llm.schema is Article
    assert llm.method == "function_calling"
    assert len(llm.calls) == 1


async def test_retries_after_parse_error() -> None:
    llm = _FakeLLM([OutputParserException("bad json"), ARTICLE])

    result = await invoke_structured(llm, Article, _messages(), max_retries=1)

    assert result is ARTICLE
    assert len(llm.calls) == 2


async def test_retries_after_validation_error() -> None:
    llm = _FakeLLM([_validation_error(), ARTICLE])

    result = await invoke_structured(llm, Article, _messages(), max_retries=1)

    assert result is ARTICLE
    assert len(llm.calls) == 2


async def test_retries_when_model_returns_nothing() -> None:
    llm = _FakeLLM([None, ARTICLE])

    result = await invoke_structured(llm, Article, _messages(), max_retries=1)

    assert result is ARTICLE
    assert len(llm.calls) == 2


async def test_raises_after_retries_exhausted() -> None:
    error = OutputParserException("bad json")
    llm = _FakeLLM([error])

    with pytest.raises(StructuredOutputError) as excinfo:
        await invoke_structured(llm, Article, _messages(), max_retries=1)

    assert len(llm.calls) == 2
    assert excinfo.value.__cause__ is error
    assert "Article" in str(excinfo.value)


async def test_zero_retries_calls_once() -> None:
    llm = _FakeLLM([OutputParserException("bad json")])

    with pytest.raises(StructuredOutputError):
        await invoke_structured(llm, Article, _messages(), max_retries=0)

    assert len(llm.calls) == 1


async def test_raises_when_nothing_returned() -> None:
    llm = _FakeLLM([None])

    with pytest.raises(StructuredOutputError) as excinfo:
        await invoke_structured(llm, Article, _messages(), max_retries=0)

    assert "模型没有返回结构化结果" in str(excinfo.value)


async def test_transport_error_is_not_retried() -> None:
    llm = _FakeLLM([RuntimeError("timeout")])

    with pytest.raises(RuntimeError, match="timeout"):
        await invoke_structured(llm, Article, _messages(), max_retries=2)

    assert len(llm.calls) == 1
