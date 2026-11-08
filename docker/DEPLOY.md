# WisdomHub 部署手册

目标环境：**2 核 4GB 单机 Linux**，Docker Compose 部署，前端静态托管在宿主机的 nginx 上。

核心前提：**构建在开发机，服务器只负责 run**。2 核上跑 `mvn package` / `pnpm build` 会卡死，
镜像一律本地构建后 `docker save` 传过去。

| 机器 | 需要 | 职责 |
| --- | --- | --- |
| 开发机 | JDK 21、Maven、Node ≥ 20.19、pnpm、Docker | 打 jar、打前端、构建镜像 |
| 服务器 | Ubuntu 22.04+/Debian 12+、Docker + Compose ≥ 2.24 | 跑容器、跑 nginx |

> Compose 版本卡死在 2.24 是因为 `docker-compose.prod.yml` 用了 `!override` 重写端口绑定。

---

## 1. 服务器：装 Docker

```bash
curl -fsSL https://get.docker.com | sh
systemctl enable --now docker
docker compose version     # 必须 >= 2.24
```

---

## 2. 开发机：构建

### 2.1 jar 与前端

```bash
./mvnw clean package -DskipTests

cd wisdomhub-web/app
corepack pnpm install --frozen-lockfile
corepack pnpm build
cd ../..
```

