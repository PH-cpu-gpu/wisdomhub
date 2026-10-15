from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """环境相关的配置全在这儿；契约常量（服务名 / group / 路由）留在各自模块里硬编码。"""

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    nacos_server_addr: str = "127.0.0.1:8848"
    nacos_namespace: str = "wisdomhub"
    nacos_register_ip: str = "127.0.0.1"
    # app占用端口
    app_port: int = 8000

    # 故意不给默认值, 缺少大模型配置程序直接报错
    chat_model: str
    chat_base_url: str
    chat_api_key: str


@lru_cache
def get_settings() -> Settings:
    return Settings()
