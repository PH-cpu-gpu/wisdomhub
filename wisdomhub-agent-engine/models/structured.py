from typing import TypeVar

from langchain_core.language_models import BaseChatModel
from langchain_core.messages import BaseMessage
from pydantic import BaseModel

SchemaT = TypeVar("SchemaT", bound=BaseModel)


async def invoke_structured(
    llm: BaseChatModel,
    schema: type[SchemaT],
    messages: list[BaseMessage] | str,
) -> SchemaT:
    """按 Pydantic 模型拿结构化输出；解析/校验失败会直接抛异常。"""
    return await llm.with_structured_output(schema).ainvoke(messages)