### 2.2 镜像

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml build
```

产物是 9 个 `wisdomhub/*:latest`。确认架构和服务器一致：

```bash
docker image inspect wisdomhub/gateway:latest --format '{{.Os}}/{{.Architecture}}'
```

### 2.3 打包

```bash
docker save $(docker images --format '{{.Repository}}:{{.Tag}}' | grep '^wisdomhub/') \
  | gzip -1 > wisdomhub-images.tar.gz
```

9 个 Java 镜像共用同一个 alpine 基础层，`docker save` 会去重，实际传输约 400–700 MB。

---

## 3. 传输

```bash
rsync -av --exclude node_modules --exclude .git --exclude target \
  ./ root@<服务器IP>:/opt/wisdomhub/

scp wisdomhub-images.tar.gz root@<服务器IP>:/root/
```

`docker/elasticsearch/plugins/` 在 `.gitignore` 里，clone 或 rsync 默认带不上。必须在服务器上补一次：

```bash
# 二选一
bash /opt/wisdomhub/docker/elasticsearch/install-ik.sh 8.18.8
rsync -av docker/elasticsearch/plugins/ root@<服务器IP>:/opt/wisdomhub/docker/elasticsearch/plugins/
```

> ⚠️ 插件目录为空时 ES 会**静默不加载插件、不报任何错**，搜索会退化成切单字。

---

## 4. 服务器：宿主机初始化

```bash
cd /opt/wisdomhub

# zram + 2G 兜底 swap + vm.max_map_count（ES 不设直接拒绝启动）+ Docker 日志轮转
sudo bash docker/host-init.sh

ufw allow 22 && ufw allow 80 && ufw allow 443 && ufw enable
```

除 80/443 外所有端口都绑在 `127.0.0.1`。要连 MySQL 或 Nacos 控制台走 SSH 隧道：

```bash
ssh -L 8849:127.0.0.1:8849 -L 3306:127.0.0.1:3306 root@<服务器IP>
```

---

## 5. 服务器：配置

### 5.1 `.env`

```bash
cd /opt/wisdomhub
cp .env.example .env
```

必须改：

| 变量 | 说明 |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` | 同时供 xxl-job-admin 使用 |
| `MONGO_PASSWORD` / `NEO4J_PASSWORD` / `RABBITMQ_PASSWORD` / `POSTGRES_PASSWORD` | 各自独立 |
| `NACOS_AUTH_TOKEN` | `openssl rand -base64 32`，必须 ≥ 32 字节 |
| `CHAT_MODEL` / `CHAT_BASE_URL` / `CHAT_API_KEY` | agent-engine 用，不填它起不来 |

`REGISTRY`、`TAG` 保持默认即可，镜像名就是 `wisdomhub/xxx:latest`。

### 5.2 Nacos 命名空间

先只起 MySQL 和 Nacos：

```bash
export COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml
docker compose up -d mysql nacos
docker compose ps          # 等 mysql 变 healthy
```

SSH 隧道打开 `http://127.0.0.1:8849` 登录控制台 → 命名空间 → 新建：

- **ID 必须手填 `wisdomhub`**。留空 Nacos 会自动生成 UUID，和代码里的 `namespace: wisdomhub` 对不上。
- 首次登录会强制改密码，改完记下来，下一步要用。

### 5.3 导入配置

生产用的环境值模板是 `docker/nacos/wisdomhub-env.prod.yaml.example`，它和开发版的区别只有三处：
`infra.*` 换成容器名、删掉 `spring.cloud.nacos.discovery.ip`、xxl-job 地址改容器名。

```bash
cp docker/nacos/wisdomhub-env.prod.yaml.example docker/nacos/config/wisdomhub-env.yaml.example
```

> ⚠️ 改完之后这几个文件里是**真密码**，而它们的文件名以 `.example` 结尾、是被 git 正常跟踪的。
> 在服务器上填完就直接跑导入，**不要再提交回仓库**。

> 删 `discovery.ip` 是必须的：`127.0.0.1` 是给 Windows 上的 Radmin 网卡加的，
> 容器里注册回环地址会导致网关永远调不通下游。`docker-compose.prod.yml` 里另外用
> `SPRING_CLOUD_NACOS_DISCOVERY_IP=""` 兜了一层。

需要手填的占位符（`change-me` / `your-xxx`）：

| 文件 | 字段 | 怎么填 |
| --- | --- | --- |
| `wisdomhub-env.yaml.example` | `mysql-password`、`mongo-password`、`neo4j-password`、`rabbitmq-password` | 与 `.env` 保持一致 |
| `wisdomhub-env.yaml.example` | `xxl.job.access-token` | 随便一个随机串 |
| `wisdomhub-common.yaml.example` | `internal-sign.secret` | `openssl rand -hex 32` |
| `cos-key.yaml.example` | `secret-id` / `secret-key` / `bucket-name` / `domain` | 腾讯云 COS；没开通也要填占位值，user-service 的 `spring.config.import` 是强制的 |
| `mps-key.yaml.example` | `secret-id` / `secret-key` | 腾讯云 MPS；没开通也要填占位值，video-service 的 `spring.config.import` 是强制的 |

导入：

```bash
export NACOS_PASSWORD='控制台密码'
bash docker/nacos/import.sh      # 15 个 DataId
```

### 5.4 加载镜像

```bash
gunzip -c /root/wisdomhub-images.tar.gz | docker load
```

---

## 6. 启动

```bash
cd /opt/wisdomhub
export COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml

# 先起中间件
docker compose up -d mysql redis mongodb rabbitmq elasticsearch nacos
watch -n 5 'docker compose ps'      # 等全部 healthy / running

# 再起业务
docker compose up -d
```

> `COMPOSE_FILE` 漏了就变成跑开发版 compose：端口全暴露、没有内存上限、没有分档。

首次启动业务服务可能重启 1–2 次：`spring.config.import` 是强制的，Nacos 没完全就绪就会退出重来，
`restart: unless-stopped` 会兜住。

扩展档按需叠加：

```bash
docker compose --profile ext up -d       # + recommendation-service
docker compose --profile stage3 up -d    # + neo4j/seata/xxl-job/postgres + knowledge/order/agent
```

stage3 和默认档同时起会超内存，见附录 A。

---

## 7. Nginx（宿主机）

```bash
apt install -y nginx
rm -f /etc/nginx/sites-enabled/default

mkdir -p /usr/share/nginx/html/landing
cp -r /opt/wisdomhub/wisdomhub-web/app/dist/* /usr/share/nginx/html/
cp -r /opt/wisdomhub/wisdomhub-web/landing/*  /usr/share/nginx/html/landing/

cp /opt/wisdomhub/wisdomhub-web/deploy/nginx.conf /etc/nginx/conf.d/wisdomhub.conf
nginx -t && systemctl reload nginx
```

配置里 upstream 是 `127.0.0.1:9090`，和网关的端口绑定一致。HTTPS：

```bash
apt install -y certbot python3-certbot-nginx
certbot --nginx -d your-domain.com
```

---

## 8. 验收

```bash
# 容器状态与内存
docker compose ps
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}'
free -m

# 网关活着：/internal 对公网恒 403
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:9090/internal/x

# JWT 过滤器生效：无 token 访问受保护路径。网关侧 4xx 是否被包成 200+code 尚未定案，
# 看到 401，或 200 但 body 是 {"code":401,...}，都算通
curl -s -i http://127.0.0.1:9090/api/user/profile | head -5

# 服务注册：隧道开 8849 看实例列表

# ES 插件真加载了：期望切成 spring / bean / 生命周期 / 生命 / 周期，切成单字说明插件没加载
curl -s 'http://127.0.0.1:9200/_analyze' -H 'Content-Type: application/json' \
  -d '{"analyzer":"ik_max_word","text":"Spring Bean 生命周期"}'

# 前端
curl -I http://<服务器IP>/
```

内存判据：**每个 Java 服务 RSS 应在 210–260 MB**。

> 这批 320m 上限是没实测过的唯一一组（构建时没有 jar）。贴上限且 99% 的，先看
> `docker logs <服务>` 是 heap 还是 metaspace 报的 OOM，然后把 `-XX:MaxMetaspaceSize`
> 和 `mem_limit` 一起加 48m。

---

## 9. 日常运维

```bash
docker compose logs -f --tail=200 content      # 单服务日志
docker compose restart user                    # 重启
docker compose down                            # 停（保留卷）
docker compose down -v                         # 停并删所有数据 ← 别手滑

# 备份
docker exec wisdomhub-mysql sh -c \
  'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --all-databases --single-transaction' \
  | gzip > /root/backup-$(date +%F).sql.gz
```

**升级流程**：本地 `mvn package` → `docker compose build` → `docker save` → 传上去
`docker load` → `docker compose up -d`（只会重建有变化的容器）。保留上一个 tar 包用于回滚。

**MySQL 初始化脚本只在数据卷为空时执行**。`docker/mysql/init/` 下新增的 SQL 改了文件名或加了新文件，
对已有卷不会自动生效，需要手工执行。

---

## 10. 故障速查

| 现象 | 大概率原因 | 查法 |
| --- | --- | --- |
| 服务反复重启，日志连不上 Nacos | 配置没导入 / 命名空间 ID 不对 | `docker logs wisdomhub-user` 首屏；Nacos 客户端日志在配置拉取之前，格式和后面不一样 |
| 网关返回 200 + `{"code":500}` | 下游没注册或已挂 | Nacos 实例列表；网关日志 `Unable to find instance for <服务名>` |
| `${infra.mysql-host}` 解析失败 | 只导了服务配置，没导 `wisdomhub-env.yaml` | Nacos 配置列表应有 15 个 DataId |
| ES 起不来 | 宿主没设 `vm.max_map_count` | `sysctl vm.max_map_count`，重跑 host-init.sh |
| 搜索切出来是单字 | IK 插件目录空了 | `docker exec wisdomhub-es ls plugins/ik` |
| 容器反复 OOMKilled | 上限太紧 | `docker inspect <名字> --format '{{.State.OOMKilled}}'` |
| 前端 502 | 网关没起或端口没绑 | `ss -lntp \| grep 9090` |
| MySQL 报 `unknown variable performance_schema` | 版本差异 | 删掉 `docker-compose.prod.yml` 里那一行 |

---

## 附录 A：内存账本

以下为在 Docker Desktop（WSL2）上实测的**稳态常驻**值，与 Linux 服务器的 cgroup anon 口径一致。

| 容器 | 实测 | 上限 | 备注 |
| --- | ---: | ---: | --- |
| elasticsearch | 596 MiB | 720 | 堆仅 192，非堆 194（metaspace 149）+ 线程栈；砍不动 |
| nacos | 620 MiB | 768 | 堆仅 192，其余是 metaspace / code cache / Derby；跑了 3 分钟才稳定 |
| mongodb | 167 MiB | 384 | 缓存下限被 mongod 卡在 0.25GB，上限必须兜住缓存长满 |
| mysql | 131 MiB | 288 | `performance_schema=OFF` + buffer pool 96M |
| rabbitmq | 57 MiB | 160 | management 插件在，Erlang scheduler 压到 1 |
| redis | 7 MiB | 48 | `maxmemory 24mb` + `noeviction`（黑名单 key 不能被淘汰） |
| **中间件小计** | **~1578 MiB** | **2368** | |
| gateway / user / content / notification / search | 预估 ~250 × 5 | 1600 | 未实测 |
| agent-engine | 导入峰值 101 MiB | 384 | |
| neo4j / seata / xxl-job / postgres | — | 384 / 224 / 256 / 96 | stage3 档 |

默认档上限合计 **3968 MiB**，实测常驻约 **3.2 GiB**（含宿主机本身约 350 MiB）。

上限之和略超物理内存是有意的：每个容器都要留余量，靠内核回收 + zram 吸收瞬时尖峰。

### 调参时踩过的坑（别再试一遍）

- **ES 的占用是堆的 3 倍**：`xpack.ml.enabled=false`、关监控已经全部生效，非堆就是这么大。
- **Nacos standalone 只认 `JVM_XMS/XMX/XMN`**，`JVM_MS/MMS` 在启动脚本里只对集群模式生效。
  它没有 `JVM_XSS` 入口，`-Xss512k` 实测只省 10 MiB（线程栈惰性提交），别指望压线程栈。
- **MongoDB 的 `wiredTigerCacheSizeGB` 最小 0.25**，传 0.125 直接 `BadValue` 起不来。
- **容器上限 ≠ `-Xmx` 的倍数**：非堆（metaspace、code cache、线程栈、direct）是独立开销，
  按 `Xmx + 208` 估比较接近实际。

---

## 附录 B：分档说明

| 档位 | 内容 | 额外内存 |
| --- | --- | ---: |
| 默认 | mysql/redis/mongodb/rabbitmq/elasticsearch/nacos + gateway/user/content/notification/search | 3968 MiB 上限 |
| `ext` | recommendation-service | +320 MiB |
| `stage3` | neo4j、seata、xxl-job-admin、postgres + knowledge/order/agent-engine | +1984 MiB |

stage3 里各服务的依赖关系：knowledge 要 neo4j，order 要 seata，agent-engine 要 postgres。
所以它们被打包在同一档，单独起其中几个没有意义。

## 附录 C：常用端口

| 端口 | 服务 | 监听 |
| --- | --- | --- |
| 80 / 443 | nginx | 公网 |
| 9090 | gateway | 127.0.0.1 |
| 3306 / 6379 / 27017 | mysql / redis / mongodb | 127.0.0.1 |
| 9200 / 5672 / 15672 / 61613 | es / rabbitmq / 管理台 / stomp | 127.0.0.1 |
| 8848 / 9848 / 8849 | nacos 服务端 / gRPC / 控制台 | 127.0.0.1 |
| 8080 / 8091 / 5432 / 7474 / 7687 | xxl-job / seata / postgres / neo4j | 127.0.0.1（stage3） |
