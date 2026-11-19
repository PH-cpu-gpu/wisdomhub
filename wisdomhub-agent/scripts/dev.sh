#!/bin/sh
# 本地启动：只起 uvicorn，迁移见 README。
set -eu

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
    echo "缺少 .env，请先执行: cp .env.example .env" >&2
    exit 1
fi

exec uv run uvicorn app.main:app --reload --port "${APP_PORT:-8000}"
