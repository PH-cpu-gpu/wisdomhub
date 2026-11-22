import type { AgentMessageArtifacts, AgentStep, WorkflowKind } from './agent'

export type ConversationMessageRole = 'user' | 'assistant'

export type ConversationMessageStatus = 'in_progress' | 'done' | 'aborted' | 'error'

export interface ConversationMessageVO {
  id: string
  messageId: string
  role: ConversationMessageRole
  content: string
  workflow: WorkflowKind | null
  status: ConversationMessageStatus
  steps?: AgentStep[] | null
  artifacts?: AgentMessageArtifacts | null
  createdAt: string
}

export interface ConversationItemVO {
  id: string
  title: string
  workflow: WorkflowKind | null
  createdAt: string
  updatedAt: string
}

export interface ConversationDetailVO extends ConversationItemVO {
  messages: ConversationMessageVO[]
}
