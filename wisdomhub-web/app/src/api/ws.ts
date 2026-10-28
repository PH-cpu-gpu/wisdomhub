import { Client, ReconnectionTimeMode, type IMessage } from '@stomp/stompjs'

import { getAccessToken } from '@/api/auth-token'
import type { NotificationPushVO } from '@/types/notification'

const SUBSCRIPTION_PATH = '/user/queue/notification'
const INITIAL_RECONNECT_DELAY = 1_000
const MAX_RECONNECT_DELAY = 30_000
/** Mock 模式下没有通知服务；置 1 时允许连到测试用 WebSocket */
const MOCK_WS_FLAG = 'wisdomhub:mock.ws'

export interface NotificationSocketHandlers {
  onMessage: (payload: NotificationPushVO) => void
  onConnected?: (reconnected: boolean) => void
  onDisconnected?: () => void
}

let client: Client | null = null
let handlers: NotificationSocketHandlers | null = null
let connectedOnce = false

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
    onWebSocketClose: () => {
      handlers?.onDisconnected?.()
    },
  })

  // refreshToken 不轮换，accessToken 可能已续期；每次握手前取最新 token
  next.beforeConnect = () => {
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
    client = buildClient()
    client.activate()
  },

  stop(): void {
    handlers = null
    connectedOnce = false
    const current = client
    client = null
    if (current) void current.deactivate()
  },

  isActive(): boolean {
    return client !== null
  },
}
