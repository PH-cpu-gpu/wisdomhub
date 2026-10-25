import logging
from typing import Annotated, Any
from urllib.parse import quote

from fastapi import Request
from langchain_core.tools import BaseTool, ToolException, tool
from pydantic import Field

from tools.internal_client import InternalClient, InternalClientError

log = logging.getLogger(__name__)

CONTENT_SERVICE = "content-service"
ARTICLE_PATH = "/internal/content/article/{article_id}"

KNOWLEDGE_SERVICE = "knowledge-service"
RELATED_PATH = "/internal/knowledge/related"
RELATED_BY_ID_PATH = "/internal/knowledge/tag/{tag_id}/related"
PREREQUISITE_PATH = "/internal/knowledge/prerequisites"

KNOWLEDGE_TOOL_NAME = "query_knowledge_graph"
PREREQUISITE_TOOL_NAME = "query_prerequisite_chain"

MAX_CHAIN_DEPTH = 5
MAX_TECHNOLOGY_CHARS = 50


async def get_article_snapshot(
    client: InternalClient, article_id: str
) -> dict[str, Any]:
    """文章完整快照：元数据 + 标签 + markdown 正文。"""
    path = ARTICLE_PATH.format(article_id=quote(str(article_id), safe=""))
    data = await client.get(CONTENT_SERVICE, path)
    if not isinstance(data, dict):
        raise ValueError("文章快照格式异常")
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


async def query_prerequisite_chain(
    client: InternalClient, technology: str
) -> dict[str, Any]:
    """按技术名查前置知识链；返回 knowledge-service 的 PrerequisiteTreeVO。"""
    name = (technology or "").strip()
    if not name:
        raise ToolException("技术名不能为空")
    if len(name) > MAX_TECHNOLOGY_CHARS:
        raise ToolException(f"技术名不能超过 {MAX_TECHNOLOGY_CHARS} 字")

    try:
        data = await client.get(
            KNOWLEDGE_SERVICE,
            PREREQUISITE_PATH,
            params={"tech": name, "maxDepth": MAX_CHAIN_DEPTH},
        )
    except InternalClientError as exc:
        log.warning("query_prerequisite_chain 失败 kind=%s status=%s code=%s",
                    exc.kind, exc.status, exc.code)
        if exc.code == 404 or exc.status == 404:
            raise ToolException(f"知识图谱中没有找到技术「{name}」") from exc
        raise ToolException("知识图谱服务暂不可用，请稍后重试") from exc

    if not isinstance(data, dict):
        raise ToolException("知识图谱返回格式异常")
    log.info("工具 query_prerequisite_chain 返回 %d 层",
             len(data.get("levels") or []))
    return data


def build_java_tools(client: InternalClient) -> list[BaseTool]:
    """绑定 app 级 InternalClient"""

    @tool(KNOWLEDGE_TOOL_NAME)
    async def query_knowledge_graph_tool(
        technology: Annotated[str, Field(description="技术名，必须与词表一致且大小写敏感，如 Redis、Spring Boot")],
    ) -> dict[str, Any]:
        """查询技术知识图谱中某技术的直接关联技术，按关系类型分组：
        PREREQUISITE 前置知识、BASED_ON 基于、USES 使用、SIMILAR_TO 相似、BELONGS_TO 属于；
        out 表示本技术指向对方，in 表示对方指向本技术。技术名不在词表里会报错。"""
        return await query_knowledge_graph(client, technology=technology)

    @tool(PREREQUISITE_TOOL_NAME)
    async def query_prerequisite_chain_tool(
        technology: Annotated[str, Field(description="目标技术名，必须与词表一致且大小写敏感，如 Redis、Spring Boot")],
    ) -> dict[str, Any]:
        """查询技术的前置知识链，按层返回：level 1 是直接前置，数字越大越基础；
        truncated 为 true 表示还有更基础的前置没展开。技术名不在词表里会报错。"""
        return await query_prerequisite_chain(client, technology)

    return [query_knowledge_graph_tool, query_prerequisite_chain_tool]


def get_java_tools(request: Request) -> list[BaseTool]:
    return getattr(request.app.state, "java_tools", [])
