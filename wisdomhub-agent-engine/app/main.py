import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from v2.nacos import NacosConfigService, NacosNamingService

from api.chat import router
from app.config import get_settings
from app.errors import register_exception_handlers
from db.session import create_engine, create_session_factory, ping_database
from models.llm import create_chat_model
from registry.client import build_client_config
from registry.naming import SERVICE_NAME, deregister_self, register_self
from security.internal_sign import InternalSigner, load_internal_sign_secret
from tools.internal_client import create_internal_client

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    engine = create_engine(settings)
    config_client = None
    naming_client = None
    internal_client = None
    app.state.internal_client = None
    app.state.chat_model = None
    try:
        app.state.chat_model = create_chat_model(settings)
        await ping_database(engine)
        app.state.session_factory = create_session_factory(engine)
        if settings.nacos_enabled:
            client_config = build_client_config(settings)
            config_client = await NacosConfigService.create_config_service(client_config)
            naming_client = await NacosNamingService.create_naming_service(client_config)
            await register_self(naming_client, settings)
            secret = await load_internal_sign_secret(config_client)
            signer = InternalSigner(secret, service_name=SERVICE_NAME)
            internal_client = create_internal_client(settings, naming_client, signer)
            app.state.internal_client = internal_client
        else:
            log.info("nacos_enabled=false，跳过 Nacos 注册与内部调用客户端")
        yield
    finally:
        if internal_client is not None:
            try:
                await internal_client.aclose()
            except Exception:
                log.warning("内部调用客户端关闭失败", exc_info=True)
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
