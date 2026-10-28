import { del, get, post, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type {
  ArticleBriefVO,
  ArticleDetailVO,
  ArticleListItemVO,
  ArticleListQuery,
  ArticlePublishPayload,
  ArticleSourceVO,
} from '@/types/content'

export function publish(payload: ArticlePublishPayload, options: RequestOptions = {}): Promise<ID> {
  return post<ID>('/api/content/article', payload, options)
}

export function remove(articleId: ID, options: RequestOptions = {}): Promise<void> {
  return del<void>(`/api/content/article/${articleId}`, options)
}

export function list(
  params: ArticleListQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<ArticleListItemVO>> {
  return get<PageResponse<ArticleListItemVO>>('/api/content/article', params, options)
}

export function detail(articleId: ID, options: RequestOptions = {}): Promise<ArticleDetailVO> {
  return get<ArticleDetailVO>(`/api/content/article/${articleId}`, undefined, options)
}

export function listFavorites(
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<ArticleListItemVO>> {
  return get<PageResponse<ArticleListItemVO>>('/api/content/article/collect', params, options)
}

export function listBrief(articleIds: ID[]): Promise<ArticleBriefVO[]> {
  return get<ArticleBriefVO[]>('/api/content/article/brief', { articleIds })
}

export function source(articleId: ID, options: RequestOptions = {}): Promise<ArticleSourceVO> {
  return get<ArticleSourceVO>(`/api/content/article/${articleId}/source`, undefined, options)
}
