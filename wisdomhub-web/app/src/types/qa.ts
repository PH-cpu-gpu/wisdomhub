import type { ID, PageQuery } from './common'

export interface QuestionListItemVO {
  questionId: ID
  userId: ID
  title: string
  answerCount: number
  favoriteCount: number
  solved: boolean
  createTime: string
}

export interface AnswerVO {
  answerId: ID
  questionId: ID
  userId: ID
  content: string
  likeCount: number
  commentCount: number
  createTime: string
}

export interface QuestionDetailVO {
  questionId: ID
  userId: ID
  title: string
  content: string
  answerCount: number
  favoriteCount: number
  acceptedAnswerId: ID | null
  acceptedAnswer: AnswerVO | null
  favorited: boolean
  createTime: string
}

export interface QuestionCreatePayload {
  title: string
  content: string
}

export interface AnswerCreatePayload {
  content: string
}

export interface QuestionListQuery extends PageQuery {
  sort?: 'new' | 'hot'
}
