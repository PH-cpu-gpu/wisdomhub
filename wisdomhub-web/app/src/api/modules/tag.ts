import { del, get, post, put, type RequestOptions } from '@/api/http'
import type { ID } from '@/types/common'
import type { HotTagVO, TagAliasVO, TagPayload, TagVO } from '@/types/content'

export function listAll(options: RequestOptions = {}): Promise<TagVO[]> {
  return get<TagVO[]>('/api/content/tag', undefined, options)
}

export function listHot(limit?: number, options: RequestOptions = {}): Promise<HotTagVO[]> {
  return get<HotTagVO[]>('/api/content/tag/hot', { limit }, options)
}

/** 以下写接口都要求管理员；标签名与别名会按归一化规则查重（大小写 / 空格 / 分隔符忽略） */
export function createTag(payload: TagPayload, options: RequestOptions = {}): Promise<ID> {
  return post<ID>('/api/content/tag', payload, options)
}

export function updateTag(
  tagId: ID,
  payload: TagPayload,
  options: RequestOptions = {},
): Promise<void> {
  return put<void>(`/api/content/tag/${encodeURIComponent(tagId)}`, payload, options)
}

/** 删除标签会级联清别名、关系与文章关联，并由 MQ 投影到图谱 */
export function removeTag(tagId: ID, options: RequestOptions = {}): Promise<void> {
  return del<void>(`/api/content/tag/${encodeURIComponent(tagId)}`, options)
}

export function listAliases(tagId: ID, options: RequestOptions = {}): Promise<TagAliasVO[]> {
  return get<TagAliasVO[]>(
    `/api/content/tag/${encodeURIComponent(tagId)}/alias`,
    undefined,
    options,
  )
}

export function addAlias(
  tagId: ID,
  alias: string,
  options: RequestOptions = {},
): Promise<TagAliasVO> {
  return post<TagAliasVO>(
    `/api/content/tag/${encodeURIComponent(tagId)}/alias`,
    { alias },
    options,
  )
}

export function removeAlias(
  tagId: ID,
  aliasId: ID,
  options: RequestOptions = {},
): Promise<void> {
  return del<void>(
    `/api/content/tag/${encodeURIComponent(tagId)}/alias/${encodeURIComponent(aliasId)}`,
    options,
  )
}
