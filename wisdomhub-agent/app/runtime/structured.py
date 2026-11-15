"""结构化输出：Pydantic schema + with_structured_output，解析失败重试。"""

import logging
from collections.abc import Sequence
from typing import Any, Literal, Protocol

from langchain_core.exceptions import OutputParserException
from langchain_core.messages import BaseMessage
from pydantic import BaseModel, ValidationError

log = logging.getLogger(__name__)

Method = Literal["function_calling", "json_mode", "json_schema"]


class StructuredRunnable(Protocol):
    async def ainvoke(self, input: Any, config: Any = None, **kwargs: Any) -> Any: ...


class StructuredLLM(Protocol):
    def with_structured_output(
        self,
        schema: type[BaseModel],
        *,
        method: Method = "function_calling",
    ) -> StructuredRunnable: ...


class StructuredOutputError(Exception):
    """结构化输出在重试后仍无法解析。"""


async def invoke_structured[T: BaseModel](
    llm: StructuredLLM,
    schema: type[T],
    messages: Sequence[BaseMessage],
    *,
    max_retries: int,
) -> T:
    """让模型按 schema 输出；只在解析失败时重试。"""
    if max_retries < 0:
        raise ValueError("max_retries 不能为负数")

    structured = llm.with_structured_output(schema, method="function_calling")
    last_error: Exception | None = None

    for attempt in range(max_retries + 1):
        try:
            result = await structured.ainvoke(messages)
        except (OutputParserException, ValidationError) as exc:
            last_error = exc
        else:
            if result is not None:
                return result
            last_error = None
        log.warning(
            "结构化输出解析失败 schema=%s attempt=%s: %s",
            schema.__name__,
            attempt + 1,
            last_error,
        )

    detail = str(last_error) if last_error is not None else "模型没有返回结构化结果"
    raise StructuredOutputError(
        f"结构化输出失败（{schema.__name__}，尝试 {max_retries + 1} 次）: {detail}"
    ) from last_error
