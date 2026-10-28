# WisdomHub

面向开发者的智能技术知识社区与知识图谱平台。

WisdomHub 把 **内容社区 + 全文搜索 + 知识图谱 + 推荐系统 + AI Agent** 组合在一起：
用户在这里发布技术文章、提问与讨论，平台通过 Elasticsearch 提供全文检索、通过 Neo4j
建立技术知识点之间的关联、结合行为数据做个性化推荐，并由独立的 Python Agent 提供
智能问答、学习路线规划与文章知识抽取。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5、Spring Cloud 2025.0、Spring Cloud Alibaba 2025.0、MyBatis-Plus、JJWT、SpringDoc |
| AI Agent | Python 3.13、FastAPI、LangChain、SQLAlchemy 2（async）、aio-pika、Nacos SDK |
| 前端 | Vue 3.5、Vite 7、TypeScript 5.9、Pinia、Vue Router、pnpm |
| 数据与搜索 | MySQL 8.4、Redis 7.4、MongoDB 6.0、Neo4j 5.26、Elasticsearch 8.18（IK 分词）、PostgreSQL 17 |
| 基础设施 | RabbitMQ 4.1、Nacos 3.0、Seata 2.5、XXL-JOB 3.4、Docker Compose |

## 模块

| 模块 | 端口 | 职责 |
| --- | --- | --- |
| `wisdomhub-gateway` | 9090 | 统一入口、路由与鉴权过滤 |
| `wisdomhub-user` | 9091 | 用户、认证、关注关系 |
| `wisdomhub-content` | 9092 | 文章、问答、评论、点赞收藏 |
| `wisdomhub-search` | 9093 | Elasticsearch 全文检索与高亮 |
| `wisdomhub-knowledge` | 9094 | 知识图谱与学习路线 |
| `wisdomhub-recommendation` | 9095 | 个性化内容推荐 |
| `wisdomhub-notification` | 9096 | 站内通知与 WebSocket 推送 |
| `wisdomhub-order` | 9097 | 付费技术专栏订单 |
| `wisdomhub-agent-engine` | 8000 | Python AI Agent（问答 / 学习规划 / 文章分析） |
| `wisdomhub-common` | — | 公共模块：core、cache、feign、job、mq、security、web |
| `wisdomhub-web` | 5173 | 前端：`landing` 静态着陆页 + `app` Vue 应用 |

## 目录结构

```text
WisdomHub/
├── wisdomhub-common/        公共模块（core/cache/feign/job/mq/security/web）
├── wisdomhub-gateway/       网关
├── wisdomhub-user/          用户与认证
├── wisdomhub-content/       文章、问答、评论
├── wisdomhub-search/        全文检索
├── wisdomhub-knowledge/     知识图谱
├── wisdomhub-recommendation/推荐
├── wisdomhub-notification/  通知
├── wisdomhub-order/         订单
├── wisdomhub-agent-engine/  Python AI Agent
├── wisdomhub-web/           前端（landing + app）
├── docker/                  中间件初始化脚本与配置
└── docker-compose.yml       本地开发中间件编排
```

## 快速开始

### 1. 环境要求

- JDK 21、Maven 3.9+（也可直接使用仓库自带的 `mvnw`）
- Docker Desktop（或兼容的 Docker Engine + Compose v2）
- Node.js >= 20.19、pnpm >= 10
- Python >= 3.13、[uv](https://github.com/astral-sh/uv)

### 2. 启动中间件

```bash
cp .env.example .env      # Windows PowerShell: Copy-Item .env.example .env
docker compose up -d
```

首次启动 MySQL 会按 `docker/mysql/init/` 下的脚本顺序建库建表。Elasticsearch 的 IK
中文分词插件不入库，需要单独安装：

```bash
bash docker/elasticsearch/install-ik.sh 8.18.8
```

> IK 插件目录为空时 Elasticsearch 会静默地不加载插件、不报任何错，务必确认安装成功。

### 3. 配置 Nacos

各业务服务的配置统一由 Nacos 管理（命名空间 `wisdomhub`）。首次运行前需要在该命名空间
下创建以下 DataId，内容按本地环境自行填写：

- `wisdomhub-env.yaml`：中间件地址、数据库账号密码等环境相关配置
- `wisdomhub-common.yaml`：公共配置（RabbitMQ、JWT、Seata 等）
- `<service>-service.yaml`：各服务的私有配置，如 `user-service.yaml`、`content-service.yaml`

### 4. 启动后端

```bash
./mvnw clean install -DskipTests
./mvnw -pl wisdomhub-gateway spring-boot:run
```

其余服务按同样方式启动（如 `-pl wisdomhub-user spring-boot:run`）。服务通过 Nacos 注册发现，
网关默认监听 9090。

### 5. 启动 AI Agent

```bash
cd wisdomhub-agent-engine
cp .env.example .env      # 填入 CHAT_API_KEY 等大模型配置
uv sync
uv run uvicorn app.main:app --port 8000
```

### 6. 启动前端

```bash
cd wisdomhub-web/app
pnpm install
pnpm dev                  # 默认 http://localhost:5173
```

`pnpm dev:mock` 可启用 mock 数据（端口 5174），无需后端即可预览页面。

## 开发提示

- 提交前请确认 `.env`、`*.key`、构建产物没有被加入版本库；它们均已在 `.gitignore` 中忽略。
- `docker/seata/application.yml` 与 `docker/mysql/init/` 下的脚本是中间件运行所需，修改后需重建对应容器才生效。
- 前端生产构建：`pnpm build`；接口地址通过 `wisdomhub-web/app/.env.production` 注入。
