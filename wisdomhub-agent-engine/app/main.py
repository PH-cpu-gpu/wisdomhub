import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from v2.nacos import NacosConfigService, NacosNamingService

from api.chat import router
from app.config import get_settings
from app.errors import register_exception_handlers
from db.session import create_engine, create_session_factory, ping_database
from registry.client import build_client_config
from registry.naming import deregister_self, register_self

log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    engine = create_engine(settings)
    config_client = None
    naming_client = None
    try:
        await ping_database(engine)
        app.state.session_factory = create_session_factory(engine)
        if settings.nacos_enabled:
            client_config = build_client_config(settings)
            config_client = await NacosConfigService.create_config_service(client_config)
            naming_client = await NacosNamingService.create_naming_service(client_config)
            await register_self(naming_client, settings)
        else:
            log.info("nacos_enabled=false，跳过 Nacos 注册")
        yield
    finally:
        if naming_client is not None:
            try:
                await deregister_self(naming_client, settings)
            except Exception:
                log.warning("Nacos 注销失败", exc_info=True)
        for client in (naming_client, config_client):
            if client is not None:
                try:
                    await client.shutdown()
                except Exception:
                    log.warning("Nacos 关停清理失败", exc_info=True)
        await engine.dispose()


app = FastAPI(title="wisdomhub-agent-engine", lifespan=lifespan)

# 注册全局异常处理器
register_exception_handlers(app)

# 配置路由
app.include_router(router)
