#!/bin/sh
set -e

# 容器里没有固定 IP，注册进 Nacos 的地址必须取当前容器的 eth0，否则网关调不通。
# docker-compose.prod.yml 显式传 NACOS_REGISTER_IP="" 走到这里。
if [ -z "${NACOS_REGISTER_IP:-}" ]; then
  NACOS_REGISTER_IP="$(python -c 'import socket;print(socket.gethostbyname(socket.gethostname()))')"
  export NACOS_REGISTER_IP
fi

exec uvicorn app.main:app --host 0.0.0.0 --port "${APP_PORT:-8000}" --workers 1
