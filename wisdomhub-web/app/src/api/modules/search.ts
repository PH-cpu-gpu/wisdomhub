import { get, type RequestOptions } from '@/api/http'
import type { PageResponse } from '@/types/common'
import type {
  ArticleSearchQuery,
  ArticleSearchVO,
  HotWordQuery,
  HotWordVO,
  QuestionSearchQuery,
  QuestionSearchVO,
  SuggestQuery,
  TagSearchQuery,
  TagSearchVO,
  UserSearchQuery,
  UserSearchVO,
} from '@/types/search'

export function articles(
  params: ArticleSearchQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<ArticleSearchVO>> {
  return get<PageResponse<ArticleSearchVO>>('/api/search/article', params, options)
}

export function questions(
  params: QuestionSearchQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<QuestionSearchVO>> {
  return get<PageResponse<QuestionSearchVO>>('/api/search/question', params, options)
}

export function users(
  params: UserSearchQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<UserSearchVO>> {
  return get<PageResponse<UserSearchVO>>('/api/search/user', params, options)
}

export function tags(
  params: TagSearchQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<TagSearchVO>> {
  return get<PageResponse<TagSearchVO>>('/api/search/tag', params, options)
}

export function suggest(
  params: SuggestQuery = {},
  options: RequestOptions = {},
): Promise<string[]> {
  return get<string[]>('/api/search/suggest', params, options)
}

export function hotWords(
  params: HotWordQuery = {},
  options: RequestOptions = {},
): Promise<HotWordVO[]> {
  return get<HotWordVO[]>('/api/search/hotword', params, options)
}
