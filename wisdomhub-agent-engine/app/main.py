import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from v2.nacos import NacosConfigService

from app.config import get_settings
from registry.client import build_client_config
from registry.naming import create_naming_client, deregister_self, register_self

log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    client_config = build_client_config(settings)
    config_client = await NacosConfigService.create_config_service(client_config)
    naming_client = await create_naming_client(client_config)
    await register_self(naming_client, settings)
    try:
        yield
    finally:
        try:
            await deregister_self(naming_client, settings)
            await naming_client.shutdown()
            await config_client.shutdown()
        except Exception:
            log.warning("Nacos 关停清理失败", exc_info=True)


app = FastAPI(title="wisdomhub-agent-engine", lifespan=lifespan)
