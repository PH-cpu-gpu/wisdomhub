"""文章快照客户端：内部接口解析与内容状态异常。"""

from pydantic import AwareDatetime, ValidationError

from app.capabilities.base import CapabilityModel, InternalGetter
from app.infra.http.internal_client import InternalNotFound

CONTENT_SERVICE = "content-service"


class ArticleTag(CapabilityModel):
    tag_id: str
    name: str
    category: str | None = None


class ArticleSnapshot(CapabilityModel):
    article_id: str
    author_id: str
    title: str
    summary: str | None = None
    state: int
    create_time: AwareDatetime
    publish_time: AwareDatetime | None = None
    update_time: AwareDatetime
    view_count: int
    tags: list[ArticleTag]
    markdown: str | None = None
    locked: bool


class ArticleSnapshotError(Exception):
    """文章快照不可用；内容状态类错误默认不可重试。"""

    retryable = False


class ArticleNotFound(ArticleSnapshotError):
    def __init__(self, article_id: str) -> None:
        self.article_id = article_id
        super().__init__(f"文章不存在: {article_id}")


class ArticleNotPublished(ArticleSnapshotError):
    def __init__(self, article_id: str, state: int) -> None:
        self.article_id = article_id
        self.state = state
        super().__init__(f"文章未发布: {article_id}（state={state}）")


class ArticleContentMissing(ArticleSnapshotError):
    def __init__(self, article_id: str) -> None:
        self.article_id = article_id
        super().__init__(f"文章正文为空: {article_id}")


class ArticleLocked(ArticleSnapshotError):
    """付费内容不进模型。"""

    def __init__(self, article_id: str) -> None:
        self.article_id = article_id
        super().__init__(f"文章属于付费内容: {article_id}")


class ArticleSnapshotInvalid(ArticleSnapshotError):
    def __init__(self, article_id: str, detail: str) -> None:
        self.article_id = article_id
        self.detail = detail
        super().__init__(f"文章快照格式不正确: {article_id}（{detail}）")


class ArticleContentClient:
    def __init__(self, internal: InternalGetter) -> None:
        self._internal = internal

    async def get_snapshot(self, article_id: str) -> ArticleSnapshot:
        path = f"/internal/content/article/{article_id}"
        try:
            data = await self._internal.get(CONTENT_SERVICE, path)
        except InternalNotFound as exc:
            raise ArticleNotFound(article_id) from exc

        if not isinstance(data, dict):
            raise ArticleSnapshotInvalid(article_id, "响应不是 JSON 对象")
        try:
            snapshot = ArticleSnapshot.model_validate(data)
        except ValidationError as exc:
            raise ArticleSnapshotInvalid(article_id, _validation_detail(exc)) from exc

        if snapshot.state != 2:
            raise ArticleNotPublished(article_id, snapshot.state)
        if snapshot.locked:
            raise ArticleLocked(article_id)
        if not (snapshot.markdown or "").strip():
            raise ArticleContentMissing(article_id)
        return snapshot


def _validation_detail(exc: ValidationError) -> str:
    parts: list[str] = []
    for item in exc.errors()[:3]:
        loc = ".".join(str(part) for part in item.get("loc", ()))
        message = str(item.get("msg", "不符合 schema"))
        parts.append(f"{loc}: {message}" if loc else message)
    return "; ".join(parts) or "字段校验失败"
