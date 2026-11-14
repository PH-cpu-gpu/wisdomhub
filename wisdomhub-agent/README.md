# wisdomhub-agent

WisdomHub 的 AI 引擎：对外提供会话聊天（问答 + 学习路线）与文章分析能力。

## 技术栈

- Python 3.13，依赖与虚拟环境由 [uv](https://docs.astral.sh/uv/) 管理
- FastAPI、LangChain、LangGraph
- PostgreSQL（SQLAlchemy 异步 + Alembic 迁移）
- Nacos（服务发现与配置）、RabbitMQ（文章分析任务）

## 目录

```text
app/
├─ core/          配置、日志、错误、生命周期、依赖容器
├─ contracts/     对外契约：SSE、Chat、会话、分析
├─ chat/          会话图与 SSE 聊天用例
├─ features/      会话域与文章分析用例
├─ capabilities/  知识图谱、站内检索、内容快照、提案工具
├─ runtime/       模型、结构化输出、分词、上下文预算、观测
└─ infra/         DB、迁移、内部 HTTP、Nacos、MQ、安全
tests/            脚手架与用例
```

## 本地开发

```bash
uv sync                 # 安装运行时与开发依赖
uv run pytest           # 单元测试
uv run ruff check .     # 静态检查
uv run ruff format .    # 格式化
uv run mypy             # 类型检查
```

## 环境变量

```bash
cp .env.example .env
```

`.env.example` 列出全部变量及默认值；`.env` 不入库。

## 服务

```bash
# 需要数据库迁移与服务装配完成后可用
uv run uvicorn app.main:app --reload --port 8000
```
