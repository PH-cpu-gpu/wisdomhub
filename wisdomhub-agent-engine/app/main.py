import asyncio
import json
import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from v2.nacos import NacosConfigService, NacosNamingService

from app.config import get_settings
from registry.client import build_client_config
from registry.naming import deregister_self, register_self

log = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    client_config = build_client_config(settings)
    config_client = await NacosConfigService.create_config_service(client_config)
    naming_client = await NacosNamingService.create_naming_service(client_config)
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


# 网关 springdoc 的 agent 入口（gateway-service.yaml 里 url 指向这里）
@app.get("/api/agent/v3/api-docs")
async def agent_openapi():
    return app.openapi()


class ChatRequest(BaseModel):
    conversationId: str | None = None
    messageId: str | None = None
    question: str


# TODO(B3.3 临时): 回声端点，仅供网关/前端 SSE 联调；B3.4 落 PG、B3.18 接真实工作流后删除。
@app.post("/api/agent/chat")
async def chat(request: ChatRequest, x_user_id: str | None = Header(default=None, alias="X-User-Id")):
    if not x_user_id:
        raise HTTPException(status_code=401, detail="missing X-User-Id")

    conversation_id = request.conversationId or f"c-{uuid.uuid4().hex}"
    message_id = request.messageId or f"m-{uuid.uuid4().hex}"
    meta = json.dumps({"conversationId": conversation_id, "messageId": message_id},
                      ensure_ascii=False, separators=(",", ":"))

    async def event_stream():
        yield f"event: meta\ndata: {meta}\n\n"
        yield 'event: delta\ndata: {"text":"收到问题："}\n\n'
        await asyncio.sleep(0.2)
        payload = json.dumps({"text": request.question}, ensure_ascii=False, separators=(",", ":"))
        yield f"event: delta\ndata: {payload}\n\n"
        await asyncio.sleep(0.2)
        yield 'event: done\ndata: {"finishReason":"stop"}\n\n'

    return StreamingResponse(event_stream(), media_type="text/event-stream")
