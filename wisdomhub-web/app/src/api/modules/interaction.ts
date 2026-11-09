import { del, get, post, put, type RequestOptions } from '@/api/http'
import type { ID, PageResponse } from '@/types/common'
import type {
  CommentCreatePayload,
  CommentPageQuery,
  CommentVO,
  ContentTargetType,
  FavoriteItemVO,
  FavoritePageQuery,
  InteractionCountBatchVO,
  InteractionStateVO,
  InteractionTargetRef,
} from '@/types/interaction'

const BASE = '/api/interaction'

/**
 * 当前用户对单个目标的互动状态 + 计数。
 * 游客可调：未登录时 liked/collected 恒为 false，计数照常返回。
 */
export function getState(
  targetType: ContentTargetType,
  targetId: ID,
  options: RequestOptions = {},
): Promise<InteractionStateVO> {
  return get<InteractionStateVO>(`${BASE}/state`, { targetType, targetId }, options)
}

/** 一页一次批量取计数，不返回当前用户的 liked/collected；后端限制最多 100 条 */
export function batchCounts(
  targets: InteractionTargetRef[],
  options: RequestOptions = {},
): Promise<InteractionCountBatchVO> {
  return post<InteractionCountBatchVO>(`${BASE}/counts`, { items: targets }, options)
}

/** 当前用户收藏的一页 (targetType,targetId)，按收藏时间倒序；内容本体另调 brief 回填 */
export function pageFavorites(
  params: FavoritePageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<FavoriteItemVO>> {
  return get<PageResponse<FavoriteItemVO>>(`${BASE}/favorite`, params, options)
}

/** 点赞（幂等），返回最新点赞数 */
export function like(
  targetType: ContentTargetType,
  targetId: ID,
  options: RequestOptions = {},
): Promise<number> {
  return put<number>(`${BASE}/like`, undefined, { ...options, params: { targetType, targetId } })
}

/** 取消点赞（幂等），返回最新点赞数 */
export function unlike(
  targetType: ContentTargetType,
  targetId: ID,
  options: RequestOptions = {},
): Promise<number> {
  return del<number>(`${BASE}/like`, { ...options, params: { targetType, targetId } })
}

/** 收藏（幂等），返回最新收藏数 */
export function collect(
  targetType: ContentTargetType,
  targetId: ID,
  options: RequestOptions = {},
): Promise<number> {
  return put<number>(`${BASE}/favorite`, undefined, { ...options, params: { targetType, targetId } })
}

/** 取消收藏（幂等），返回最新收藏数 */
export function uncollect(
  targetType: ContentTargetType,
  targetId: ID,
  options: RequestOptions = {},
): Promise<number> {
  return del<number>(`${BASE}/favorite`, { ...options, params: { targetType, targetId } })
}

/** 发表评论；回复二级评论时 parentId 传它的一级父评论 id */
export function createComment(
  payload: CommentCreatePayload,
  options: RequestOptions = {},
): Promise<ID> {
  return post<ID>(`${BASE}/comment`, payload, options)
}

/** 评论分页：按一级评论分页，二级回复挂在 replies 上 */
export function listComments(
  params: CommentPageQuery,
  options: RequestOptions = {},
): Promise<PageResponse<CommentVO>> {
  return get<PageResponse<CommentVO>>(`${BASE}/comment`, params, options)
}

/** 删除评论；删一级评论时后端连带软删它的二级回复 */
export function removeComment(commentId: ID, options: RequestOptions = {}): Promise<void> {
  return del<void>(`${BASE}/comment/${commentId}`, options)
}
