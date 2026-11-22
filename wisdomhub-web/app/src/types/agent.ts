import type { ID } from './common'

/** 工作流编号：wire 上为整数，仅用于入口选择与只读 VO */
export type WorkflowKind = 1 | 2 | 3

/** 三个聊天端点；分支由端点决定，请求体不携带 workflow */
export type AgentEndpoint = '/api/agent/chat' | '/api/agent/learning-path' | '/api/agent/text-qa'

export const WORKFLOW = {
  QA: 1,
  LEARNING_PATH: 2,
  TEXT_QA: 3,
} as const satisfies Record<string, WorkflowKind>

export const WORKFLOW_ENDPOINTS: Record<WorkflowKind, AgentEndpoint> = {
  [WORKFLOW.QA]: '/api/agent/chat',
  [WORKFLOW.LEARNING_PATH]: '/api/agent/learning-path',
  [WORKFLOW.TEXT_QA]: '/api/agent/text-qa',
}

export const WORKFLOW_LABELS: Record<WorkflowKind, string> = {
  [WORKFLOW.QA]: '技术问答',
  [WORKFLOW.LEARNING_PATH]: '学习路线',
  [WORKFLOW.TEXT_QA]: '文本问答',
}

export function isWorkflowKind(value: unknown): value is WorkflowKind {
  return value === WORKFLOW.QA || value === WORKFLOW.LEARNING_PATH || value === WORKFLOW.TEXT_QA
}

export interface AgentTechRef {
  tagId: string
  name: string
}

export interface AgentPlanStage {
  depth: number
  tags: AgentTechRef[]
}

export interface AgentRoute {
  target: AgentTechRef
  known: string[]
  truncated: boolean
  stages: AgentPlanStage[]
}

export interface AgentMessageArtifacts {
  route: AgentRoute | null
}

export type AgentStepStatus = 'running' | 'done' | 'error'

export interface AgentStep {
  id: string
  tool: string
  label: string
  status: AgentStepStatus
  /** SSE 编码时 exclude_none，未产出则不下发 */
  summary?: string
}

interface AgentMetaEvent {
  event: 'meta'
  data: { conversationId: string; messageId: string; workflow: WorkflowKind }
}

interface AgentRouteEvent {
  event: 'route'
  data: AgentRoute
}

interface AgentStepsEvent {
  event: 'steps'
  data: { steps: AgentStep[] }
}

interface AgentDeltaEvent {
  event: 'delta'
  data: { text: string }
}

interface AgentDoneEvent {
  event: 'done'
  data: { finishReason: string }
}

interface AgentErrorEvent {
  event: 'error'
  data: { code: number; message: string }
}

export type AgentEvent =
  | AgentMetaEvent
  | AgentRouteEvent
  | AgentStepsEvent
  | AgentDeltaEvent
  | AgentDoneEvent
  | AgentErrorEvent

export type QaStreamEvent = AgentMetaEvent | AgentDeltaEvent | AgentDoneEvent | AgentErrorEvent

export type TextQaStreamEvent = QaStreamEvent

export type LearningStreamEvent = QaStreamEvent | AgentRouteEvent | AgentStepsEvent

export interface QaChatRequest {
  /** UUID；缺省由 Python 生成 */
  conversationId?: string | null
  /** 1~64 字符；前端每次发送自带才能配合幂等 */
  messageId?: string | null
  /** 必填，最多 4000 字 */
  question: string
}

export interface LearningPathChatRequest extends QaChatRequest {
  /** 1~50 字 */
  technology: string
  /** 最多 20 项 */
  knownTechnologies?: string[]
}

export interface TextQaChatRequest extends QaChatRequest {
  /** 1~2000 字 */
  text: string
}

export type ChatRequest = QaChatRequest | LearningPathChatRequest | TextQaChatRequest

export interface AgentAnalysisVO {
  articleId: ID
  status: 'pending' | 'done' | 'error'
  message?: string
  topic?: string
  entities?: string[]
  knowledgePoints?: string[]
  sourceUpdatedAt?: string | null
  analyzedAt?: string | null
}

/** 契约外的事件：保留原始 event / data，由调用方决定是否处理 */
export interface AgentUnknownEvent {
  event: string
  data: unknown
  raw: string
}
