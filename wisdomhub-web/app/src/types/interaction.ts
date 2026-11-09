import type { ID, PageQuery } from './common'

/**
 * 互动目标的类型编号，必须与后端 ContentTargetTypeConstants 保持一致（任务表 D2）。
 * interaction-service 只认 (targetType, targetId)，不认 article/video 语义。
 */
export const CONTENT_TARGET_TYPE = {
  ARTICLE: 1,
  ANSWER: 2,
  QUESTION: 3,
  COLUMN: 4,
  USER: 5,
  TAG_PROPOSAL: 6,
  VIDEO: 7,
} as const

export type ContentTargetType = (typeof CONTENT_TARGET_TYPE)[keyof typeof CONTENT_TARGET_TYPE]

/** 评论只开放这几类目标，其余类型后端会返回 400 */
export type CommentTargetType = 1 | 2 | 3 | 7

export interface InteractionTargetRef {
  targetType: ContentTargetType
  targetId: ID
}

export interface InteractionStateVO {
  liked: boolean
  collected: boolean
  likeCount: number
  commentCount: number
  favoriteCount: number
}

export interface InteractionCountVO extends InteractionTargetRef {
  likeCount: number
  commentCount: number
  favoriteCount: number
}

export interface InteractionCountBatchVO {
  items: InteractionCountVO[]
}

/** 批量查询请求体；后端限制一页最多 100 条 */
export interface InteractionCountBatchPayload {
  items: InteractionTargetRef[]
}

/** 我的收藏一页里的一行，内容本体由各内容服务的 brief 接口回填 */
export interface FavoriteItemVO extends InteractionTargetRef {
  createTime: string
}

export interface FavoritePageQuery extends PageQuery {
  /** 不传则不限制类型 */
  targetType?: ContentTargetType
}

export interface CommentVO {
  commentId: ID
  userId: ID
  parentId: ID | null
  content: string
  createTime: string
  replies: CommentVO[]
}

export interface CommentCreatePayload {
  /** 缺省按 1-文章 */
  targetType?: CommentTargetType
  targetId: ID
  /** 回复二级评论时传它的一级父评论 id，服务端会再归一 */
  parentId?: ID | null
  content: string
}

export interface CommentPageQuery extends PageQuery {
  targetType?: CommentTargetType
  targetId: ID
}
