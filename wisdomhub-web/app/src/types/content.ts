import type { ID, PageQuery } from './common'

export interface ArticleListItemVO {
  articleId: ID
  authorId: ID
  title: string
  summary: string
  publishTime: string
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
}

export interface ArticleColumnVO {
  columnId: ID
  title: string
  /** 单位：分；0 表示免费 */
  price: number
  purchased: boolean
  purchasable: boolean
}

export interface ArticleDetailVO extends ArticleListItemVO {
  /** 服务端消毒后的正文；locked=true 时为 null */
  html: string | null
  locked: boolean
  columns: ArticleColumnVO[]
  liked: boolean
  collected: boolean
}

export interface ArticleBriefVO {
  articleId: ID
  title: string
  summary: string
}

export interface ArticleSourceVO {
  articleId: ID
  title: string
  summary: string
  markdown: string
  /** 1-草稿 2-已发布 3-下架 */
  state: 1 | 2 | 3
  updateTime: string
  tagIds: ID[]
}

export interface ArticlePublishPayload {
  /** 带了 articleId 就是覆盖发布（草稿转发布走这条） */
  articleId?: ID
  title: string
  summary?: string
  markdown: string
  /** null = 保持原关联；[] = 清空；非空 = 全量替换 */
  tagIds?: ID[] | null
}

export interface ArticleListQuery extends PageQuery {
  sort?: 'new' | 'hot'
  tagId?: ID
  authorId?: ID
}

export interface DraftBriefVO {
  articleId: ID
  title: string
  updateTime: string
}

export interface DraftPayload {
  title?: string
  markdown?: string
}

export interface CommentVO {
  commentId: ID
  userId: ID
  parentId: ID | null
  content: string
  createTime: string
  replies: CommentVO[]
}

export interface CommentCreatePayload {
  /** 1-文章（默认） 2-回答 */
  targetType?: 1 | 2
  targetId: ID
  parentId?: ID | null
  content: string
}

export interface CommentPageQuery extends PageQuery {
  targetType?: 1 | 2
  targetId: ID
}

export interface ColumnVO {
  columnId: ID
  userId: ID
  title: string
  description: string
  cover: string | null
  /** 单位：分；0 表示免费 */
  price: number
  articleCount: number
  /** 仅专栏详情填充；我的专栏列表为 null */
  purchased: boolean | null
  createTime: string
}

export interface ColumnCreatePayload {
  title: string
  description?: string
  cover?: string
  price?: number
}

export interface PurchasedColumnVO {
  columnId: ID
  userId: ID
  title: string
  description: string
  cover: string | null
  articleCount: number
  /** 1-正常 2-下架 */
  state: 1 | 2
  /** 实付价快照，单位：分 */
  price: number
  orderId: ID
  purchaseTime: string
}

export interface TagVO {
  tagId: ID
  name: string
  category: string
  description: string
}

export interface HotTagVO {
  tagId: ID
  name: string
  articleCount: number
}
