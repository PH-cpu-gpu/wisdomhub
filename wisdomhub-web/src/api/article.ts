import { del, get, post, put } from './http'
import type { PageResponse } from './types'

/**
 * 文章接口（F1.2 / F1.4 用）。
 *
 * ⚠️ **ID 是字符串**：后端给 `Long` 注册了 `ToStringSerializer`（17.2），雪花 ID 19 位
 * 超过 JS 安全整数范围，当成 number 会静默丢精度。`total` 这类计数才是 number。
 */
export interface ArticleListItem {
  articleId: string
  authorId: string
  title: string
  summary: string
  /** UTC ISO 串，展示前用 utils/format 转本地时区 */
  publishTime: string
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
}

/** 排序白名单。后端只认这两个，传别的给 400（B1.23） */
export type ArticleSort = 'new' | 'hot'

export interface ArticleListQuery {
  page?: number
  pageSize?: number
  sort?: ArticleSort
  tagId?: string
  authorId?: string
}

/**
 * 公开文章列表。
 *
 * 只返回**已发布且未删除**的文章；`hot` 用 `article.view_count` 快照列排序，
 * 而那个快照由 B1.22 每 2 分钟从 Redis 落库一次 —— 所以「最热」最多滞后 2 分钟，
 * 且列表里的阅读量会略低于详情页的实时值。这是刻意的（列表不逐条读 Redis）。
 */
export function listArticles(query: ArticleListQuery = {}) {
  return get<PageResponse<ArticleListItem>>('/content/article', query)
}

/**
 * 文章详情。
 *
 * `html` 是**后端渲染并消毒过**的（flexmark → jsoup 白名单，见 content 的 MarkdownRenderer），
 * 所以详情页可以放心 `v-html`；**不要**再拿 markdown 在前端渲染一遍 —— 两套实现必然不一致。
 *
 * ⚠️ `liked` / `collected` 是**可选登录**路径：游客恒为 `false`，不代表「没赞过」，
 * 而是「不知道你是谁」。所以游客态不要拿它渲染「已赞」的样式（反正也点不动）。
 */
export interface ArticleDetail {
  articleId: string
  authorId: string
  title: string
  summary: string
  html: string
  publishTime: string
  liked: boolean
  collected: boolean
  /** 阅读量：Redis 是读源，这里拿到的是实时值（与列表的快照列不同） */
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
}

/** 编辑器源数据（草稿恢复 / 编辑已发布都走它，属主本人、任意非删除状态都返回） */
export interface ArticleSource {
  articleId: string
  title: string
  summary: string
  markdown: string
  /** 1-草稿 2-已发布 3-下架 */
  state: number
  updateTime: string
  tagIds: string[]
}

export interface PublishPayload {
  /** 带上就是覆盖发布（草稿转发布也走这条）；不带则新分配 ID */
  articleId?: string
  title: string
  summary?: string
  markdown: string
  /** null 与 [] 不等价：null = 不改动标签，[] = 清空（B1.18 定案） */
  tagIds?: string[]
}

export function getArticleDetail(articleId: string) {
  return get<ArticleDetail>(`/content/article/${articleId}`)
}

export function getArticleSource(articleId: string) {
  return get<ArticleSource>(`/content/article/${articleId}/source`)
}

/** 发布 / 覆盖发布。返回文章 ID */
export function publishArticle(payload: PublishPayload) {
  return post<string>('/content/article', payload)
}

// ---------------------------------------------------------------------------
// 点赞 / 收藏（B1.26：设置状态，幂等，四个动作都返回最新计数）
// ---------------------------------------------------------------------------

export function likeArticle(articleId: string) {
  return put<number>(`/content/article/${articleId}/like`)
}

export function unlikeArticle(articleId: string) {
  return del<number>(`/content/article/${articleId}/like`)
}

export function collectArticle(articleId: string) {
  return put<number>(`/content/article/${articleId}/collect`)
}

export function uncollectArticle(articleId: string) {
  return del<number>(`/content/article/${articleId}/collect`)
}

/**
 * 我的收藏（B1.26）。按**收藏时间**倒序（不是文章发布时间），
 * 走 `article_favorite JOIN article`，所以已删/草稿/下架的文章不会出现 ✓
 */
export function listMyFavorites(page = 1, pageSize = 10) {
  return get<PageResponse<ArticleListItem>>('/content/article/collect', { page, pageSize })
}
