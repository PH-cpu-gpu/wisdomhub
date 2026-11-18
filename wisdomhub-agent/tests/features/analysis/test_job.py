"""分析 Job：claim 分支、重试退避、发布与失败落库。"""

from collections.abc import AsyncIterator, Mapping, Sequence
from contextlib import asynccontextmanager
from datetime import UTC, datetime
from typing import Any, cast

import pytest
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.capabilities.content.client import ArticleNotPublished
from app.features.analysis import job as job_module
from app.features.analysis.job import RETRY_BASE_SECONDS, AnalysisJob, JobResult
from app.features.analysis.models import AnalysisResult
from app.features.analysis.pipeline import ArticleAnalysis
from app.features.analysis.repository import ClaimOutcome, ClaimResult
from app.infra.http.internal_client import InternalUnavailable
from app.infra.mq.topology import KNOWLEDGE_EXTRACT
from tests.fakes import FakePublisher

ARTICLE = "1001"
VERSION = datetime(2026, 11, 17, 8, tzinfo=UTC)
NOW = datetime(2026, 11, 18, 8, tzinfo=UTC)
LEASE = 1800
BUDGET = 24000
STRUCTURED_RETRIES = 1


class _Session:
    def begin(self) -> Any:
        return _session_scope()


@asynccontextmanager
async def _session_scope() -> AsyncIterator[None]:
    yield None


@asynccontextmanager
async def _session_cm() -> AsyncIterator[_Session]:
    yield _Session()


class _SessionFactory:
    def __call__(self) -> Any:
        return _session_cm()


class _FakeRepo:
    def __init__(self, claim_result: ClaimResult) -> None:
        self.claim_result = claim_result
        self.claims: list[tuple[str, datetime, int, datetime]] = []
        self.finished: list[dict[str, Any]] = []
        self.failed: list[str] = []
        self.marked_published = 0
        self.finish_result = True
        self.mark_result = True
        self.fail_error: Exception | None = None

    async def claim(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        lease_seconds: int,
        now: datetime | None = None,
    ) -> ClaimResult:
        assert now is not None
        self.claims.append((article_id, source_updated_at, lease_seconds, now))
        return self.claim_result

    async def finish(
        self,
        article_id: str,
        source_updated_at: datetime,
        analysis: dict[str, Any],
        *,
        now: datetime | None = None,
    ) -> bool:
        self.finished.append(analysis)
        return self.finish_result

    async def fail(
        self,
        article_id: str,
        source_updated_at: datetime,
        error: str,
        *,
        now: datetime | None = None,
    ) -> bool:
        if self.fail_error is not None:
            raise self.fail_error
        self.failed.append(error)
        return True

    async def mark_published(
        self,
        article_id: str,
        source_updated_at: datetime,
        *,
        now: datetime | None = None,
    ) -> bool:
        self.marked_published += 1
        return self.mark_result


class _Pipeline:
    def __init__(self, results: Sequence[ArticleAnalysis | Exception]) -> None:
        self.results = list(results)
        self.calls: list[dict[str, Any]] = []

    async def __call__(
        self,
        client: Any,
        llm: Any,
        article_id: str,
        *,
        max_article_tokens: int,
        structured_retries: int,
    ) -> ArticleAnalysis:
        self.calls.append(
            {
                "article_id": article_id,
                "max_article_tokens": max_article_tokens,
                "structured_retries": structured_retries,
            }
        )
        value = self.results[min(len(self.calls) - 1, len(self.results) - 1)]
        if isinstance(value, Exception):
            raise value
        return value


class _FlakyPublisher(FakePublisher):
    def __init__(self, failures: int) -> None:
        super().__init__()
        self.failures = failures
        self.attempts = 0

    async def publish(self, routing_key: str, payload: Mapping[str, Any]) -> None:
        self.attempts += 1
        if self.attempts <= self.failures:
            raise InternalUnavailable("事件发布超时", service="mq", path=routing_key)
        await super().publish(routing_key, payload)


def _row(analysis: dict[str, Any] | None = None) -> AnalysisResult:
    return AnalysisResult(article_id=ARTICLE, status="done", analysis=analysis)


