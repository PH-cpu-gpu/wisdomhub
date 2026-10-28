import { createParser } from 'eventsource-parser'

import type { R } from '@/types/common'

import { getAccessToken } from './auth-token'
import { ApiError, isApiError } from './error'
import { refreshAccessToken } from './http'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const IDLE_TIMEOUT = 90_000

export interface SseFrame {
  event: string
  data: string
}

export interface PostSseOptions {
  signal?: AbortSignal
  headers?: Record<string, string>
  /** 返回 false 立即结束流（done / error 等终态事件） */
  onEvent?: (frame: SseFrame) => boolean | void
}

function resolveUrl(url: string): string {
  if (/^https?:\/\//i.test(url)) return url
  return `${BASE_URL}${url.startsWith('/') ? url : `/${url}`}`
}

function isR(value: unknown): value is R<unknown> {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Record<string, unknown>
  return typeof record.code === 'number' && typeof record.message === 'string'
}

async function responseError(response: Response): Promise<ApiError> {
  let payload: unknown = null
  try {
    payload = await response.json()
  } catch {
    // 非 JSON 错误体交给默认文案
  }

  if (isR(payload)) return new ApiError(payload.code, payload.message, response.status)
  return new ApiError(response.status, undefined, response.status)
}

async function nonSseError(response: Response): Promise<ApiError> {
  let payload: unknown = null
  try {
    payload = JSON.parse(await response.text())
  } catch {
    // 纯文本 / 空响应
  }

  if (isR(payload) && payload.code !== 200) {
    return new ApiError(payload.code, payload.message, response.status)
  }
  return new ApiError(0, 'AI 服务返回了非流式响应')
}

/**
 * POST + fetch + ReadableStream 的 SSE 客户端。
 * 不能使用 EventSource（只支持 GET，也带不了 Authorization）。
 */
export async function postSse(
  url: string,
  body: unknown,
  options: PostSseOptions = {},
): Promise<void> {
  if (options.signal?.aborted) return

  const controller = new AbortController()
  const onCallerAbort = () => controller.abort()
  options.signal?.addEventListener('abort', onCallerAbort, { once: true })

  let timedOut = false
  let idleTimer: number | null = null
  let reader: ReadableStreamDefaultReader<Uint8Array> | null = null

  function armIdleTimer(): void {
    if (idleTimer !== null) window.clearTimeout(idleTimer)
    idleTimer = window.setTimeout(() => {
      timedOut = true
      controller.abort()
    }, IDLE_TIMEOUT)
  }

  function sendRequest(token: string | null): Promise<Response> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...options.headers,
    }
    if (token) headers.Authorization = `Bearer ${token}`

    return fetch(resolveUrl(url), {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
      signal: controller.signal,
      cache: 'no-store',
    })
  }

  try {
    armIdleTimer()

    let token = getAccessToken()
    let response = await sendRequest(token)

    // accessToken 过期：与 axios 层共用单飞刷新，成功后重试一次
    if (response.status === 401) {
      await response.body?.cancel().catch(() => {})
      try {
        token = await refreshAccessToken()
      } catch (error) {
        if (isApiError(error)) throw error
        throw new ApiError(401, '登录已过期，请重新登录', 401)
      }
      response = await sendRequest(token)
      if (response.status === 401) {
        throw new ApiError(401, '登录已过期，请重新登录', 401)
      }
    }

    if (!response.ok) throw await responseError(response)

    const contentType = response.headers.get('content-type') ?? ''
    if (!contentType.includes('text/event-stream')) {
      throw await nonSseError(response)
    }

    if (!response.body) throw new ApiError(0, '当前浏览器不支持流式响应')
    reader = response.body.getReader()

    const decoder = new TextDecoder()
    let terminated = false

    const parser = createParser({
      // : ping 注释不会进入 onEvent；未知事件由调用方透传
      onEvent: (message) => {
        const frame: SseFrame = { event: message.event ?? 'message', data: message.data }
        if (options.onEvent?.(frame) === false) terminated = true
      },
    })

    while (!terminated) {
      const { value, done } = await reader.read()
      if (done) break
      armIdleTimer()
      parser.feed(decoder.decode(value, { stream: true }))
    }

    if (!terminated) parser.feed(decoder.decode())
    if (terminated) await reader.cancel().catch(() => {})
  } catch (error) {
    if (options.signal?.aborted) return
    if (timedOut) throw new ApiError(0, 'AI 响应超时，请重试')
    if (isApiError(error)) throw error
    if (error instanceof DOMException && error.name === 'AbortError') return
    throw new ApiError(0, 'AI 服务连接失败，请稍后重试')
  } finally {
    if (idleTimer !== null) window.clearTimeout(idleTimer)
    options.signal?.removeEventListener('abort', onCallerAbort)

    if (reader) {
      await reader.cancel().catch(() => {})
      try {
        reader.releaseLock()
      } catch {
        // reader 已经释放
      }
    }
  }
}
