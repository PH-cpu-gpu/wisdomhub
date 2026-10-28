import { del, get, post, put, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type { DraftBriefVO, DraftPayload } from '@/types/content'

export function create(payload: DraftPayload, options: RequestOptions = {}): Promise<ID> {
  return post<ID>('/api/content/draft', payload, options)
}

export function update(
  articleId: ID,
  payload: DraftPayload,
  options: RequestOptions = {},
): Promise<ID> {
  return put<ID>(`/api/content/draft/${articleId}`, payload, options)
}

export function list(
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<DraftBriefVO>> {
  return get<PageResponse<DraftBriefVO>>('/api/content/draft', params, options)
}

export function remove(articleId: ID, options: RequestOptions = {}): Promise<void> {
  return del<void>(`/api/content/draft/${articleId}`, options)
}