def _analysis(entities: Sequence[str] = ("Redis", "AOF")) -> ArticleAnalysis:
    return ArticleAnalysis(
        article_id=ARTICLE,
        topic="Redis 持久化",
        entities=list(entities),
        knowledge_points=["RDB 与 AOF"],
    )


def _unavailable() -> InternalUnavailable:
    return InternalUnavailable("内部服务不可用", service="content-service", path="/internal")


def _make_job(
    monkeypatch: pytest.MonkeyPatch,
    repo: _FakeRepo,
    *,
    retry_times: int = 2,
    publisher: FakePublisher | None = None,
    clock: Any = lambda: NOW,
) -> tuple[AnalysisJob, list[float], FakePublisher]:
    monkeypatch.setattr(job_module, "AnalysisRepository", lambda session: repo)
    sleeps: list[float] = []

    async def sleep(delay: float) -> None:
        sleeps.append(delay)

    bus = publisher or FakePublisher()
    job = AnalysisJob(
        cast(async_sessionmaker[AsyncSession], _SessionFactory()),
        cast(Any, object()),
        cast(Any, object()),
        bus,
        retry_times=retry_times,
        lease_seconds=LEASE,
        max_article_tokens=BUDGET,
        structured_retries=STRUCTURED_RETRIES,
        clock=clock,
        sleep=sleep,
    )
    return job, sleeps, bus


def _use_pipeline(monkeypatch: pytest.MonkeyPatch, pipeline: _Pipeline) -> _Pipeline:
    monkeypatch.setattr(job_module, "run_pipeline", pipeline)
    return pipeline


