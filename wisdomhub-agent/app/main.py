"""FastAPI 应用装配：路由、异常、日志与 trace。"""

from fastapi import FastAPI

from app import __version__
from app.chat.api import router as chat_router
from app.core.config import Settings, get_settings
from app.core.errors import register_exception_handlers
from app.core.lifespan import build_steps, create_lifespan
from app.core.logging import TraceIdMiddleware, configure_logging
from app.features.analysis.api import router as analysis_router
from app.features.conversation.api import router as conversation_router

TITLE = "WisdomHub Agent"


def create_app(settings: Settings | None = None) -> FastAPI:
    resolved = settings or get_settings()
    configure_logging(resolved.app.log_level, resolved.app.log_json)
    application = FastAPI(
        title=TITLE,
        version=__version__,
        lifespan=create_lifespan(build_steps(resolved), settings=resolved),
    )
    application.add_middleware(TraceIdMiddleware)
    register_exception_handlers(application)
    for router in (chat_router, conversation_router, analysis_router):
        application.include_router(router)
    return application


app = create_app()
