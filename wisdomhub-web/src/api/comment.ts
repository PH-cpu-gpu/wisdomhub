import { del, get, post } from './http'
import type { PageResponse } from './types'

/**
 * 评论（B1.25）。
 *
 * 三条形态要记住：
 *   - **分页按「一级评论」分页，每条内联它的二级回复**（一次查询回填，无 N+1）。
 *     所以 `total` 是**一级评论数**，与文章上的 `commentCount`（含回复）刻意不等 ——
 *     分页器用 `total`，页面上的「N 条评论」用 `commentCount`。这不是 bug。
 *   - **回复二级评论时 parentId 传二级评论的 id 就行**，服务端会归一到一级父评论；
 *     「回复谁」不保留（表里没这个列），所以 UI 渲染不了「回复 @某人」。
 *   - 读路径不校验文章（空列表就是 200 + total:0），写路径才校验。
 */

/** 1-文章 2-回答。B1.28 起回答也能评论 */
export const CommentTargetType = {
  ARTICLE: 1,
  ANSWER: 2,
} as const

export interface CommentItem {
  commentId: string
  userId: string
  /** 一级评论为 null；二级回复指向它的一级父评论 */
  parentId: string | null
  content: string
  createTime: string
  replies: CommentItem[]
}

export interface CommentCreatePayload {
  targetType?: number
  targetId: string
  /** 回复谁就传谁的 commentId（二级评论也行，服务端会归一） */
  parentId?: string
  content: string
}

export function createComment(payload: CommentCreatePayload) {
  return post<string>('/content/comment', payload)
}

export function pageComments(targetId: string, targetType: number, page = 1, pageSize = 10) {
  return get<PageResponse<CommentItem>>('/content/comment', {
    targetType,
    targetId,
    page,
    pageSize,
  })
}

/** 删除评论：仅作者本人；删一级评论会级联删它的回复 */
export function deleteComment(commentId: string) {
  return del<void>(`/content/comment/${commentId}`)
}
