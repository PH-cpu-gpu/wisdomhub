import logging
from typing import Annotated, Any
from urllib.parse import quote

from fastapi import Request
from langchain_core.tools import BaseTool, ToolException, tool
from pydantic import Field

from tools.internal_client import InternalClient, InternalClientError

log = logging.getLogger(__name__)

SEARCH_SERVICE = "search-service"
KNOWLEDGE_SERVICE = "knowledge-service"
RECALL_PATH = "/internal/search/recall"
RELATED_PATH = "/internal/knowledge/related"
RELATED_BY_ID_PATH = "/internal/knowledge/tag/{tag_id}/related"

SEARCH_TOOL_NAME = "search_articles"
KNOWLEDGE_TOOL_NAME = "query_knowledge_graph"

DEFAULT_SEARCH_LIMIT = 5
MAX_SEARCH_LIMIT = 10
MAX_KEYWORD_CHARS = 100
MAX_TECHNOLOGY_CHARS = 50


async def search_articles(
    client: InternalClient,
    keyword: str,
    limit: int = DEFAULT_SEARCH_LIMIT,
) -> list[dict[str, Any]]:
    """按关键词召回文章；返回 search-service 的 ArticleRecallVO，字段原样不裁剪。"""
    keyword = keyword.strip()
    if not keyword:
        raise ToolException("关键词不能为空")
    if len(keyword) > MAX_KEYWORD_CHARS:
        raise ToolException(f"关键词不能超过 {MAX_KEYWORD_CHARS} 字")
    try:
        limit = max(1, min(int(limit), MAX_SEARCH_LIMIT))
    except (TypeError, ValueError) as exc:
        raise ToolException("limit 必须是整数") from exc

    try:
        data = await client.post(
            SEARCH_SERVICE, RECALL_PATH, json_body={"keyword": keyword, "limit": limit}
        )
    except InternalClientError as exc:
        log.warning("search_articles 失败 kind=%s status=%s code=%s",
                    exc.kind, exc.status, exc.code)
        raise ToolException("检索服务暂不可用，请稍后重试") from exc

    if not isinstance(data, list) or any(not isinstance(item, dict) for item in data):
        raise ToolException("检索服务返回格式异常")
    log.info("工具 search_articles 命中 %d 篇", len(data))
    return data


async def query_knowledge_graph(
    client: InternalClient,
    *,
    technology: str | None = None,
    tag_id: str | None = None,
) -> dict[str, Any]:
    """按技术名（LLM 入口）或 tagId（程序化入口）查直接关联技术，两者必须二选一。"""
    name = (technology or "").strip()
    tag = (str(tag_id) if tag_id is not None else "").strip()
    if bool(name) == bool(tag):
        raise ToolException("technology 与 tag_id 必须且只能给一个")
    if len(name) > MAX_TECHNOLOGY_CHARS:
        raise ToolException(f"技术名不能超过 {MAX_TECHNOLOGY_CHARS} 字")

    try:
        if name:
            data = await client.get(KNOWLEDGE_SERVICE, RELATED_PATH, params={"tech": name})
        else:
            path = RELATED_BY_ID_PATH.format(tag_id=quote(tag, safe=""))
            data = await client.get(KNOWLEDGE_SERVICE, path)
    except InternalClientError as exc:
        log.warning("query_knowledge_graph 失败 kind=%s status=%s code=%s",
                    exc.kind, exc.status, exc.code)
        if exc.code == 404 or exc.status == 404:
            target = name or f"tagId={tag}"
            raise ToolException(f"知识图谱中没有找到技术「{target}」") from exc
        raise ToolException("知识图谱服务暂不可用，请稍后重试") from exc

    if not isinstance(data, dict):
        raise ToolException("知识图谱返回格式异常")
    log.info("工具 query_knowledge_graph 返回 %d 个关系组",
             len(data.get("relations") or {}))
    return data


def build_java_tools(client: InternalClient) -> list[BaseTool]:
    """绑定 app 级 InternalClient"""

    @tool(SEARCH_TOOL_NAME)
    async def search_articles_tool(
        keyword: Annotated[str, Field(description="检索关键词，尽量是技术名或短语，如 Redis、Spring Boot")],
        limit: Annotated[int, Field(description="返回条数，只允许传入 1~10，缺省则默认为 5")] = DEFAULT_SEARCH_LIMIT,
    ) -> list[dict[str, Any]]:
        """在 WisdomHub 文章库中按关键词检索文章，返回标题、摘要、标签、热度与 articleId。
        回答涉及具体技术、框架或概念时先调用它；空列表表示没有相关文章。"""
        return await search_articles(client, keyword, limit)

    @tool(KNOWLEDGE_TOOL_NAME)
    async def query_knowledge_graph_tool(
        technology: Annotated[str, Field(description="技术名，必须与词表一致且大小写敏感，如 Redis、Spring Boot")],
    ) -> dict[str, Any]:
        """查询技术知识图谱中某技术的直接关联技术，按关系类型分组：
        PREREQUISITE 前置知识、BASED_ON 基于、USES 使用、SIMILAR_TO 相似、BELONGS_TO 属于；
        out 表示本技术指向对方，in 表示对方指向本技术。技术名不在词表里会报错。"""
        return await query_knowledge_graph(client, technology=technology)

    return [search_articles_tool, query_knowledge_graph_tool]


def get_java_tools(request: Request) -> list[BaseTool]:
    return getattr(request.app.state, "java_tools", [])
