"""QA 与文本问答节点：流式拼接、无工具、文本隔离与错误传播。"""

from collections.abc import AsyncIterator, Sequence
from typing import Any

import pytest
from langchain_core.messages import (
    AIMessage,
    AIMessageChunk,
    AnyMessage,
    BaseMessage,
    BaseMessageChunk,
    HumanMessage,
    SystemMessage,
)

from app.chat.graph.node import make_qa_agent, make_text_qa_agent
from app.chat.graph.state import ChatState
from app.chat.prompt import QA_SYSTEM_PROMPT, TEXT_QA_SYSTEM_PROMPT
from app.contracts.workflow import WorkflowKind
from tests.fakes import FakeLLM


class _BrokenLLM:
    async def astream(
        self, messages: Sequence[BaseMessage], **kwargs: Any
    ) -> AsyncIterator[BaseMessageChunk]:
        raise RuntimeError("boom")
        yield AIMessageChunk(content="")


class _UsageLLM:
    async def astream(
        self, messages: Sequence[BaseMessage], **kwargs: Any
    ) -> AsyncIterator[BaseMessageChunk]:
        yield AIMessageChunk(content="答")
        yield AIMessageChunk(
            content="",
            usage_metadata={"input_tokens": 3, "output_tokens": 5, "total_tokens": 8},
        )


class _BlockLLM:
    async def astream(
        self, messages: Sequence[BaseMessage], **kwargs: Any
    ) -> AsyncIterator[BaseMessageChunk]:
        yield AIMessageChunk(
            content=[
                {"type": "text", "text": "第一段"},
                {"type": "image", "url": "https://example.com/a.png"},
                "第二段",
            ]
        )


async def test_qa_agent_streams_full_answer_without_tools() -> None:
    llm = FakeLLM(["Redis 支持 RDB 和 AOF。"], chunk_size=3)
    messages: list[AnyMessage] = [
        SystemMessage(QA_SYSTEM_PROMPT),
        HumanMessage("Redis 怎么持久化？"),
    ]
    state: ChatState = {"messages": messages, "workflow": WorkflowKind.QA}

    result = await make_qa_agent(llm)(state)

    answer = result["messages"][0]
    assert isinstance(answer, AIMessage)
    assert answer.content == "Redis 支持 RDB 和 AOF。"
    assert llm.stream_calls == [messages]
    assert llm.calls == []
    assert llm.bound_tools == []


async def test_text_qa_agent_uses_router_messages_only() -> None:
    selected = "Redis 支持 RDB 和 AOF。"
    llm = FakeLLM(["这段文本介绍了两种持久化方式。"])
    messages: list[AnyMessage] = [
        SystemMessage(TEXT_QA_SYSTEM_PROMPT),
        HumanMessage(f"<selected_text>{selected}</selected_text>\n\n问题：这段文本讲了什么？"),
    ]
    state: ChatState = {
        "messages": messages,
        "workflow": WorkflowKind.TEXT_QA,
        "text": selected,
    }

    result = await make_text_qa_agent(llm)(state)

    sent = llm.stream_calls[0]
    assert sent == messages
    humans = [message for message in sent if isinstance(message, HumanMessage)]
    assert len(humans) == 1
    assert humans[0].content.count("<selected_text>") == 1
    assert result["messages"][0].content == "这段文本介绍了两种持久化方式。"
    assert llm.bound_tools == []


async def test_empty_answer_returns_empty_ai_message() -> None:
    llm = FakeLLM([""])

    result = await make_qa_agent(llm)({"messages": [HumanMessage("问题")]})

    answer = result["messages"][0]
    assert isinstance(answer, AIMessage)
    assert answer.content == ""


async def test_llm_error_propagates() -> None:
    with pytest.raises(RuntimeError, match="boom"):
        await make_qa_agent(_BrokenLLM())({"messages": [HumanMessage("问题")]})


async def test_usage_metadata_is_preserved() -> None:
    result = await make_qa_agent(_UsageLLM())({"messages": [HumanMessage("问题")]})

    answer = result["messages"][0]
    assert isinstance(answer, AIMessage)
    assert answer.content == "答"
    assert answer.usage_metadata == {
        "input_tokens": 3,
        "output_tokens": 5,
        "total_tokens": 8,
    }


async def test_non_text_content_blocks_are_skipped() -> None:
    result = await make_qa_agent(_BlockLLM())({"messages": [HumanMessage("问题")]})

    assert result["messages"][0].content == "第一段第二段"
