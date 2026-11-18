"""文章事件消费：解析、状态过滤与 ACK/DLQ 映射。"""

import json
from datetime import UTC, datetime
from typing import Any

import pytest

from app.features.analysis.consumer import (
    ArticleAnalysisHandler,
    build_analyze_consumer,
)
from app.features.analysis.job import JobResult
from app.infra.mq.consumer import Consumer, ConsumeResult
from app.infra.mq.topology import ANALYZE_QUEUE, ARTICLE_CREATED, ARTICLE_UPDATED
from tests.infra.mq.fakes import FakeMessage

ARTICLE = "2108541493908459521"
VERSION = datetime(2026, 11, 17, 8, tzinfo=UTC)


class _Job:
    def __init__(
        self,
        result: JobResult = JobResult.ACK,
        *,
        error: Exception | None = None,
    ) -> None:
        self.result = result
        self.error = error
        self.calls: list[tuple[str, datetime, str | None]] = []

    async def run(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        author_id: str | None = None,
    ) -> JobResult:
        self.calls.append((article_id, source_updated_at, author_id))
        if self.error is not None:
            raise self.error
        return self.result


def _body(
    *,
    event_type: str = ARTICLE_CREATED,
    **payload_overrides: Any,
) -> bytes:
    payload: dict[str, Any] = {
        "articleId": ARTICLE,
        "authorId": "2001",
        "title": "Redis 持久化",
        "summary": "摘要",
        "state": 2,
        "publishTime": "2026-11-17T07:00:00Z",
        "updateTime": "2026-11-17T08:00:00Z",
    }
    payload.update(payload_overrides)
    event = {
        "eventId": "6f1c8b2e-0000-0000-0000-000000000000",
        "eventType": event_type,
        "occurredAt": "2026-11-17T08:00:01Z",
        "payload": payload,
    }
    return json.dumps(event, ensure_ascii=False).encode("utf-8")


async def _handle(
    body: bytes,
    *,
    job: _Job | None = None,
) -> tuple[ConsumeResult, _Job]:
    runner = job or _Job()
    handler = ArticleAnalysisHandler(runner)
    result = await handler(FakeMessage(body))
    return result, runner


async def test_created_event_runs_job() -> None:
    result, job = await _handle(_body())

    assert result is ConsumeResult.ACK
    assert job.calls == [(ARTICLE, VERSION, "2001")]


async def test_updated_event_runs_job() -> None:
    result, job = await _handle(_body(event_type=ARTICLE_UPDATED))

    assert result is ConsumeResult.ACK
    assert job.calls == [(ARTICLE, VERSION, "2001")]


async def test_unpublished_article_skips_job() -> None:
    result, job = await _handle(_body(state=1))

    assert result is ConsumeResult.ACK
    assert job.calls == []


async def test_offline_article_skips_job() -> None:
    result, job = await _handle(_body(state=3))

    assert result is ConsumeResult.ACK
    assert job.calls == []


async def test_numeric_article_id_is_normalized() -> None:
    result, job = await _handle(_body(articleId=12345))

    assert result is ConsumeResult.ACK
    assert job.calls == [("12345", VERSION, "2001")]


async def test_numeric_author_id_is_normalized() -> None:
    result, job = await _handle(_body(authorId=2001))

    assert result is ConsumeResult.ACK
    assert job.calls == [(ARTICLE, VERSION, "2001")]


@pytest.mark.parametrize("overrides", [{"authorId": None}, {"authorId": "abc"}])
async def test_missing_author_still_analyzes(overrides: dict[str, Any]) -> None:
    result, job = await _handle(_body(**overrides))

    assert result is ConsumeResult.ACK
    assert job.calls == [(ARTICLE, VERSION, None)]


async def test_naive_update_time_is_treated_as_utc() -> None:
    result, job = await _handle(_body(updateTime="2026-11-17T08:00:00.123"))

    assert result is ConsumeResult.ACK
    assert job.calls == [(ARTICLE, datetime(2026, 11, 17, 8, 0, 0, 123000, tzinfo=UTC), "2001")]


@pytest.mark.parametrize(
    "body",
    [
        b"not json",
        b"[]",
        json.dumps({"eventType": ARTICLE_CREATED}).encode(),
        json.dumps({"eventType": "article.deleted", "payload": {}}).encode(),
    ],
)
async def test_malformed_message_goes_to_dlq(body: bytes) -> None:
    result, job = await _handle(body)

    assert result is ConsumeResult.DLQ
    assert job.calls == []


@pytest.mark.parametrize(
    "overrides",
    [
        {"articleId": None},
        {"articleId": "abc"},
        {"articleId": 0},
        {"articleId": True},
        {"updateTime": None},
        {"updateTime": "yesterday"},
        {"state": "2"},
        {"state": None},
        {"state": True},
    ],
)
async def test_invalid_payload_goes_to_dlq(overrides: dict[str, Any]) -> None:
    result, job = await _handle(_body(**overrides))

    assert result is ConsumeResult.DLQ
    assert job.calls == []


async def test_job_failure_maps_to_dlq() -> None:
    result, job = await _handle(_body(), job=_Job(JobResult.DLQ))

    assert result is ConsumeResult.DLQ
    assert len(job.calls) == 1


async def test_job_exception_propagates_to_consumer() -> None:
    handler = ArticleAnalysisHandler(_Job(error=RuntimeError("boom")))

    with pytest.raises(RuntimeError, match="boom"):
        await handler(FakeMessage(_body()))


def test_build_consumer_targets_analyze_queue() -> None:
    consumer = build_analyze_consumer(
        "amqp://guest:guest@127.0.0.1/", _Job(), reconnect_interval=5.0
    )

    assert isinstance(consumer, Consumer)
    assert consumer._queue_name == ANALYZE_QUEUE
    assert consumer._prefetch_count == 1
