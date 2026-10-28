import type { ID } from './common'

/** 1 点赞 2 评论 3 回答 4 关注 5 专栏购买 6 AI 分析 7 系统 */
export type NotificationType = 1 | 2 | 3 | 4 | 5 | 6 | 7

/** 1 文章 2 回答 3 问题 4 专栏 5 用户 */
export type NotificationTargetType = 1 | 2 | 3 | 4 | 5

export interface NotificationVO {
  notificationId: ID
  type: NotificationType
  targetType: NotificationTargetType
  targetId: ID
  /** 系统通知为 null */
  senderId: ID | null
  /** 只有系统通知会填 */
  content: string | null
  read: boolean
  createTime: string
}

export interface NotificationPushVO {
  notification: NotificationVO
  unreadCount: number
}
