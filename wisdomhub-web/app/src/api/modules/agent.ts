import { ApiError } from '@/api/error'
import { get, type RequestOptions } from '@/api/http'
import { postSse } from '@/api/sse'
import { isWorkflowKind } from '@/types/agent'
import type {
  AgentAnalysisVO,
  AgentEndpoint,
  AgentEvent,
  AgentPlanStage,
  AgentRoute,
  AgentStep,
  AgentStepStatus,
  AgentTechRef,
  AgentUnknownEvent,
  ChatRequest,
} from '@/types/agent'
import type { ID } from '@/types/common'

const KNOWN_EVENTS = new Set(['meta', 'route', 'steps', 'delta', 'done', 'error'])

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isTechRef(value: unknown): value is AgentTechRef {
  return isRecord(value) && typeof value.tagId === 'string' && typeof value.name === 'string'
}

function isPlanStage(value: unknown): value is AgentPlanStage {
  return (
    isRecord(value) &&
    typeof value.depth === 'number' &&
    Array.isArray(value.tags) &&
    value.tags.every(isTechRef)
  )
}

function isRouteData(value: unknown): value is AgentRoute {
  return (
    isRecord(value) &&
    isTechRef(value.target) &&
    Array.isArray(value.known) &&
    value.known.every((item) => typeof item === 'string') &&
    typeof value.truncated === 'boolean' &&
    Array.isArray(value.stages) &&
    value.stages.every(isPlanStage)
  )
}

function isStepStatus(value: unknown): value is AgentStepStatus {
  return value === 'running' || value === 'done' || value === 'error'
}

function isStep(value: unknown): value is AgentStep {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.tool === 'string' &&
    typeof value.label === 'string' &&
    isStepStatus(value.status) &&
    (value.summary === undefined || typeof value.summary === 'string')
  )
}

/** trigger=true 时无记录或上次失败会后台补跑；只在首次进入 / 手动重试时传。 */
export function analysis(
  articleId: ID,
  options: RequestOptions = {},
  trigger = false,
): Promise<AgentAnalysisVO> {
  return get<AgentAnalysisVO>(
    `/api/agent/analysis/${articleId}`,
    trigger ? { trigger: true } : undefined,
    options,
  )
}

export interface AgentStreamOptions {
  signal?: AbortSignal
  onEvent?: (event: AgentEvent) => void
  onUnknownEvent?: (event: AgentUnknownEvent) => void
}

function parseFrameData(raw: string): unknown {
  try {
    return JSON.parse(raw)
  } catch {
    return raw
  }
}

function toAgentEvent(event: string, data: unknown): AgentEvent | null {
  if (typeof data !== 'object' || data === null) return null
  const record = data as Record<string, unknown>

  switch (event) {
    case 'meta':
      return typeof record.conversationId === 'string' &&
        typeof record.messageId === 'string' &&
        isWorkflowKind(record.workflow)
        ? {
            event,
            data: {
              conversationId: record.conversationId,
              messageId: record.messageId,
              workflow: record.workflow,
            },
          }
        : null
    case 'route':
      return isRouteData(data) ? { event, data } : null
    case 'steps':
      return Array.isArray(record.steps) && record.steps.every(isStep)
        ? { event, data: { steps: record.steps } }
        : null
    case 'delta':
      return typeof record.text === 'string' ? { event, data: { text: record.text } } : null
    case 'done':
      return typeof record.finishReason === 'string'
        ? { event, data: { finishReason: record.finishReason } }
        : null
    case 'error':
      return typeof record.code === 'number' && typeof record.message === 'string'
        ? { event, data: { code: record.code, message: record.message } }
        : null
    default:
      return null
  }
}

/**
 * AI 对话流：POST {endpoint}。
 * meta → route? → steps* → delta* → done | error；未知事件通过 onUnknownEvent 透传。
 */
export function chatStream(
  endpoint: AgentEndpoint,
  payload: ChatRequest,
  options: AgentStreamOptions = {},
): Promise<void> {
  return postSse(endpoint, payload, {
    signal: options.signal,
    onEvent: (frame) => {
      const data = parseFrameData(frame.data)

      if (!KNOWN_EVENTS.has(frame.event)) {
        const unknown: AgentUnknownEvent = { event: frame.event, data, raw: frame.data }
        options.onUnknownEvent?.(unknown)
        return true
      }

      const event = toAgentEvent(frame.event, data)
      if (!event) throw new ApiError(0, `AI 事件格式异常：${frame.event}`)

      options.onEvent?.(event)
      return event.event !== 'done' && event.event !== 'error'
    },
  })
}
