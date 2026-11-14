"""测试替身的基本行为。"""

import pytest
from langchain_core.messages import HumanMessage

from tests.fakes import FakeInternalClient, FakeLLM, FakePublisher


async def test_fake_llm_ainvoke_records_calls() -> None:
    llm = FakeLLM(["hello", "world"])

    first = await llm.ainvoke([HumanMessage(content="hi")])
    second = await llm.ainvoke([HumanMessage(content="again")])

    assert first.content == "hello"
    assert second.content == "world"
    assert [len(call) for call in llm.calls] == [1, 1]


async def test_fake_llm_astream_and_bind_tools() -> None:
    llm = FakeLLM(["abcdef"], chunk_size=2)
    tools = [object()]

    assert llm.bind_tools(tools) is llm
    chunks = [chunk.content async for chunk in llm.astream([HumanMessage(content="hi")])]

    assert "".join(str(part) for part in chunks) == "abcdef"
    assert llm.bound_tools == tools
    assert len(llm.stream_calls) == 1


async def test_fake_internal_client_success_and_error() -> None:
    client = FakeInternalClient({"/internal/search/recall": {"items": []}})

    assert await client.get("/internal/search/recall", params={"q": "x"}) == {"items": []}
    assert client.calls[0].params == {"q": "x"}

    with pytest.raises(KeyError):
        await client.get("/missing")

    failing = FakeInternalClient(error=RuntimeError("down"))
    with pytest.raises(RuntimeError, match="down"):
        await failing.post("/any", json={})

    await client.aclose()
    assert client.closed


async def test_fake_publisher_records_and_can_fail() -> None:
    publisher = FakePublisher()
    await publisher.publish("ai.completed", {"id": 1})

    assert publisher.messages[0].routing_key == "ai.completed"
    assert publisher.messages[0].payload == {"id": 1}

    failing = FakePublisher(error=RuntimeError("mq down"))
    with pytest.raises(RuntimeError, match="mq down"):
        await failing.publish("x", {})

    await publisher.aclose()
    assert publisher.closed
