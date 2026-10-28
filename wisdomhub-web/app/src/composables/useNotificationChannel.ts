import { onBeforeUnmount, ref, watch } from 'vue'

import { notificationSocket } from '@/api/ws'
import { useAuthStore, useNotificationStore } from '@/stores'
import type { NotificationPushVO } from '@/types/notification'

const POLL_INTERVAL = 60_000

/**
 * 通知通道：登录后连 STOMP 收实时推送；未连上时用 60s 轮询兜底。
 * 断开后进入退避重连（由 STOMP 客户端负责），期间恢复轮询。
 */
export function useNotificationChannel() {
  const auth = useAuthStore()
  const notification = useNotificationStore()
  const connected = ref(false)

  let pollTimer: number | null = null
  let active = false

  function stopPolling(): void {
    if (pollTimer !== null) {
      window.clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function startPolling(): void {
    stopPolling()
    pollTimer = window.setInterval(() => {
      if (auth.isLoggedIn() && !connected.value) void notification.loadUnreadCount()
    }, POLL_INTERVAL)
  }

  function handleConnected(reconnected: boolean): void {
    connected.value = true
    stopPolling()
    void notification.loadUnreadCount()
    if (reconnected) {
      void notification.loadList(notification.page).catch(() => {})
    }
  }

  function handleDisconnected(): void {
    connected.value = false
    if (!active) return
    startPolling()
    void notification.loadUnreadCount()
  }

  function handleMessage(payload: NotificationPushVO): void {
    notification.receivePush(payload)
  }

  function start(): void {
    if (active) return
    active = true
    notificationSocket.start({
      onMessage: handleMessage,
      onConnected: handleConnected,
      onDisconnected: handleDisconnected,
    })
    void notification.loadUnreadCount()
    startPolling()
  }

  function stop(): void {
    active = false
    connected.value = false
    stopPolling()
    notificationSocket.stop()
    notification.reset()
  }

  watch(
    () => auth.isLoggedIn(),
    (loggedIn) => {
      if (loggedIn) start()
      else stop()
    },
    { immediate: true },
  )

  onBeforeUnmount(() => {
    if (active) stop()
  })

  return { connected }
}
