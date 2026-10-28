import { get, type RequestOptions } from '@/api/http'
import type { HotTagVO, TagVO } from '@/types/content'

export function listAll(): Promise<TagVO[]> {
  return get<TagVO[]>('/api/content/tag')
}

export function listHot(limit?: number, options: RequestOptions = {}): Promise<HotTagVO[]> {
  return get<HotTagVO[]>('/api/content/tag/hot', { limit }, options)
}
