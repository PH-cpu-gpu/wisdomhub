"""事件映射：顺序、过滤、去重、工具调用污染与端到端。"""

from collections.abc import AsyncIterator, Mapping, Sequence
from typing import Any

from langchain_core.messages import AIMessageChunk, HumanMessage

from app.capabilities.knowledge.tools import KNOWLEDGE_SERVICE, KnowledgeTools
from app.chat.graph.build import build_graph
from app.chat.graph.node import LEARNING_FETCH, QA_AGENT, TEXT_QA_AGENT
from app.chat.stream import map_events
from app.contracts.artifacts import PlanStage, RouteData, TechRef
from app.contracts.chat_events import RouteEvent
from app.contracts.sse import DeltaEvent, MetaEvent, SseEvent, Step, StepsEvent
from app.contracts.workflow import WorkflowKind
from app.infra.http.internal_client import InternalNotFound
from tests.fakes import FakeInternalClient, FakeLLM

PATH = "/internal/knowledge/prerequisites"


async def _stream(parts: Sequence[Mapping[str, Any]]) -> AsyncIterator[Mapping[str, Any]]:
    for part in parts:
        yield part


def _values(**state: Any) -> dict[str, Any]:
    return {"type": "values", "ns": (), "data": state, "interrupts": ()}


def _message(message: Any, node: str) -> dict[str, Any]:
    return {"type": "messages", "ns": (), "data": (message, {"langgraph_node": node})}


def _route() -> RouteData:
    return RouteData(
        target=TechRef(tag_id="3001", name="Redis"),
        known=["Java"],
        truncated=False,
        stages=[
            PlanStage(depth=1, tags=[TechRef(tag_id="3002", name="数据结构")]),
            PlanStage(depth=0, tags=[TechRef(tag_id="3001", name="Redis")]),
        ],
    )


def _step(**overrides: Any) -> Step:
    payload: dict[str, Any] = {
        "id": "s1",
        "tool": "query_prerequisite_chain",
        "label": "查询前置知识链",
        "status": "done",
        "summary": "返回 2 层前置",
    }
    payload.update(overrides)
    return Step.model_validate(payload)


async def _collect(
    parts: Sequence[Mapping[str, Any]],
    *,
    workflow: WorkflowKind = WorkflowKind.LEARNING_PATH,
) -> list[SseEvent]:
    return [
        event
        async for event in map_events(
            _stream(parts),
            conversation_id="c-1",
            message_id="m-1",
            workflow=workflow,
        )
    ]


def _payload() -> dict[str, Any]:
    return {
        "tagId": "3001",
        "name": "Redis",
        "depth": 0,
        "levels": [
            {"depth": 1, "tags": [{"tagId": "3002", "name": "数据结构"}]},
            {"depth": 2, "tags": [{"tagId": "3003", "name": "内存管理"}]},
        ],
        "truncated": False,
    }


async def test_event_order_route_steps_then_deltas() -> None:
    route = _route()
    step = _step()
    parts = [
        _values(messages=[]),
        _values(route=route, steps=[step]),
        _values(route=route, steps=[step]),
        _message(AIMessageChunk(content="先学", id="resp-1"), "learning_agent"),
        _message(AIMessageChunk(content="数据结构", id="resp-1"), "learning_agent"),
    ]

    events = await _collect(parts)

    assert [type(event) for event in events] == [
        MetaEvent,
        RouteEvent,
        StepsEvent,
        DeltaEvent,
        DeltaEvent,
    ]
    assert isinstance(events[0], MetaEvent)
    assert events[0].workflow is WorkflowKind.LEARNING_PATH
    assert isinstance(events[2], StepsEvent)
    assert events[2].steps == [step]
    deltas = [event for event in events if isinstance(event, DeltaEvent)]
    assert [event.text for event in deltas] == ["先学", "数据结构"]


async def test_meta_is_sent_for_empty_stream() -> None:
    events = await _collect([], workflow=WorkflowKind.QA)

    assert len(events) == 1
    assert isinstance(events[0], MetaEvent)
    assert events[0].conversation_id == "c-1"
    assert events[0].message_id == "m-1"


async def test_steps_only_emitted_when_snapshot_changes() -> None:
    running = _step(status="running", summary=None)
    done = _step()
    parts = [
        _values(steps=[running]),
        _values(steps=[running]),
        _values(steps=[done]),
        _values(steps=[]),
    ]

    events = await _collect(parts)
    steps_events = [event for event in events if isinstance(event, StepsEvent)]

    assert len(steps_events) == 2
    assert steps_events[0].steps[0].status == "running"
    assert steps_events[1].steps[0].status == "done"


