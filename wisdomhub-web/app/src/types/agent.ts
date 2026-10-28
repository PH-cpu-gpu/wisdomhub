import type { ID } from './common'

export type AgentWorkflow = 'qa' | 'learning_path'

export interface AgentChatRequest {
  /** UUID；缺省由 Python 生成 */
  conversationId?: string | null
  /** 1~64 字符；前端每次发送自带才能配合幂等 */
  messageId?: string | null
  workflow?: AgentWorkflow
  /** 仅 learning_path：目标技术 */
  technology?: string | null
  /** 仅 learning_path：已掌握技术 */
  knownTechnologies?: string[]
  /** 必填，最多 4000 字 */
  question: string
}

export interface AgentAnalysisVO {
  articleId: ID
  status: 'pending' | 'done' | 'error'
  message?: string
  topic?: string
  entities?: string[]
  knowledgePoints?: string[]
}

export type AgentEvent =
  | { event: 'meta'; data: { conversationId: string; messageId: string } }
  | { event: 'delta'; data: { text: string } }
  | { event: 'done'; data: { finishReason: string } }
  | { event: 'error'; data: { code: number; message: string } }

/** 契约外的事件：保留原始 event / data，由调用方决定是否处理 */
export interface AgentUnknownEvent {
  event: string
  data: unknown
  raw: string
}
