import type { ID, PageQuery } from './common'

export interface QuestionListItemVO {
  questionId: ID
  userId: ID
  title: string
  answerCount: number
  solved: boolean
  createTime: string
}

export interface AnswerVO {
  answerId: ID
  questionId: ID
  userId: ID
  content: string
  createTime: string
}

export interface QuestionDetailVO {
  questionId: ID
  userId: ID
  title: string
  content: string
  answerCount: number
  acceptedAnswerId: ID | null
  acceptedAnswer: AnswerVO | null
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
