import { del, get, post, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type { AnswerCreatePayload, AnswerVO } from '@/types/qa'

export function create(
  questionId: ID,
  payload: AnswerCreatePayload,
  options: RequestOptions = {},
): Promise<ID> {
  return post<ID>(`/api/content/question/${questionId}/answer`, payload, options)
}

export function list(
  questionId: ID,
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<AnswerVO>> {
  return get<PageResponse<AnswerVO>>(`/api/content/question/${questionId}/answer`, params, options)
}

export function remove(answerId: ID, options: RequestOptions = {}): Promise<void> {
  return del<void>(`/api/content/answer/${answerId}`, options)
}
