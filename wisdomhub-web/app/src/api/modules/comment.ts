import { del, get, post, type RequestOptions } from '@/api/http'
import type { ID, PageResponse } from '@/types/common'
import type { CommentCreatePayload, CommentPageQuery, CommentVO } from '@/types/content'

export function create(payload: CommentCreatePayload, options: RequestOptions = {}): Promise<ID> {
  return post<ID>('/api/content/comment', payload, options)
}

export function list(
  params: CommentPageQuery,
  options: RequestOptions = {},
): Promise<PageResponse<CommentVO>> {
  return get<PageResponse<CommentVO>>('/api/content/comment', params, options)
}

export function remove(commentId: ID, options: RequestOptions = {}): Promise<void> {
  return del<void>(`/api/content/comment/${commentId}`, options)
}
