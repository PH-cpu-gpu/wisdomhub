#!/bin/sh
# 容器入口：解析 Nacos 注册 IP 后启动 FastAPI。
set -eu

if [ -z "${NACOS_REGISTER_IP:-}" ]; then
    NACOS_REGISTER_IP="$(
        python -c 'import socket; s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM); s.connect(("192.0.2.1", 9)); print(s.getsockname()[0])' \
            || hostname -i | awk '{print $1}'
    )"
    export NACOS_REGISTER_IP
fi

if [ -z "$NACOS_REGISTER_IP" ]; then
    echo "无法确定容器 IP，请显式设置 NACOS_REGISTER_IP" >&2
    exit 1
fi
echo "Nacos 注册 IP: $NACOS_REGISTER_IP"

exec uvicorn app.main:app --host 0.0.0.0 --port "${APP_PORT:-8000}"
