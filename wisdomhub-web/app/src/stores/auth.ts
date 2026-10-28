import { defineStore } from 'pinia'
import { ref } from 'vue'

import { clearTokens, getAccessToken, getRefreshToken, setTokens } from '@/api/auth-token'
import { refreshAccessToken } from '@/api/http'
import { logout as logoutRequest } from '@/api/modules/auth'
import type { LoginVO } from '@/types/user'
import { decodeJwtUserId, isJwtExpired } from '@/utils/id'
import { storage } from '@/utils/storage'

const HAS_EMAIL_KEY = 'auth.hasEmail'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(null)
  const userId = ref<string | null>(null)
  const hasEmail = ref<boolean | null>(null)

  /** 每次调用都校验 exp，避免 computed 缓存在 token 过期后仍返回 true */
  function isLoggedIn(): boolean {
    return Boolean(accessToken.value) && !isJwtExpired(accessToken.value)
  }

  function hydrate(): void {
    const token = getAccessToken()
    const refresh = getRefreshToken()

    if (!token && !refresh) {
      clearSession()
      return
    }

    accessToken.value = token
    refreshToken.value = refresh
    userId.value = decodeJwtUserId(token)
    hasEmail.value = storage.get<boolean | null>(HAS_EMAIL_KEY, null)
  }

  /** 守卫在每次导航前调用：access 有效直接通过，过期则用 refreshToken 续期 */
  async function ensureSession(): Promise<boolean> {
    hydrate()
    if (isLoggedIn()) return true

    if (!refreshToken.value) {
      clearSession()
      return false
    }

    try {
      await refreshAccessToken(false)
      hydrate()
      const ok = isLoggedIn()
      if (!ok) clearSession()
      return ok
    } catch {
      clearSession()
      return false
    }
  }

  /** 退出优先本地：服务端 logout 失败也清 token */
  async function logout(): Promise<void> {
    const access = accessToken.value
    const refresh = refreshToken.value

    try {
      if (access && refresh) {
        await logoutRequest(
          { accessToken: access, refreshToken: refresh },
          { skipErrorToast: true },
        )
      }
    } catch {
      // 本地退出优先
    } finally {
      clearSession()
    }
  }

  function setSession(session: LoginVO): void {
    setTokens({
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
    })
    accessToken.value = session.accessToken
    refreshToken.value = session.refreshToken
    userId.value = decodeJwtUserId(session.accessToken)

    if (typeof session.hasEmail === 'boolean') {
      hasEmail.value = session.hasEmail
      storage.set(HAS_EMAIL_KEY, session.hasEmail)
    }
  }

  /** 401 刷新成功后由 http 层回调；refreshToken 不轮换，缺省时保留旧值 */
  function updateTokens(tokens: { accessToken: string; refreshToken?: string | null }): void {
    setTokens(tokens)
    accessToken.value = tokens.accessToken
    userId.value = decodeJwtUserId(tokens.accessToken)
    if (tokens.refreshToken) refreshToken.value = tokens.refreshToken
  }

  function updateHasEmail(value: boolean | null): void {
    hasEmail.value = value
    if (value === null) storage.remove(HAS_EMAIL_KEY)
    else storage.set(HAS_EMAIL_KEY, value)
  }

  function clearSession(): void {
    clearTokens()
    accessToken.value = null
    refreshToken.value = null
    userId.value = null
    hasEmail.value = null
    storage.remove(HAS_EMAIL_KEY)
  }

  hydrate()

  return {
    accessToken,
    refreshToken,
    userId,
    hasEmail,
    isLoggedIn,
    hydrate,
    ensureSession,
    logout,
    setSession,
    updateTokens,
    updateHasEmail,
    clearSession,
  }
})
