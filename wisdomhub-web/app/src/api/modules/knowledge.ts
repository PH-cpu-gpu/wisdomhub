import { get, type RequestOptions } from '@/api/http'
import type { ID } from '@/types/common'
import type { GraphVO, PrerequisiteTreeVO, TagDetailVO, TagRelationsVO } from '@/types/knowledge'

export function tagDetail(tagId: ID, options: RequestOptions = {}): Promise<TagDetailVO> {
  return get<TagDetailVO>(`/api/knowledge/tag/${tagId}`, undefined, options)
}

export function tagRelations(tagId: ID, options: RequestOptions = {}): Promise<TagRelationsVO> {
  return get<TagRelationsVO>(`/api/knowledge/tag/${tagId}/related`, undefined, options)
}

export function prerequisites(
  tagId: ID,
  maxDepth = 5,
  options: RequestOptions = {},
): Promise<PrerequisiteTreeVO> {
  return get<PrerequisiteTreeVO>(`/api/knowledge/tag/${tagId}/prerequisites`, { maxDepth }, options)
}

export function graph(options: RequestOptions = {}): Promise<GraphVO> {
  return get<GraphVO>('/api/knowledge/graph', undefined, options)
}

export function neighborhood(
  tagId: ID,
  params: { depth?: number; articleLimit?: number } = {},
  options: RequestOptions = {},
): Promise<GraphVO> {
  return get<GraphVO>(`/api/knowledge/graph/tag/${tagId}`, params, options)
}
