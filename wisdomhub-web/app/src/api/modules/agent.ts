import { ApiError } from '@/api/error'
import { get, type RequestOptions } from '@/api/http'
import { postSse } from '@/api/sse'
import type {
  AgentAnalysisVO,
  AgentChatRequest,
  AgentEvent,
  AgentUnknownEvent,
} from '@/types/agent'
import type { ID } from '@/types/common'

const KNOWN_EVENTS = new Set(['meta', 'delta', 'done', 'error'])

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
      return typeof record.conversationId === 'string' && typeof record.messageId === 'string'
        ? { event, data: { conversationId: record.conversationId, messageId: record.messageId } }
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
 * AI 对话流：POST /api/agent/chat。
 * meta → delta* → done | error；未知事件通过 onUnknownEvent / onEvent 透传。
 */
export function chatStream(
  payload: AgentChatRequest,
  options: AgentStreamOptions = {},
): Promise<void> {
  return postSse('/api/agent/chat', payload, {
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
