import { get, post, type RequestOptions } from '@/api/http'
import type { ID, PageResponse } from '@/types/common'
import type { TagProposalQuery, TagProposalVO } from '@/types/tag-proposal'

/** 提案分页；仅管理员可访问，非管理员返回 403 */
export function listProposals(
  query: TagProposalQuery,
  options: RequestOptions = {},
): Promise<PageResponse<TagProposalVO>> {
  return get<PageResponse<TagProposalVO>>('/api/content/tag-proposal', query, options)
}

export function getProposal(id: ID, options: RequestOptions = {}): Promise<TagProposalVO> {
  return get<TagProposalVO>(`/api/content/tag-proposal/${encodeURIComponent(id)}`, undefined, options)
}

/** 通过后后端会建标签/关系并发 MQ 投影；并发时返回 409 */
export function approveProposal(id: ID, options: RequestOptions = {}): Promise<void> {
  return post<void>(`/api/content/tag-proposal/${encodeURIComponent(id)}/approve`, undefined, options)
}

export function rejectProposal(id: ID, options: RequestOptions = {}): Promise<void> {
  return post<void>(`/api/content/tag-proposal/${encodeURIComponent(id)}/reject`, undefined, options)
}
