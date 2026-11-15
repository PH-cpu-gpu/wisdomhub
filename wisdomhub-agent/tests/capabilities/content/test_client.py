"""文章快照客户端：解析与异常映射。"""

from typing import Any

import pytest

from app.capabilities.content.client import (
    CONTENT_SERVICE,
    ArticleContentClient,
    ArticleContentMissing,
    ArticleNotFound,
    ArticleNotPublished,
    ArticleSnapshotInvalid,
)
from app.infra.http.internal_client import (
    InternalNotFound,
    InternalServerError,
    InternalUnavailable,
)
from tests.fakes import FakeInternalClient

PATH = "/internal/content/article/1001"


def _payload(**overrides: Any) -> dict[str, Any]:
    data: dict[str, Any] = {
        "articleId": "1001",
        "authorId": "2001",
        "title": "Spring Bean 生命周期",
        "summary": "摘要",
        "state": 2,
        "createTime": "2026-11-15T08:00:00Z",
        "publishTime": "2026-11-15T09:00:00Z",
        "updateTime": "2026-11-15T10:00:00Z",
        "viewCount": 12,
        "tags": [{"tagId": "3001", "name": "Spring", "category": "FRAMEWORK"}],
        "markdown": "# 正文",
        "locked": False,
    }
    data.update(overrides)
    return data


def _client(data: Any = None, *, error: Exception | None = None) -> ArticleContentClient:
    if error is not None:
        return ArticleContentClient(FakeInternalClient(error=error))
    return ArticleContentClient(FakeInternalClient({(CONTENT_SERVICE, PATH): data}))


async def test_parses_snapshot() -> None:
    internal = FakeInternalClient({(CONTENT_SERVICE, PATH): _payload()})
    client = ArticleContentClient(internal)

    snapshot = await client.get_snapshot("1001")

    call = internal.calls[0]
    assert call.service == CONTENT_SERVICE
    assert call.path == PATH
    assert snapshot.article_id == "1001"
    assert snapshot.author_id == "2001"
    assert snapshot.state == 2
    assert snapshot.update_time.tzinfo is not None
    assert snapshot.tags[0].tag_id == "3001"
    assert snapshot.tags[0].name == "Spring"
    assert snapshot.markdown == "# 正文"
    assert snapshot.locked is False


async def test_locked_article_without_markdown_is_returned() -> None:
    client = _client(_payload(locked=True, markdown=""))

    snapshot = await client.get_snapshot("1001")

    assert snapshot.locked is True
    assert snapshot.markdown == ""


async def test_404_maps_to_not_found() -> None:
    error = InternalNotFound(
        "文章不存在",
        service=CONTENT_SERVICE,
        path=PATH,
        status=404,
        code=404,
    )

    with pytest.raises(ArticleNotFound) as excinfo:
        await _client(error=error).get_snapshot("1001")

    assert excinfo.value.article_id == "1001"


@pytest.mark.parametrize("state", [1, 3])
async def test_non_published_state(state: int) -> None:
    with pytest.raises(ArticleNotPublished) as excinfo:
        await _client(_payload(state=state)).get_snapshot("1001")

    assert excinfo.value.state == state


@pytest.mark.parametrize("markdown", ["", "   ", None])
async def test_empty_markdown(markdown: str | None) -> None:
    with pytest.raises(ArticleContentMissing):
        await _client(_payload(markdown=markdown)).get_snapshot("1001")


async def test_missing_locked_is_invalid() -> None:
    payload = _payload()
    payload.pop("locked")

    with pytest.raises(ArticleSnapshotInvalid) as excinfo:
        await _client(payload).get_snapshot("1001")

    assert "locked" in str(excinfo.value)


async def test_non_dict_payload_is_invalid() -> None:
    with pytest.raises(ArticleSnapshotInvalid):
        await _client("oops").get_snapshot("1001")


async def test_ignores_unknown_fields() -> None:
    snapshot = await _client(_payload(extraField="x")).get_snapshot("1001")

    assert snapshot.article_id == "1001"


async def test_server_error_propagates() -> None:
    error = InternalServerError(
        "boom",
        service=CONTENT_SERVICE,
        path=PATH,
        status=500,
    )

    with pytest.raises(InternalServerError):
        await _client(error=error).get_snapshot("1001")


async def test_timeout_propagates() -> None:
    client = ArticleContentClient(FakeInternalClient(timeout=True))

    with pytest.raises(InternalUnavailable):
        await client.get_snapshot("1001")
