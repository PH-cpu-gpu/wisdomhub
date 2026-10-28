import type { ID, PageQuery } from './common'
import type { ArticleListItemVO } from './content'

/** 搜索结果里的标签摘要，带展示分组 */
export interface SearchTagBriefVO {
  tagId: ID
  name: string
  category: string
}

export interface ArticleSearchVO extends ArticleListItemVO {
  /** key 取 title / summary / content，value 是带 <em> 的已转义片段 */
  highlight: Record<string, string>
  tags: SearchTagBriefVO[]
}

export interface QuestionSearchVO {
  questionId: ID
  userId: ID
  title: string
  content: string
  highlight: Record<string, string>
  answerCount: number
  favoriteCount: number
  acceptedAnswerId: ID | null
  createTime: string
  updateTime: string
}

export interface UserSearchVO {
  userId: ID
  username: string
  avatar: string | null
  bio: string | null
  highlight: Record<string, string>
  createTime: string
  updateTime: string
}

export interface TagSearchVO {
  tagId: ID
  name: string
  category: string
  description: string
  highlight: Record<string, string>
  createTime: string
  updateTime: string
}

export interface HotWordVO {
  keyword: string
  count: number
}

export interface ArticleSearchQuery extends PageQuery {
  keyword?: string
  sort?: 'relevance' | 'new' | 'hot'
  tagId?: ID
  startTime?: string
  endTime?: string
}

export interface QuestionSearchQuery extends PageQuery {
  keyword?: string
  sort?: 'relevance' | 'new' | 'hot'
  startTime?: string
  endTime?: string
}

export interface UserSearchQuery extends PageQuery {
  keyword?: string
  sort?: 'relevance' | 'new'
}

export interface TagSearchQuery extends PageQuery {
  keyword?: string
  sort?: 'relevance' | 'new'
  category?: string
}

export interface SuggestQuery {
  keyword?: string
  type?: 'article' | 'question' | 'user' | 'tag'
  size?: number
}

export interface HotWordQuery {
  days?: number
  size?: number
}
