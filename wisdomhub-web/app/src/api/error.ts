const DEFAULT_MESSAGES: Record<number, string> = {
  400: '请求参数不合法',
  401: '未登录或登录已过期',
  403: '无权限访问',
  404: '资源不存在',
  409: '操作冲突',
  429: '请求过于频繁，请稍后重试',
  500: '服务异常，请稍后重试',
  503: '依赖服务暂不可用',
}

export class ApiError extends Error {
  readonly code: number
  readonly status?: number

  constructor(code: number, message?: string, status?: number) {
    super(message || DEFAULT_MESSAGES[code] || '请求失败，请稍后重试')
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

export function defaultErrorMessage(code: number): string {
  return DEFAULT_MESSAGES[code] ?? '请求失败，请稍后重试'
}
