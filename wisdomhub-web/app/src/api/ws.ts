import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'

import { getAccessToken } from '@/api/auth-token'
import { refreshAccessToken } from '@/api/http'
import type { NotificationPushVO } from '@/types/notification'

const SUBSCRIPTION_PATH = '/user/queue/notification'
const INITIAL_RECONNECT_DELAY = 1_000
const MAX_RECONNECT_DELAY = 30_000
/** 与服务端 StompSessionRegistry 的关闭码保持一致 */
const TOKEN_EXPIRED_CLOSE_CODE = 4001
const SESSION_REVOKED_CLOSE_CODE = 4002
/** Mock 模式下没有通知服务；置 1 时允许连到测试用 WebSocket */
const MOCK_WS_FLAG = 'wisdomhub:mock.ws'

export interface NotificationSocketHandlers {
  onMessage: (payload: NotificationPushVO) => void
  onConnected?: (reconnected: boolean) => void
  onDisconnected?: () => void
  /** 服务端判定会话已被登出（踢线），本地登录态需要清理 */
  onSessionRevoked?: () => void
}

let client: Client | null = null
let handlers: NotificationSocketHandlers | null = null
let connectedOnce = false
/** 服务端以 4001 关闭连接时置位，下次握手前先续期 */
let needsTokenRefresh = false

async function deactivateClient(): Promise<void> {
  const current = client
  client = null
  connectedOnce = false
  needsTokenRefresh = false
  if (current) await current.deactivate()
}

/** 生产环境 VITE_WS_URL 是相对路径，按当前页面协议推导 ws / wss */
function resolveSocketUrl(): string {
  const configured = import.meta.env.VITE_WS_URL || '/api/notification/ws'
  if (/^wss?:\/\//i.test(configured)) return configured
  if (/^https?:\/\//i.test(configured)) return configured.replace(/^http/i, 'ws')
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const path = configured.startsWith('/') ? configured : `/${configured}`
  return `${protocol}//${window.location.host}${path}`
}

function isSocketEnabled(): boolean {
  if (import.meta.env.VITE_USE_MOCK !== 'true') return true
  return localStorage.getItem(MOCK_WS_FLAG) === '1'
}

function parsePush(message: IMessage): NotificationPushVO | null {
  try {
    const data = JSON.parse(message.body) as Partial<NotificationPushVO>
    const notification = data.notification
    if (!notification?.notificationId) return null
    if (typeof data.unreadCount !== 'number') return null
    return data as NotificationPushVO
  } catch {
    return null
  }
}

function buildClient(): Client {
  const next = new Client({
    brokerURL: resolveSocketUrl(),
    connectHeaders: {
      Authorization: `Bearer ${getAccessToken() ?? ''}`,
    },
    reconnectDelay: INITIAL_RECONNECT_DELAY,
    maxReconnectDelay: MAX_RECONNECT_DELAY,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    debug: () => {},
    onConnect: () => {
      const reconnected = connectedOnce
      connectedOnce = true
      next.subscribe(SUBSCRIPTION_PATH, (message: IMessage) => {
        const payload = parsePush(message)
        if (payload) handlers?.onMessage(payload)
      })
      handlers?.onConnected?.(reconnected)
    },
    onWebSocketClose: (event) => {
      if (event.code === SESSION_REVOKED_CLOSE_CODE) {
        void deactivateClient()
        handlers?.onDisconnected?.()
        handlers?.onSessionRevoked?.()
        return
      }
      // accessToken 到期被服务端断开：重连前先续期，否则会一直拿旧 token 被拒
      if (event.code === TOKEN_EXPIRED_CLOSE_CODE) needsTokenRefresh = true
      handlers?.onDisconnected?.()
    },
    onStompError: () => {
      // CONNECT 被拒；本地已无 token（已登出）就不再退避重连
      if (!getAccessToken()) {
        void deactivateClient()
        return
      }
      needsTokenRefresh = true
    },
  })

  // accessToken 可能已续期；每次握手前取最新 token
  next.beforeConnect = async () => {
    if (needsTokenRefresh) {
      needsTokenRefresh = false
      try {
        await refreshAccessToken()
      } catch {
        await deactivateClient()
        return
      }
    }
    next.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ''}` }
  }

  return next
}

/** 通知 STOMP 客户端：CONNECT 带 Bearer，只订阅 /user/queue/notification */
export const notificationSocket = {
  start(nextHandlers: NotificationSocketHandlers): void {
    handlers = nextHandlers
    if (!isSocketEnabled()) return
    if (!getAccessToken()) return
    if (client) return

    connectedOnce = false
    needsTokenRefresh = false
    client = buildClient()
    client.activate()
  },

  stop(): void {
    handlers = null
    connectedOnce = false
    needsTokenRefresh = false
    const current = client
    client = null
    if (current) void current.deactivate()
  },

  isActive(): boolean {
    return client !== null
  },
}
