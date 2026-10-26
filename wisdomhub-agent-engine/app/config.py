from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """环境相关的配置全在这儿；契约常量（服务名 / group / 路由）留在各自模块里硬编码。"""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # nacos相关配置
    nacos_server_addr: str = "127.0.0.1:8848"
    nacos_namespace: str = "wisdomhub"
    nacos_register_ip: str = "127.0.0.1"
    app_port: int = 8000
    # SSE 空闲兜底（秒），必须大于 chat_timeout；LLM 自己的读超时先触发
    stream_idle_timeout: float = 90.0
    # 会话库；缺失直接启动失败
    database_url: str
    # assistant 处于 in_progress 的接管时限（秒）
    message_in_progress_ttl: int = 300
    # 上下文窗口：轮数 + token 双预算
    context_max_turns: int = 20
    context_max_tokens: int = 12000
    context_message_max_tokens: int = 2000
    # Python -> Java /internal/**：连接 1s / 查询 3s / 长任务 10m
    internal_connect_timeout: float = 1.0
    internal_query_timeout: float = 3.0
    internal_long_timeout: float = 600.0
    internal_retry_times: int = 1

    # 故意不给默认值, 缺少大模型配置程序直接报错
    chat_model: str
    chat_base_url: str
    chat_api_key: str
    chat_temperature: float = 0.3
    chat_timeout: float = 60.0
    # 文章分析：长任务 LLM 超时与正文截断
    analysis_timeout: float = 600.0
    analysis_max_article_tokens: int = 24000
    # 文章分析 MQ
    rabbitmq_enabled: bool = True
    rabbitmq_url: str | None = None
    rabbitmq_reconnect_interval: float = 5.0
    analysis_retry_times: int = 2
    article_analysis_ttl: int = 1800

    # 测试使用
    # false 用于本地旁路测试：跳过 Nacos 注册
    nacos_enabled: bool = True


@lru_cache
def get_settings() -> Settings:
    return Settings()
