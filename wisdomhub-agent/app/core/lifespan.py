"""应用启动与关停编排。"""

import logging
from collections.abc import AsyncIterator, Awaitable, Callable, Sequence
from contextlib import AbstractAsyncContextManager, asynccontextmanager
from dataclasses import dataclass
from typing import Any, cast

import aio_pika
import httpx
from aio_pika.abc import AbstractConnection
from fastapi import FastAPI

from app.capabilities.content.client import ArticleContentClient
from app.capabilities.knowledge.tools import KnowledgeTools
from app.chat.graph.build import build_graph
from app.chat.graph.node import ChatModel
from app.core.config import Settings, get_settings
from app.core.deps import AppDeps
from app.features.analysis.consumer import build_analyze_consumer
from app.features.analysis.job import AnalysisJob
from app.features.analysis.service import AnalysisService
from app.features.conversation.service import ConversationService
from app.infra.db.session import Database
from app.infra.http.internal_client import InternalClient
from app.infra.mq.config import resolve_rabbitmq_url
from app.infra.mq.publisher import EventPublisher
from app.infra.mq.topology import declare_topology
from app.infra.nacos.client import NacosClients, create_nacos_clients
from app.infra.nacos.config import (
    NacosConfigError,
    load_env_config,
    load_internal_sign_secret,
)
from app.infra.nacos.naming import NacosServiceResolver, deregister_self, register_self
from app.infra.security.internal_sign import InternalSigner
from app.runtime.llm import LlmProfile, LlmRegistry

log = logging.getLogger(__name__)

Startup = Callable[[AppDeps], Awaitable[None]]
Lifespan = Callable[[FastAPI], AbstractAsyncContextManager[None]]


@dataclass(frozen=True, slots=True)
class BuildStep:
    name: str
    run: Startup


@dataclass
class _Runtime:
    """装配中间态：只给后续步骤用，不进入 AppDeps。"""

    settings: Settings
    llm: LlmRegistry | None = None
    database: Database | None = None
    nacos: NacosClients | None = None
    signer: InternalSigner | None = None
    internal: InternalClient | None = None
    content: ArticleContentClient | None = None
    env_config: dict[str, Any] | None = None
    publisher: EventPublisher | None = None
    connection: AbstractConnection | None = None


def build_steps(settings: Settings) -> list[BuildStep]:
    rt = _Runtime(settings)

    async def build_llm(_: AppDeps) -> None:
        registry = LlmRegistry(settings)
        registry.get(LlmProfile.CHAT)
        registry.get(LlmProfile.ANALYSIS)
        rt.llm = registry

    async def build_database(deps: AppDeps) -> None:
        database = Database.create(settings.db)
        deps.on_close("database", database.close)
        await database.ping()
        rt.database = database
        deps.conversation = ConversationService(
            database.session_factory,
            ttl_seconds=settings.app.message_in_progress_ttl,
        )

    async def build_nacos(deps: AppDeps) -> None:
        clients = await create_nacos_clients(settings)
        if clients is None:
            log.info("Nacos 关闭，跳过注册与配置拉取")
            return
        rt.nacos = clients

        async def close_nacos() -> None:
            try:
                await deregister_self(clients.naming, settings)
            finally:
                await clients.aclose()

        deps.on_close("nacos", close_nacos)
        await register_self(clients.naming, settings)
        rt.signer = InternalSigner(await load_internal_sign_secret(clients.config, settings))
        try:
            rt.env_config = await load_env_config(clients.config, settings)
        except NacosConfigError:
            log.warning("Nacos 缺少环境配置，RabbitMQ 需显式配置 RABBITMQ_URL", exc_info=True)

    async def build_internal(deps: AppDeps) -> None:
        if rt.nacos is None or rt.signer is None:
            log.info("Nacos 关闭，内部客户端与能力工具不可用")
            return
        internal = InternalClient(
            settings.limits,
            NacosServiceResolver(rt.nacos.naming, settings.nacos.group),
            rt.signer,
            httpx.AsyncClient(),
        )
        deps.on_close("internal-client", internal.aclose)
        rt.internal = internal
        rt.content = ArticleContentClient(internal)
        deps.knowledge_tools = KnowledgeTools(internal)

    async def build_chat_graph(deps: AppDeps) -> None:
        if rt.llm is None:
            raise RuntimeError("模型未装配")
        deps.graph = build_graph(
            cast(ChatModel, rt.llm.get(LlmProfile.CHAT)),
            deps.knowledge_tools,
        )

    async def build_analysis(deps: AppDeps) -> None:
        if rt.llm is None or rt.database is None or rt.content is None:
            log.info("依赖不完整，跳过分析服务装配")
            return
        url = resolve_rabbitmq_url(settings, rt.env_config)
        if url is None:
            log.info("RabbitMQ 未配置或关闭，跳过分析服务装配")
            return
        try:
            connection = await aio_pika.connect_robust(
                url, reconnect_interval=settings.mq.reconnect_interval
            )
        except Exception:
            log.warning("RabbitMQ 连接失败，跳过分析服务装配", exc_info=True)
            return
        try:
            channel = await connection.channel(publisher_confirms=True)
            topology = await declare_topology(channel)
        except Exception:
            log.warning("RabbitMQ 拓扑初始化失败，跳过分析服务装配", exc_info=True)
            await _close_quietly(connection)
            return

        rt.connection = connection
        rt.publisher = EventPublisher(topology.events)
        deps.on_close("mq-connection", connection.close)

        job = AnalysisJob(
            rt.database.session_factory,
            rt.content,
            rt.llm.get(LlmProfile.ANALYSIS),
            rt.publisher,
            retry_times=settings.mq.analysis_retry_times,
            lease_seconds=settings.mq.article_analysis_ttl,
            max_article_tokens=settings.llm.analysis_max_article_tokens,
            structured_retries=settings.llm.structured_retry_times,
        )
        deps.analysis = AnalysisService(
            rt.database.session_factory,
            rt.content,
            job,
            spawn=deps.spawn,
        )
        consumer = build_analyze_consumer(
            url,
            job,
            reconnect_interval=settings.mq.reconnect_interval,
        )
        deps.on_close("mq-consumer", consumer.stop)
        deps.spawn(consumer.run(), name="analysis-consumer")

    return [
        BuildStep("llm", build_llm),
        BuildStep("database", build_database),
        BuildStep("nacos", build_nacos),
        BuildStep("internal-client", build_internal),
        BuildStep("graph", build_chat_graph),
        BuildStep("analysis", build_analysis),
    ]


async def _close_quietly(connection: AbstractConnection) -> None:
    try:
        await connection.close()
    except Exception:
        log.warning("关闭 MQ 连接失败", exc_info=True)


async def build_deps(settings: Settings, steps: Sequence[BuildStep]) -> AppDeps:
    deps = AppDeps(settings=settings)
    try:
        for step in steps:
            log.info("启动依赖: %s", step.name)
            await step.run(deps)
    except BaseException:
        await deps.aclose()
        raise
    return deps


def create_lifespan(
    steps: Sequence[BuildStep] = (),
    *,
    settings: Settings | None = None,
) -> Lifespan:
    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        deps = await build_deps(settings or get_settings(), steps)
        app.state.deps = deps
        try:
            yield
        finally:
            await deps.aclose()

    return lifespan


lifespan = create_lifespan()
