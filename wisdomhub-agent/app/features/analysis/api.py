"""文章分析结果读取接口。"""

from typing import Annotated, Any

from fastapi import APIRouter, Depends, Path

from app.core.deps import AppDeps, get_deps
from app.core.errors import BusinessError, ErrorCode, success
from app.features.analysis.service import AnalysisService

router = APIRouter(prefix="/api/agent/analysis", tags=["analysis"])

Deps = Annotated[AppDeps, Depends(get_deps)]
ArticleId = Annotated[str, Path(min_length=1, max_length=64)]


def get_analysis_service(deps: Deps) -> AnalysisService:
    service = deps.analysis
    if service is None:
        raise BusinessError(ErrorCode.SERVICE_UNAVAILABLE, "分析服务暂不可用")
    return service


Service = Annotated[AnalysisService, Depends(get_analysis_service)]


@router.get("/{article_id}")
async def get_analysis(
    article_id: ArticleId,
    service: Service,
    trigger: bool = False,
) -> dict[str, Any]:
    vo = await service.get(article_id, trigger=trigger)
    return success(vo.model_dump(mode="json", by_alias=True))