async def test_tokens_filtered_by_node() -> None:
    parts = [
        _message(AIMessageChunk(content="router"), "router"),
        _message(AIMessageChunk(content="fetch"), LEARNING_FETCH),
        _message(AIMessageChunk(content="qa"), QA_AGENT),
        _message(AIMessageChunk(content="text"), TEXT_QA_AGENT),
    ]

    events = await _collect(parts, workflow=WorkflowKind.QA)

    assert [event.text for event in events if isinstance(event, DeltaEvent)] == ["qa", "text"]


async def test_empty_text_and_empty_state_send_nothing() -> None:
    parts = [
        _message(AIMessageChunk(content="", id="resp-1"), QA_AGENT),
        _values(steps=[], messages=[]),
    ]

    events = await _collect(parts, workflow=WorkflowKind.QA)

    assert [type(event) for event in events] == [MetaEvent]


async def test_tool_call_chunks_poison_response_id() -> None:
    parts = [
        _message(AIMessageChunk(content="正常", id="resp-1"), QA_AGENT),
        _message(
            AIMessageChunk(
                content="} </tool_call>",
                id="resp-1",
                tool_call_chunks=[{"name": "query", "args": "", "id": "call-1", "index": 0}],
            ),
            QA_AGENT,
        ),
        _message(AIMessageChunk(content="后文", id="resp-1"), QA_AGENT),
        _message(AIMessageChunk(content="另一个回答", id="resp-2"), QA_AGENT),
    ]

    events = await _collect(parts, workflow=WorkflowKind.QA)

    assert [event.text for event in events if isinstance(event, DeltaEvent)] == [
        "正常",
        "另一个回答",
    ]


async def test_qa_end_to_end_streams_single_delta() -> None:
    llm = FakeLLM(["你好世界"])
    graph = build_graph(llm, None)
    stream = graph.astream(
        {"messages": [HumanMessage("你好")], "workflow": WorkflowKind.QA},
        stream_mode=["messages", "values"],
        version="v2",
    )

    events = [
        event
        async for event in map_events(
            stream,
            conversation_id="c-1",
            message_id="m-1",
            workflow=WorkflowKind.QA,
        )
    ]

    assert [type(event) for event in events] == [MetaEvent, DeltaEvent]
    assert isinstance(events[1], DeltaEvent)
    assert events[1].text == "你好世界"
    assert len(llm.stream_calls) == 1


async def test_learning_end_to_end_sends_route_before_delta() -> None:
    internal = FakeInternalClient({(KNOWLEDGE_SERVICE, PATH): _payload()})
    llm = FakeLLM(["先学数据结构。"])
    graph = build_graph(llm, KnowledgeTools(internal))
    stream = graph.astream(
        {
            "messages": [HumanMessage("我要学 Redis")],
            "workflow": WorkflowKind.LEARNING_PATH,
            "technology": "Redis",
            "known": ["Java"],
        },
        stream_mode=["messages", "values"],
        version="v2",
    )

    events = [
        event
        async for event in map_events(
            stream,
            conversation_id="c-1",
            message_id="m-1",
            workflow=WorkflowKind.LEARNING_PATH,
        )
    ]

    assert [type(event) for event in events] == [
        MetaEvent,
        RouteEvent,
        StepsEvent,
        DeltaEvent,
    ]
    assert isinstance(events[1], RouteEvent)
    assert isinstance(events[2], StepsEvent)
    assert isinstance(events[3], DeltaEvent)
    assert events[1].target.tag_id == "3001"
    assert events[2].steps[0].status == "done"
    assert events[3].text == "先学数据结构。"


async def test_learning_failure_end_to_end_sends_fallback_delta() -> None:
    tools = KnowledgeTools(
        FakeInternalClient(error=InternalNotFound("不存在", service=KNOWLEDGE_SERVICE, path=PATH))
    )
    llm = FakeLLM(["不应出现"])
    graph = build_graph(llm, tools)
    stream = graph.astream(
        {
            "messages": [HumanMessage("我要学 Redis")],
            "workflow": WorkflowKind.LEARNING_PATH,
            "technology": "Redis",
        },
        stream_mode=["messages", "values"],
        version="v2",
    )

    events = [
        event
        async for event in map_events(
            stream,
            conversation_id="c-1",
            message_id="m-1",
            workflow=WorkflowKind.LEARNING_PATH,
        )
    ]

    assert [type(event) for event in events] == [MetaEvent, StepsEvent, DeltaEvent]
    assert isinstance(events[1], StepsEvent)
    assert isinstance(events[2], DeltaEvent)
    assert events[1].steps[0].status == "error"
    assert events[2].text == "知识图谱中没有找到技术「Redis」"
    assert llm.stream_calls == []
