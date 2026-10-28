import { defineStore } from 'pinia'
import { ref } from 'vue'

import type { RequestOptions } from '@/api/http'
import {
  list as fetchNotifications,
  markAllRead as fetchMarkAllRead,
  markRead as fetchMarkRead,
  unreadCount as fetchUnreadCount,
} from '@/api/modules/notification'
import type { ID } from '@/types/common'
import type { NotificationPushVO, NotificationVO } from '@/types/notification'

export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const items = ref<NotificationVO[]>([])
  const page = ref(1)
  const pageSize = ref(10)
  const total = ref(0)
  const loading = ref(false)
  const unreadLoading = ref(false)

  async function loadUnreadCount(
    options: RequestOptions = { skipErrorToast: true },
  ): Promise<void> {
    if (unreadLoading.value) return
    unreadLoading.value = true

    try {
      unreadCount.value = await fetchUnreadCount(options)
    } catch {
      // 轮询失败不打扰用户
    } finally {
      unreadLoading.value = false
    }
  }

  async function loadList(targetPage = page.value, targetPageSize = pageSize.value): Promise<void> {
    if (loading.value) return
    loading.value = true

    try {
      const result = await fetchNotifications({
        page: targetPage,
        pageSize: targetPageSize,
      })
      items.value = result.items
      total.value = result.total
      page.value = result.page
      pageSize.value = result.pageSize
    } finally {
      loading.value = false
    }
  }

  async function markRead(notificationId: ID): Promise<void> {
    const target = items.value.find((item) => item.notificationId === notificationId)
    if (target?.read) return

    await fetchMarkRead(notificationId)
    if (target) target.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    void loadUnreadCount()
  }

  async function markAllRead(): Promise<void> {
    await fetchMarkAllRead()
    items.value.forEach((item) => {
      item.read = true
    })
    unreadCount.value = 0
  }

  /** WebSocket 推送入口：去重后放到列表头，并同步未读数 */
  function receivePush(payload: NotificationPushVO): void {
    unreadCount.value = payload.unreadCount

    const index = items.value.findIndex(
      (item) => item.notificationId === payload.notification.notificationId,
    )
    if (index >= 0) {
      items.value[index] = payload.notification
      return
    }

    if (page.value === 1) {
      items.value = [payload.notification, ...items.value].slice(0, pageSize.value)
      total.value += 1
    }
  }

  function reset(): void {
    unreadCount.value = 0
    items.value = []
    page.value = 1
    pageSize.value = 10
    total.value = 0
    loading.value = false
    unreadLoading.value = false
  }

  return {
    unreadCount,
    items,
    page,
    pageSize,
    total,
    loading,
    unreadLoading,
    loadUnreadCount,
    loadList,
    markRead,
    markAllRead,
    receivePush,
    reset,
  }
})
