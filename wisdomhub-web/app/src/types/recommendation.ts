import type { ID } from './common'
import type { ArticleListItemVO } from './content'

export interface RecommendationTagVO {
  tagId: ID
  name: string
  category: string
}

export interface RecommendationItemVO extends ArticleListItemVO {
  /** 1-文章 7-视频；视频条目运行时 articleId 为 null */
  contentType: number
  videoId: ID | null
  coverKey: string | null
  coverUrl: string | null
  durationMs: number | null
  tags: RecommendationTagVO[]
}
