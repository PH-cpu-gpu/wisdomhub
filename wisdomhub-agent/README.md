# wisdomhub-agent

WisdomHub 的 AI 引擎：对外提供会话聊天（问答 + 学习路线 + 文本问答）与文章分析能力。

## 技术栈

- Python 3.13，依赖与虚拟环境由 [uv](https://docs.astral.sh/uv/) 管理
- FastAPI、LangChain、LangGraph
- PostgreSQL（SQLAlchemy 异步 + Alembic 迁移）
- Nacos（服务发现与配置）、RabbitMQ（文章分析任务）

## 目录

```text
app/
├─ core/          配置、日志、错误、生命周期、依赖容器
├─ contracts/     对外契约：SSE 传输事件、聊天业务事件、Chat、会话、分析
├─ chat/          会话图与 SSE 聊天用例
├─ features/      会话域与文章分析用例
├─ capabilities/  知识图谱、内容快照、提案工具
├─ runtime/       模型、结构化输出、分词、上下文预算、观测
└─ infra/         DB、迁移、内部 HTTP、Nacos、MQ、安全
tests/            脚手架与用例
scripts/dev.sh    本地启动脚本
```

## 本地启动

### 1. 环境要求

- Python 3.13 + [uv](https://docs.astral.sh/uv/)
- Docker Desktop（PostgreSQL 由仓库根目录的 compose 提供）
- 可选：Nacos、RabbitMQ（不启动时按下表降级）

### 2. 安装与配置

```bash
uv sync
cp .env.example .env
```

`.env.example` 列出全部变量及默认值；至少填入 `CHAT_API_KEY`。`.env` 不入库。

### 3. 数据库与迁移

在**仓库根目录**启动 PostgreSQL：

```bash
docker compose up -d postgres
```

在 `wisdomhub-agent/` 执行迁移（服务启动不会自动迁移）：

```bash
uv run alembic upgrade head
uv run alembic downgrade -1    # 需要时回滚一版
```

`DATABASE_URL` 取自 `.env`；指向其他库时直接用环境变量覆盖。

### 4. 启动服务

```bash
bash scripts/dev.sh
# 等价于 uv run uvicorn app.main:app --reload --port 8000
```

打开 <http://127.0.0.1:8000/docs> 确认启动成功。换端口：`APP_PORT=8080 bash scripts/dev.sh`。

### 5. 可选依赖与降级

| 开关 | 关闭后 |
| --- | --- |
| `NACOS_ENABLED=false` | 不注册、不拉配置；学习路线返回 503，问答与文本问答正常 |
| `RABBITMQ_ENABLED=false` | 不消费、不装配分析服务；`/api/agent/analysis` 返回 503 |
| 两者都关 | 只保留问答与文本问答（会话仍依赖 PostgreSQL） |

只跑问答与会话：

```bash
NACOS_ENABLED=false RABBITMQ_ENABLED=false bash scripts/dev.sh
```

环境变量优先于 `.env`。Nacos 重启后需要重启服务才会重新注册。

### 6. 测试与静态检查

```bash
uv run pytest
uv run ruff check .
uv run ruff format .
uv run mypy
```

PostgreSQL 不可用时涉及数据库的用例会自动跳过；可用 `TEST_DATABASE_URL`
指向另一个实例。文章分析用例路径：`uv run pytest tests/features/analysis`。
