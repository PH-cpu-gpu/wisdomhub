import { defineStore } from 'pinia'
import { computed } from 'vue'

import {
  clearSession as clearStoredSession,
  getSession,
  setSession as setStoredSession,
  type AuthSession,
} from '@/utils/auth-storage'
import { readUserId } from '@/utils/jwt'

/**
 * 会话状态。
 *
 * 这里刻意只做**本地会话的读写**，不碰任何接口：
 * 登录（B1.8）、刷新（B1.9）、注销（B1.9）都由 F1.1 的登录页来调，
 * 各自记得在这里 setSession / clearSession。
 *
 * 会话数据本身存在 utils/auth-storage 里（请求层也读它），
 * 这个 store 只是把它包成响应式的、给界面用的视图，
 * 不另存一份 —— 两份状态一定会不同步。
 *
 * ⚠️ 用户资料（头像、昵称）这里还没有。等 B1.10 / B1.11 的接口出来，
 * F1.1 / F1.6 再往上加，别现在就凭想象编字段。
 */
export const useUserStore = defineStore('user', () => {
  const session = computed<AuthSession | null>(() => getSession())

  const isLoggedIn = computed(() => session.value !== null)

  /** 当前 token 的持有者 id。没有就返回 null。 */
  const accessToken = computed(() => session.value?.accessToken ?? null)

  /**
   * 当前登录用户 id（从 accessToken 的 payload 里读，**不验签**）。
   * 只用来判断「这条评论是不是我写的」「这篇文章是不是我的」，不能当权限依据 —— 见 utils/jwt。
   */
  const userId = computed(() => readUserId(session.value?.accessToken))

  function setSession(next: AuthSession): void {
    setStoredSession(next)
  }

  /**
   * 清掉本地会话。**不发请求** —— 这个动作不等于「注销」。
   * 注销要调后端把 token 拉黑（B1.9），届时在 F1.1 里补，别在这里偷偷发请求。
   */
  function clearSession(): void {
    clearStoredSession()
  }

  return { session, isLoggedIn, accessToken, userId, setSession, clearSession }
})
