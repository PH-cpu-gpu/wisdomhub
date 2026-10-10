import { del, get, post, put } from './http'
import type { PageResponse } from './types'

/**
 * 草稿（B1.20）。
 *
 * 契约里最容易踩的一条：**草稿就是 `article` 表里 `state=1` 的一行 + 同一份正文**，
 * 没有独立的草稿表/collection（原设计的 `article_draft` 在 B1.20 就取消了）。
 * 所以「新建草稿」返回的 `articleId` 就是将来这篇文章的 ID，发布时带回去即可原地转发布。
 */
export interface DraftBrief {
  articleId: string
  title: string
  updateTime: string
}

export interface DraftPayload {
  title: string
  markdown: string
}

/** 新建草稿，返回 articleId。第一次自动保存时调 */
export function createDraft(payload: DraftPayload) {
  return post<string>('/content/draft', payload)
}

/** 后续自动保存。⚠️ 只接受 state=1，已发布的文章改这个会给 409 */
export function updateDraft(articleId: string, payload: DraftPayload) {
  return put<string>(`/content/draft/${articleId}`, payload)
}

/** 草稿箱分页，按最后编辑时间倒序 */
export function listDrafts(page = 1, pageSize = 10) {
  return get<PageResponse<DraftBrief>>('/content/draft', { page, pageSize })
}

/** 丢弃草稿（只接受 state=1） */
export function deleteDraft(articleId: string) {
  return del<void>(`/content/draft/${articleId}`)
}
