export const AUTH_TOKEN_KEY = 'wisdomhub.accessToken'
export const REFRESH_TOKEN_KEY = 'wisdomhub.refreshToken'

function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // 隐私模式 / 存储被禁用时静默失败，登录态无法持久化
  }
}

function remove(key: string): void {
  try {
    localStorage.removeItem(key)
  } catch {
    // ignore
  }
}

export function getAccessToken(): string | null {
  return read(AUTH_TOKEN_KEY)
}

export function getRefreshToken(): string | null {
  return read(REFRESH_TOKEN_KEY)
}

export function hasAccessToken(): boolean {
  return Boolean(getAccessToken())
}

export function setTokens(tokens: { accessToken: string; refreshToken?: string | null }): void {
  write(AUTH_TOKEN_KEY, tokens.accessToken)
  if (tokens.refreshToken) write(REFRESH_TOKEN_KEY, tokens.refreshToken)
}

export function clearTokens(): void {
  remove(AUTH_TOKEN_KEY)
  remove(REFRESH_TOKEN_KEY)
}
