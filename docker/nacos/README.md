# Nacos 配置模板

## 为什么需要

各服务的 `application-local.yml` 这样写：

```yaml
spring:
  config:
    import:
      - nacos:wisdomhub-env.yaml
      - nacos:wisdomhub-common.yaml
      - nacos:<service>-service.yaml
```

`spring.config.import` 没有 `optional:` 前缀，**缺少任何一个 DataId 服务都会启动失败**。
模板的作用就是告诉新环境：每个 DataId 需要哪些键、哪些值必须自己填。

## 目录

```text
docker/nacos/
├── config/                      # 脱敏模板（入库），文件名去掉 .example 即 DataId
│   ├── wisdomhub-env.yaml.example
│   ├── wisdomhub-common.yaml.example
│   ├── gateway-service.yaml.example
│   ├── user-service.yaml.example
│   ├── content-service.yaml.example
│   ├── interaction-service.yaml.example
│   ├── video-service.yaml.example
│   ├── search-service.yaml.example
│   ├── knowledge-service.yaml.example
│   ├── recommendation-service.yaml.example
│   ├── notification-service.yaml.example
│   ├── order-service.yaml.example
│   ├── agent-service.yaml.example
│   ├── cos-key.yaml.example
│   └── mps-key.yaml.example
├── import.ps1                   # Windows 批量导入
└── import.sh                    # macOS / Linux 批量导入
```

## 使用步骤

1. **替换占位符**：把 `config/*.example` 里的 `change-me` / `your-xxx` 改成实际值。
2. **导入**（Nacos 需先启动，命名空间 `wisdomhub` 需存在）：

   ```powershell
   $env:NACOS_PASSWORD = '你的Nacos密码'
   pwsh -File docker/nacos/import.ps1
   ```

   ```bash
   export NACOS_PASSWORD='你的Nacos密码'
   bash docker/nacos/import.sh
   ```

3. **验证**：登录控制台 <http://127.0.0.1:8849>，在 `wisdomhub` 命名空间下应能看到 15 个配置。

## DataId 一览

| DataId | 内容 |
| --- | --- |
| `wisdomhub-env.yaml` | 环境相关：中间件地址与账号密码、XXL-JOB 调度中心地址与 token |
| `wisdomhub-common.yaml` | 公共配置：日志格式、内部签名密钥、Feign 超时、RabbitMQ 连接与重试、JWT 公钥 |
| `gateway-service.yaml` | 网关路由、限流、CORS、免鉴权路径 |
| `user-service.yaml` | 用户服务：数据源、Redis、上传大小、XXL 执行器、OpenAPI 路径、JWT 私钥 |
| `content-service.yaml` | 内容服务：数据源、Redis、MongoDB、Seata、对账与清理参数 |
| `interaction-service.yaml` | 互动服务：数据源、Redis、XXL 执行器与计数对账、OpenAPI 路径 |
| `video-service.yaml` | 视频服务：数据源、OpenAPI 路径 |
| `search-service.yaml` | 搜索服务：Elasticsearch、Redis、XXL 执行器、对账参数 |
| `knowledge-service.yaml` | 知识图谱服务：Neo4j、Redis、XXL 执行器 |
| `recommendation-service.yaml` | 推荐服务：Redis、Caffeine/Redis 缓存 TTL |
| `notification-service.yaml` | 通知服务：数据源、Redis、死信重放 |
| `order-service.yaml` | 订单服务：数据源、Redis、Seata |
| `agent-service.yaml` | Agent 的 Spring 侧配置：Redis、MongoDB、OpenAPI 路径 |
| `cos-key.yaml` | 腾讯云 COS 凭据（头像上传、视频源文件） |
| `mps-key.yaml` | 腾讯云 MPS 凭据与区域（视频转码） |

## 占位符说明

| 占位符 | 出现位置 | 填什么 |
| --- | --- | --- |
| `change-me` | 各中间件密码、XXL-JOB `access-token`、COS 密钥 | 实际密码 / 密钥 |
| `change-me-to-a-random-64-hex` | `wisdomhub-common.yaml` 的 `internal-sign.secret` | 64 位十六进制随机串，可用 `openssl rand -hex 32` 生成 |
| `change-me-jwt-private-key` | `user-service.yaml` 的 `jwt.private-key` | 单行 Base64(PKCS#8 DER)，必须与 common 的 `jwt.public-key` 成对 |
| `your-bucket-name-APPID` | `cos-key.yaml` | COS 存储桶名 |
| `https://your-bucket-name-APPID.cos.ap-shanghai.myqcloud.com` | `cos-key.yaml` | COS 访问域名 |

