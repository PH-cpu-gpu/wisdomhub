import { get, type RequestOptions } from '@/api/http'
import type { ID } from '@/types/common'
import type { RecommendationItemVO } from '@/types/recommendation'

export function home(
  limit?: number,
  options: RequestOptions = {},
): Promise<RecommendationItemVO[]> {
  return get<RecommendationItemVO[]>('/api/recommendation/home', { limit }, options)
}

export function related(
  articleId: ID,
  limit?: number,
  options: RequestOptions = {},
): Promise<RecommendationItemVO[]> {
  return get<RecommendationItemVO[]>(`/api/recommendation/related/${articleId}`, { limit }, options)
}

export function hot(
  params: { tagId?: ID; limit?: number } = {},
  options: RequestOptions = {},
): Promise<RecommendationItemVO[]> {
  return get<RecommendationItemVO[]>('/api/recommendation/hot', params, options)
}
