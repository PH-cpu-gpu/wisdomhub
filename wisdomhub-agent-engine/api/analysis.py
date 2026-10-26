from typing import Annotated

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.response import success
from db import analysis_repository
from db.session import get_session_factory

router = APIRouter(prefix="/api/agent", tags=["agent"])


@router.get("/analysis/{articleId}")
async def get_analysis(
    articleId: str,
    session_factory: Annotated[
        async_sessionmaker[AsyncSession], Depends(get_session_factory)
    ],
):
    async with session_factory() as session:
        row = await analysis_repository.get(session, articleId)

    if row is None or row.status == "in_progress":
        return success({"articleId": articleId, "status": "pending"})
    if row.status == "error":
        return success({
            "articleId": articleId,
            "status": "error",
            "message": "文章分析失败，请稍后重试",
        })

    analysis = row.analysis if isinstance(row.analysis, dict) else {}
    return success({
        "articleId": articleId,
        "status": "done",
        "topic": analysis.get("topic"),
        "entities": analysis.get("entities") or [],
        "knowledgePoints": analysis.get("knowledge_points") or [],
    })
