from v2.nacos import ClientConfigBuilder, GRPCConfig

from app.config import Settings


def build_client_config(settings: Settings):
    """Nacos 客户端配置。namespace 使用 wisdomhub"""
    return (ClientConfigBuilder()
            .server_address(settings.nacos_server_addr)
            .namespace_id(settings.nacos_namespace)
            .log_level("INFO")
            .grpc_config(GRPCConfig(grpc_timeout=3000))
            .build())
