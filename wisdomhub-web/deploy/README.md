# WisdomHub 前端构建与部署

## 1. 构建

要求 Node >= 20.19（本机用 Node 22 + corepack pnpm）。

```bash
cd wisdomhub-web/app
corepack pnpm install --frozen-lockfile
corepack pnpm typecheck
corepack pnpm build
```

产物在 `wisdomhub-web/app/dist/`。落地页是独立的静态目录 `wisdomhub-web/landing/`，
不进入 Vite 构建，发布时与 `dist` 一起拷贝。生产环境使用 `.env.production`：

```text
VITE_API_BASE_URL=            # 留空，走同源 /api
VITE_WS_URL=/api/notification/ws
VITE_USE_MOCK=false
```

前端所有 HTTP 请求走相对 `/api`，WebSocket 按当前页面协议推导 `ws://` / `wss://`，不写死网关地址。

## 2. Nginx

把 `dist/` 拷到服务器（示例用 `/usr/share/nginx/html`），把 `landing/` 拷到
`/usr/share/nginx/html/landing/`，把 `nginx.conf` 放到
`/etc/nginx/conf.d/wisdomhub.conf`；如放在主配置里，把 `server { ... }` 放入 `http { ... }`。

```text
# /usr/share/nginx/html/index.html       → SPA（/home、/articles、/login…）
# /usr/share/nginx/html/landing/index.html → 落地页（/）
root /usr/share/nginx/html;   # 改成 dist 的实际路径
upstream wisdomhub_gateway { server 127.0.0.1:9090; }
```

检查并重载：

```bash
nginx -t
nginx -s reload
```

配置覆盖：

- `location = /`：返回 `landing/index.html`，即默认入口落地页；
- `/styles.css`、`/main.js`：落地页静态资源；
- `location /`：`try_files $uri $uri/ /index.html;`，支持 SPA（`/home` 及所有应用路由）刷新；
- `/api/`：反代到网关 `9090`；
- `/api/notification/ws`：WebSocket `Upgrade`，超时 1 小时，关闭缓冲；
- `/api/agent/`：SSE 关闭 `proxy_buffering` / `proxy_cache`，超时 1 小时，禁用上游压缩；
- `/assets/`：带 hash 的资源 `immutable` 一年缓存；SPA `index.html` 不缓存；
- `gzip`：HTML / JS / CSS / JSON / SVG / 字体。

### Docker 里跑 Nginx

Nginx 在容器内时 `127.0.0.1` 指向容器本身，需要改成宿主机网关。先准备一个合并目录：

```bash
mkdir -p /path/to/web-root/landing
cp -r wisdomhub-web/app/dist/* /path/to/web-root/
cp -r wisdomhub-web/landing/* /path/to/web-root/landing/
# 把 nginx.conf 里的 127.0.0.1:9090 改成 host.docker.internal:9090
```

```bash
docker run -d --name wisdomhub-web \
  -p 8080:80 \
  -v /path/to/web-root:/usr/share/nginx/html:ro \
  -v /path/to/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
  --add-host host.docker.internal:host-gateway \
  nginx:1.27-alpine
```

## 3. HTTPS / WSS

生产建议在 Nginx 上终止 TLS，并额外监听 443。前端会自动把 WS 地址从 `ws://` 切到
`wss://`（`api/ws.ts` 按 `location.protocol` 推导），无需改前端环境变量。

## 4. 部署前核对

1. **网关可选登录白名单**：`/api/content/article/**`、`/api/content/question/**`、
   `/api/content/tag/**`、`/api/content/column/**`、`/api/knowledge/**`、
   `/api/user/profile/**` 是否在 Nacos `gateway-service.yaml` 的 `optional-auth-paths`；
   本地实测 `/api/knowledge/**` 游客仍返回 401，需要补。
2. **WebSocket 路由**：Nacos 是否有 `/api/notification/ws` → `lb:ws://notification-service`
   且 `order: -1`；`allowed-origins` 是否包含正式域名。
3. **AI SSE**：网关到 `agent-engine` 的路由也不要缓冲；LLM Key 与 Python 服务就绪。

## 5. 冒烟命令

```bash
# 默认入口：应返回落地页（正文包含「把散落的」）
curl http://host/ | head -20

# 主页面 / SPA fallback：应返回 SPA index.html
curl -I http://host/home
curl -I http://host/articles

# 落地页静态资源
curl -I http://host/styles.css
curl -I http://host/main.js

# SPA 入口不缓存
curl -I http://host/index.html

# 静态资源 gzip + 长缓存
curl -I -H 'Accept-Encoding: gzip' http://host/assets/<asset>.js

# 网关联通
curl http://host/api/content/tag

# WebSocket 升级：期望 101 Switching Protocols
curl -i -N \
  -H 'Connection: Upgrade' \
  -H 'Upgrade: websocket' \
  -H 'Sec-WebSocket-Version: 13' \
  -H 'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  http://host/api/notification/ws
```

SSE 需要登录态；用浏览器登录后进入 `/ai` 发一条消息，确认回答是逐字出现而不是整段
一次性返回，即说明 `/api/agent/` 没有缓冲。
