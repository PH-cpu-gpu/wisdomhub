import { del, get, post, put } from './http'
import type { PageResponse } from './types'

/**
 * 问答（B1.28）。
 *
 * 几个后端定下的形态，前端别按直觉改：
 *   - **问题列表不返回正文**（`content` 是 TEXT，跟着列表走会让一页几十 KB），点进详情才拿
 *   - **回答是按「采纳的置顶、其余按发表时间升序」排的**（后端 SQL 里 `(id <=> accepted_answer_id) DESC`），
 *     而且详情单独给了一份 `acceptedAnswer` —— 所以不要指望它在回答列表的第一页里找
 *   - **采纳可改可撤**：`PUT /accept/{answerId}` 是幂等设置，再采纳另一条就是改采纳；
 *     `DELETE` 取消，且只有「当前采纳的正是这条」时才会清空
 *   - 提问是**创建类**（不幂等），重复提交真的会建两条
 */

export interface QuestionListItem {
  questionId: string
  userId: string
  title: string
  answerCount: number
  favoriteCount: number
  /** 是否已采纳答案（后端由 acceptedAnswerId 派生） */
  solved: boolean
  createTime: string
}

export interface AnswerItem {
  answerId: string
  questionId: string
  userId: string
  content: string
  likeCount: number
  /** 回答的评论（B1.28 起 targetType=2 打通了）—— 这一版 UI 还没做，恒为 0 */
  commentCount: number
  createTime: string
}

export interface QuestionDetail {
  questionId: string
  userId: string
  title: string
  content: string
  answerCount: number
  favoriteCount: number
  acceptedAnswerId: string | null
  /** 已采纳时是被采纳的那条回答，否则 null */
  acceptedAnswer: AnswerItem | null
  /** 游客恒 false（可选登录路径） */
  favorited: boolean
  createTime: string
}

/** 白名单只有这两个，传别的后端给 400 */
export type QuestionSort = 'new' | 'hot'

// ---------------------------------------------------------------------------
// 问题
// ---------------------------------------------------------------------------

export function listQuestions(query: {
  page?: number
  pageSize?: number
  sort?: QuestionSort
} = {}) {
  return get<PageResponse<QuestionListItem>>('/content/question', query)
}

export function getQuestion(questionId: string) {
  return get<QuestionDetail>(`/content/question/${questionId}`)
}

/** 提问。返回问题 ID */
export function createQuestion(payload: { title: string; content: string }) {
  return post<string>('/content/question', payload)
}

export function deleteQuestion(questionId: string) {
  return del<void>(`/content/question/${questionId}`)
}

export function favoriteQuestion(questionId: string) {
  return put<number>(`/content/question/${questionId}/favorite`)
}

export function unfavoriteQuestion(questionId: string) {
  return del<number>(`/content/question/${questionId}/favorite`)
}

// ---------------------------------------------------------------------------
// 回答
// ---------------------------------------------------------------------------

export function listAnswers(questionId: string, page = 1, pageSize = 10) {
  return get<PageResponse<AnswerItem>>(`/content/question/${questionId}/answer`, {
    page,
    pageSize,
  })
}

/** 回答问题。返回回答 ID */
export function createAnswer(questionId: string, payload: { content: string }) {
  return post<string>(`/content/question/${questionId}/answer`, payload)
}

export function deleteAnswer(answerId: string) {
  return del<void>(`/content/answer/${answerId}`)
}

export function likeAnswer(answerId: string) {
  return put<number>(`/content/answer/${answerId}/like`)
}

export function unlikeAnswer(answerId: string) {
  return del<number>(`/content/answer/${answerId}/like`)
}

// ---------------------------------------------------------------------------
// 采纳（只有提问者本人能调）
// ---------------------------------------------------------------------------

export function acceptAnswer(questionId: string, answerId: string) {
  return put<void>(`/content/question/${questionId}/accept/${answerId}`)
}

export function cancelAccept(questionId: string, answerId: string) {
  return del<void>(`/content/question/${questionId}/accept/${answerId}`)
}
