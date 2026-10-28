import { del, get, post, put, type RequestOptions } from '@/api/http'
import type { ID, PageQuery, PageResponse } from '@/types/common'
import type {
  ArticleListItemVO,
  ColumnCreatePayload,
  ColumnVO,
  PurchasedColumnVO,
} from '@/types/content'

export function create(payload: ColumnCreatePayload, options: RequestOptions = {}): Promise<ID> {
  return post<ID>('/api/content/column', payload, options)
}

export function listMine(
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<ColumnVO>> {
  return get<PageResponse<ColumnVO>>('/api/content/column/mine', params, options)
}

export function listPurchased(
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<PurchasedColumnVO>> {
  return get<PageResponse<PurchasedColumnVO>>('/api/content/column/purchased', params, options)
}

export function detail(columnId: ID, options: RequestOptions = {}): Promise<ColumnVO> {
  return get<ColumnVO>(`/api/content/column/${columnId}`, undefined, options)
}

export function listArticles(
  columnId: ID,
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<ArticleListItemVO>> {
  return get<PageResponse<ArticleListItemVO>>(
    `/api/content/column/${columnId}/article`,
    params,
    options,
  )
}

export function addArticle(
  columnId: ID,
  articleId: ID,
  options: RequestOptions = {},
): Promise<number> {
  return put<number>(`/api/content/column/${columnId}/article/${articleId}`, undefined, options)
}

export function removeArticle(
  columnId: ID,
  articleId: ID,
  options: RequestOptions = {},
): Promise<number> {
  return del<number>(`/api/content/column/${columnId}/article/${articleId}`, options)
}

export function remove(columnId: ID, options: RequestOptions = {}): Promise<void> {
  return del<void>(`/api/content/column/${columnId}`, options)
}
