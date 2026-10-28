import { get, put, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type { NotificationVO } from '@/types/notification'

export function unreadCount(options: RequestOptions = {}): Promise<number> {
  return get<number>('/api/notification/unread/count', undefined, options)
}

export function list(params: PageQuery = {}): Promise<PageResponse<NotificationVO>> {
  return get<PageResponse<NotificationVO>>('/api/notification/list', params)
}

export function markRead(notificationId: ID): Promise<void> {
  return put<void>(`/api/notification/${notificationId}/read`)
}

export function markAllRead(): Promise<void> {
  return put<void>('/api/notification/read-all')
}
