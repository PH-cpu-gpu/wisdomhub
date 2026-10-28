import type { ID } from './common'
import type { ArticleListItemVO } from './content'

export interface RecommendationTagVO {
  tagId: ID
  name: string
  category: string
}

export interface RecommendationItemVO extends ArticleListItemVO {
  tags: RecommendationTagVO[]
}
