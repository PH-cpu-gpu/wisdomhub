import { del, get, post, put, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type {
  QuestionCreatePayload,
  QuestionDetailVO,
  QuestionListItemVO,
  QuestionListQuery,
} from '@/types/qa'

export function create(payload: QuestionCreatePayload, options: RequestOptions = {}): Promise<ID> {
  return post<ID>('/api/content/question', payload, options)
}

export function list(
  params: QuestionListQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<QuestionListItemVO>> {
  return get<PageResponse<QuestionListItemVO>>('/api/content/question', params, options)
}

export function listFavorites(
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<QuestionListItemVO>> {
  return get<PageResponse<QuestionListItemVO>>('/api/content/question/favorite', params, options)
}

export function detail(questionId: ID, options: RequestOptions = {}): Promise<QuestionDetailVO> {
  return get<QuestionDetailVO>(`/api/content/question/${questionId}`, undefined, options)
}

export function remove(questionId: ID): Promise<void> {
  return del<void>(`/api/content/question/${questionId}`)
}

export function favorite(questionId: ID, options: RequestOptions = {}): Promise<number> {
  return put<number>(`/api/content/question/${questionId}/favorite`, undefined, options)
}

export function unfavorite(questionId: ID, options: RequestOptions = {}): Promise<number> {
  return del<number>(`/api/content/question/${questionId}/favorite`, options)
}

export function accept(questionId: ID, answerId: ID, options: RequestOptions = {}): Promise<void> {
  return put<void>(`/api/content/question/${questionId}/accept/${answerId}`, undefined, options)
}

export function cancelAccept(
  questionId: ID,
  answerId: ID,
  options: RequestOptions = {},
): Promise<void> {
  return del<void>(`/api/content/question/${questionId}/accept/${answerId}`, options)
}
