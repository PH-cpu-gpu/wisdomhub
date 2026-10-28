import asyncio
import logging
from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, Depends, Request
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from app.response import success
from db import analysis_repository
from db.session import get_session_factory

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/agent", tags=["agent"])

# 同一进程内避免同一篇文章的懒触发任务叠加；跨进程由 claim 的幂等兜底
_LAZY_INFLIGHT: set[str] = set()
# 持有 Task 强引用，避免事件循环只保留弱引用导致任务中途被回收
_LAZY_TASKS: set[asyncio.Task[None]] = set()


def _schedule_lazy_analysis(request: Request, article_id: str) -> None:
    consumer = getattr(request.app.state, "analysis_consumer", None)
    if consumer is None or consumer.job is None:
        log.info("文章分析消费者未启用，跳过懒触发 article=%s", article_id)
        return
    if article_id in _LAZY_INFLIGHT:
        return
    _LAZY_INFLIGHT.add(article_id)

    async def runner() -> None:
        try:
            await consumer.trigger(article_id)
        except Exception:
            log.exception("懒触发文章分析失败 article=%s", article_id)
        finally:
            _LAZY_INFLIGHT.discard(article_id)

    task = asyncio.create_task(runner())
    _LAZY_TASKS.add(task)
    task.add_done_callback(_LAZY_TASKS.discard)


@router.get("/analysis/{articleId}")
async def get_analysis(
    articleId: str,
    request: Request,
    session_factory: Annotated[
        async_sessionmaker[AsyncSession], Depends(get_session_factory)
    ],
    trigger: bool = False,
):
    async with session_factory() as session:
        row = await analysis_repository.get(session, articleId)

    # trigger=true 才懒触发；in_progress 仅在租约过期后补跑
    expired = (
        row is not None
        and row.status == "in_progress"
        and (row.expires_at is None or row.expires_at <= datetime.now(UTC))
    )
    if trigger and (row is None or row.status == "error" or expired):
        _schedule_lazy_analysis(request, articleId)

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
