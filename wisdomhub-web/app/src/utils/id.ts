import type { ID } from '@/types/common'

export interface JwtPayload {
  userId?: string | number
  /** 标准 JWT 过期时间，单位：秒 */
  exp?: number
  [key: string]: unknown
}

function base64UrlDecode(input: string): string {
  const base64 = input.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
  return new TextDecoder().decode(bytes)
}

/** 只解码载荷，不校验签名；userId 仅用于 UI，不能作为授权依据 */
export function decodeJwtPayload(token?: string | null): JwtPayload | null {
  if (!token) return null
  const parts = token.split('.')
  if (parts.length < 2) return null

  try {
    // 后端 userId 是 19 位 Long；先给 JSON 里 16 位以上的整数加引号，避免 JSON.parse 丢精度
    const raw = base64UrlDecode(parts[1] ?? '').replace(/:\s*(-?\d{16,})(?=\s*[,}])/g, ': "$1"')
    const payload = JSON.parse(raw) as JwtPayload
    return payload && typeof payload === 'object' ? payload : null
  } catch {
    return null
  }
}

export function decodeJwtUserId(token?: string | null): ID | null {
  const payload = decodeJwtPayload(token)
  if (!payload) return null

  const raw = payload.userId
  if (typeof raw === 'string' && raw) return raw
  if (typeof raw === 'number' && Number.isFinite(raw)) return String(raw)
  return null
}

/** 早于当前时间（可留 skewSeconds 余量）视为过期 */
export function isJwtExpired(token?: string | null, skewSeconds = 0): boolean {
  const payload = decodeJwtPayload(token)
  if (!payload || typeof payload.exp !== 'number') return true
  return payload.exp * 1000 <= Date.now() + skewSeconds * 1000
}
