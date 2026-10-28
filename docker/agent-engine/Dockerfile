# agent-engine 运行镜像。构建上下文是 wisdomhub-agent-engine/。
# uv.lock 必须存在，锁文件变更后要重新 uv lock 再构建。

FROM ghcr.io/astral-sh/uv:python3.13-bookworm-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    MALLOC_ARENA_MAX=2

WORKDIR /app

# 先装依赖再拷代码，改代码不会导致依赖层重建
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

COPY . .
RUN uv sync --frozen --no-dev && chmod +x /app/docker-entrypoint.sh

ENV PATH="/app/.venv/bin:${PATH}"
EXPOSE 8000

ENTRYPOINT ["/app/docker-entrypoint.sh"]
