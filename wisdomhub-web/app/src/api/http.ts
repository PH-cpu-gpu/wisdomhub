import axios, {
  AxiosHeaders,
  type AxiosAdapter,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

import { toast } from '@/components/base/toast'
import type { R } from '@/types/common'

import { clearTokens, getAccessToken, getRefreshToken, setTokens } from './auth-token'
import { ApiError, defaultErrorMessage, isApiError } from './error'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
const TIMEOUT = 15000
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true'

/** mock 适配器按需加载，关闭时 fixtures 不进入首屏 */
const mockAdapter: AxiosAdapter = async (config) => {
  const { handleMockRequest } = await import('@/mocks')
  return handleMockRequest(config)
}

const PUBLIC_AUTH_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/api/auth/code/',
]
const NO_REFRESH_PATHS = [...PUBLIC_AUTH_PATHS, '/api/auth/logout']

export interface RequestOptions extends AxiosRequestConfig {
  /** 不附带 Authorization（登录 / 注册 / 验证码） */
  skipAuth?: boolean
  /** 调用方自己处理错误提示 */
  skipErrorToast?: boolean
}

interface InternalRequestConfig extends InternalAxiosRequestConfig {
  skipAuth?: boolean
  skipErrorToast?: boolean
  _retry?: boolean
}

interface LoginTokens {
  accessToken: string
  refreshToken: string
  hasEmail?: boolean | null
}

let refreshPromise: Promise<string> | null = null
let sessionExpiredNotified = false
let unauthorizedHandler: (() => void) | null = null
let tokensUpdatedHandler: ((tokens: { accessToken: string; refreshToken: string }) => void) | null =
  null

/** main.ts 注册：401 且无法刷新时由 router 跳登录，避免 http 层直接依赖 router */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

/** main.ts 注册：刷新成功后把新 token 同步到 auth store */
export function setTokensUpdatedHandler(
  handler: ((tokens: { accessToken: string; refreshToken: string }) => void) | null,
): void {
  tokensUpdatedHandler = handler
}

function isPublicAuthPath(url?: string): boolean {
  if (!url) return false
  return PUBLIC_AUTH_PATHS.some((path) => url.startsWith(path))
}

function isNoRefreshPath(url?: string): boolean {
  if (!url) return false
  return NO_REFRESH_PATHS.some((path) => url.startsWith(path))
}

function isR(value: unknown): value is R<unknown> {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Record<string, unknown>
  return typeof record.code === 'number' && typeof record.message === 'string'
}

/** 数组参数转逗号分隔：articleIds=1,2，而不是 articleIds[]=1&articleIds[]=2 */
export function serializeParams(params: Record<string, unknown>): string {
  const search = new URLSearchParams()

  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue

    if (Array.isArray(value)) {
      if (value.length > 0) search.append(key, value.map((item) => String(item)).join(','))
    } else if (value instanceof Date) {
      search.append(key, value.toISOString())
    } else if (typeof value === 'object') {
      search.append(key, JSON.stringify(value))
    } else {
      search.append(key, String(value))
    }
  }

  return search.toString()
}

export const http: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  paramsSerializer: {
    serialize: (params) => serializeParams(params as Record<string, unknown>),
  },
  ...(USE_MOCK ? { adapter: mockAdapter } : {}),
})

http.interceptors.request.use((config) => {
  const requestConfig = config as InternalRequestConfig

  if (!requestConfig.skipAuth && !isPublicAuthPath(requestConfig.url)) {
    const token = getAccessToken()
    if (token) {
      const headers = AxiosHeaders.from(requestConfig.headers)
      headers.set('Authorization', `Bearer ${token}`)
      requestConfig.headers = headers
    }
  }

  return requestConfig
})

function expireSession(): void {
  clearTokens()

  if (!sessionExpiredNotified) {
    sessionExpiredNotified = true
    toast.warning('登录已过期，请重新登录')
    window.setTimeout(() => {
      sessionExpiredNotified = false
    }, 3000)
  }

  if (unauthorizedHandler) {
    unauthorizedHandler()
    return
  }

  const current = `${window.location.pathname}${window.location.search}`
  if (!current.startsWith('/login')) {
    window.location.assign(`/login?redirect=${encodeURIComponent(current)}`)
  }
}