async def test_new_runs_pipeline_and_publishes(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    job, sleeps, publisher = _make_job(monkeypatch, repo)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([_analysis()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK
    assert len(pipeline.calls) == 1
    assert pipeline.calls[0] == {
        "article_id": ARTICLE,
        "max_article_tokens": BUDGET,
        "structured_retries": STRUCTURED_RETRIES,
    }
    assert repo.claims == [(ARTICLE, VERSION, LEASE, NOW)]
    assert repo.finished == [
        {
            "article_id": ARTICLE,
            "topic": "Redis 持久化",
            "entities": ["Redis", "AOF"],
            "knowledge_points": ["RDB 与 AOF"],
        }
    ]
    assert publisher.messages[0].routing_key == KNOWLEDGE_EXTRACT
    assert publisher.messages[0].payload == {
        "articleId": 1001,
        "entities": [{"name": "Redis"}, {"name": "AOF"}],
    }
    assert repo.marked_published == 1
    assert sleeps == []


@pytest.mark.parametrize(
    "outcome",
    [ClaimOutcome.IN_PROGRESS, ClaimOutcome.STALE, ClaimOutcome.DUPLICATE],
)
async def test_skipped_claims_ack_without_side_effects(
    monkeypatch: pytest.MonkeyPatch, outcome: ClaimOutcome
) -> None:
    repo = _FakeRepo(ClaimResult(outcome, _row()))
    job, _, publisher = _make_job(monkeypatch, repo)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([_analysis()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK
    assert pipeline.calls == []
    assert repo.finished == []
    assert repo.failed == []
    assert repo.marked_published == 0
    assert publisher.messages == []


async def test_republish_uses_stored_entities(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.REPUBLISH, _row({"entities": ["Redis", "AOF"]})))
    job, _, publisher = _make_job(monkeypatch, repo)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([_analysis()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK
    assert pipeline.calls == []
    assert repo.finished == []
    assert publisher.messages[0].payload == {
        "articleId": 1001,
        "entities": [{"name": "Redis"}, {"name": "AOF"}],
    }
    assert repo.marked_published == 1


@pytest.mark.parametrize(
    "stored",
    [None, {}, {"entities": None}, {"entities": "Redis"}, {"entities": [1, None]}],
)
async def test_republish_with_broken_result_fails(
    monkeypatch: pytest.MonkeyPatch, stored: dict[str, Any] | None
) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.REPUBLISH, _row(stored)))
    job, sleeps, publisher = _make_job(monkeypatch, repo)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([_analysis()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.DLQ
    assert pipeline.calls == []
    assert publisher.messages == []
    assert repo.marked_published == 0
    assert sleeps == []
    assert "entities" in repo.failed[0]


async def test_retries_pipeline_with_backoff(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    job, sleeps, publisher = _make_job(monkeypatch, repo)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([_unavailable(), _analysis()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK
    assert len(pipeline.calls) == 2
    assert sleeps == [RETRY_BASE_SECONDS]
    assert len(repo.finished) == 1
    assert len(publisher.messages) == 1


async def test_publish_retry_does_not_rerun_pipeline(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    publisher = _FlakyPublisher(failures=1)
    job, sleeps, _ = _make_job(monkeypatch, repo, publisher=publisher)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([_analysis()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK
    assert len(pipeline.calls) == 1
    assert len(repo.finished) == 1
    assert publisher.attempts == 2
    assert sleeps == [RETRY_BASE_SECONDS]
    assert repo.marked_published == 1


async def test_exhausted_retries_mark_error_and_dlq(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    job, sleeps, publisher = _make_job(monkeypatch, repo, retry_times=2)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([_unavailable()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.DLQ
    assert len(pipeline.calls) == 3
    assert sleeps == [RETRY_BASE_SECONDS, RETRY_BASE_SECONDS * 2]
    assert "InternalUnavailable" in repo.failed[0]
    assert repo.marked_published == 0
    assert publisher.messages == []


async def test_zero_retries_marks_error_immediately(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    job, sleeps, _ = _make_job(monkeypatch, repo, retry_times=0)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([_unavailable()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.DLQ
    assert len(pipeline.calls) == 1
    assert sleeps == []
    assert repo.failed


async def test_snapshot_error_fails_without_retry(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    job, sleeps, publisher = _make_job(monkeypatch, repo)
    pipeline = _use_pipeline(monkeypatch, _Pipeline([ArticleNotPublished(ARTICLE, 1)]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK
    assert len(pipeline.calls) == 1
    assert sleeps == []
    assert "ArticleNotPublished" in repo.failed[0]
    assert publisher.messages == []
    assert repo.marked_published == 0


async def test_stale_finish_discards_result(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    repo.finish_result = False
    job, _, publisher = _make_job(monkeypatch, repo)
    _use_pipeline(monkeypatch, _Pipeline([_analysis()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK
    assert publisher.messages == []
    assert repo.marked_published == 0
    assert repo.failed == []


async def test_mark_published_false_still_acks(monkeypatch: pytest.MonkeyPatch) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    repo.mark_result = False
    job, sleeps, publisher = _make_job(monkeypatch, repo)
    _use_pipeline(monkeypatch, _Pipeline([_analysis()]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK
    assert len(publisher.messages) == 1
    assert repo.marked_published == 1
    assert sleeps == []


async def test_fail_persistence_error_does_not_raise(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    repo = _FakeRepo(ClaimResult(ClaimOutcome.NEW, _row()))
    repo.fail_error = RuntimeError("db down")
    job, _, _ = _make_job(monkeypatch, repo)
    _use_pipeline(monkeypatch, _Pipeline([ArticleNotPublished(ARTICLE, 1)]))

    result = await job.run(ARTICLE, VERSION)

    assert result is JobResult.ACK


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("retry_times", -1),
        ("lease_seconds", 0),
        ("max_article_tokens", 0),
        ("structured_retries", -1),
    ],
)
def test_rejects_invalid_settings(field: str, value: int) -> None:
    kwargs: dict[str, Any] = {
        "retry_times": 2,
        "lease_seconds": LEASE,
        "max_article_tokens": BUDGET,
        "structured_retries": STRUCTURED_RETRIES,
    }
    kwargs[field] = value

    with pytest.raises(ValueError):
        AnalysisJob(
            cast(async_sessionmaker[AsyncSession], _SessionFactory()),
            cast(Any, object()),
            cast(Any, object()),
            FakePublisher(),
            **kwargs,
        )