function normalizeError(error: unknown): ApiError {
  if (isApiError(error)) return error
  if (axios.isCancel(error)) return new ApiError(0, '请求已取消')

  if (axios.isAxiosError(error)) {
    if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
      return new ApiError(0, '请求超时，请稍后重试')
    }
    if (!error.response) {
      return new ApiError(0, '网络异常，请检查网络连接')
    }

    const body = error.response.data as Partial<R<unknown>> | undefined
    const code = typeof body?.code === 'number' ? body.code : error.response.status
    const message =
      typeof body?.message === 'string' && body.message ? body.message : defaultErrorMessage(code)

    return new ApiError(code, message, error.response.status)
  }

  if (error instanceof Error) return new ApiError(0, error.message)
  return new ApiError(0, defaultErrorMessage(0))
}

async function doRefresh(): Promise<string> {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new ApiError(401, '登录已过期，请重新登录', 401)
  }

  const tokens = await post<LoginTokens>(
    '/api/auth/refresh',
    { refreshToken },
    { skipAuth: true, skipErrorToast: true },
  )

  if (!tokens?.accessToken) {
    throw new ApiError(401, '登录已过期，请重新登录', 401)
  }

  const nextTokens = {
    accessToken: tokens.accessToken,
    refreshToken: tokens.refreshToken,
  }
  setTokens(nextTokens)
  tokensUpdatedHandler?.(nextTokens)
  return tokens.accessToken
}

/** 单飞：并发 401 / 守卫主动刷新只发一次 refresh */
function getRefreshedToken(notify = true): Promise<string> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise.catch((error) => {
    if (notify) expireSession()
    throw error
  })
}

/** 守卫调用时 notify=false：刷新失败只清本地，由守卫负责带 redirect 跳登录 */
export function refreshAccessToken(notify = true): Promise<string> {
  return getRefreshedToken(notify)
}

function notifyError(error: ApiError, config?: InternalRequestConfig): void {
  if (config?.skipErrorToast) return
  if (error.code === 401) return
  toast.error(error.message)
}

async function handleFailure(
  error: ApiError,
  config?: InternalRequestConfig,
): Promise<AxiosResponse> {
  if (error.code === 401 && config && !config.skipAuth && !isNoRefreshPath(config.url)) {
    if (!config._retry) {
      await getRefreshedToken()
      const retryConfig: InternalRequestConfig = {
        ...config,
        _retry: true,
      }
      return http.request(retryConfig)
    }

    expireSession()
    throw error
  }

  notifyError(error, config)
  throw error
}

http.interceptors.response.use(
  (response) => {
    const body: unknown = response.data

    if (isR(body)) {
      if (body.code === 200) {
        response.data = body.data
        return response
      }
      return handleFailure(
        new ApiError(body.code, body.message, response.status),
        response.config as InternalRequestConfig,
      )
    }

    return response
  },
  (error: unknown) => {
    if (axios.isCancel(error)) return Promise.reject(error)

    const config = axios.isAxiosError(error)
      ? (error.config as InternalRequestConfig | undefined)
      : undefined

    return handleFailure(normalizeError(error), config)
  },
)

export function request<T>(config: RequestOptions): Promise<T> {
  return http
    .request<unknown, AxiosResponse<unknown>>(config)
    .then((response) => response.data as T)
}

export function get<T>(url: string, params?: object, config?: RequestOptions): Promise<T> {
  return request<T>({ ...config, method: 'GET', url, params })
}

export function post<T>(url: string, data?: unknown, config?: RequestOptions): Promise<T> {
  return request<T>({ ...config, method: 'POST', url, data })
}

export function put<T>(url: string, data?: unknown, config?: RequestOptions): Promise<T> {
  return request<T>({ ...config, method: 'PUT', url, data })
}

export function del<T>(url: string, config?: RequestOptions): Promise<T> {
  return request<T>({ ...config, method: 'DELETE', url })
}
