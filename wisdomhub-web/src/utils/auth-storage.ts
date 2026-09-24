import { shallowRef } from 'vue'

/**
 * 会话（token）的持久化与共享状态。
 *
 * 为什么单独一个模块、而不是放进 Pinia store：
 * 请求拦截器每次发请求都要读 token，如果从 store 读，就成了
 * `api/http.ts → stores/user.ts → api/http.ts` 的循环依赖。
 * 所以会话状态下沉到这个与框架其余部分无关的模块，store 和请求层都只依赖它。
 *
 * 为什么用 shallowRef 而不是普通变量：401 时是请求层（http.ts）清掉会话的，
 * 而界面（顶部导航的登录态）在 store 里读它。两边必须看到同一份状态，
 * 否则会出现「token 已失效但页面还显示已登录」。用响应式引用就能自动同步。
 *
 * ⚠️ 安全取舍：token 放 localStorage 意味着任何 XSS 都能把凭证读走。
 * 更稳妥的做法是 refreshToken 放 HttpOnly Cookie、accessToken 只留内存。
 * 当前按 localStorage 实现（B1.8/B1.9 的契约如此），**因为 accessToken 有有效期
 * 且能被后端拉黑**，代价可接受。但不要在这里再存任何别的东西。
 */

export interface AuthSession {
  accessToken: string
  refreshToken?: string
}

const STORAGE_KEY = 'wisdomhub:session'

function readStored(): AuthSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const parsed: unknown = raw ? JSON.parse(raw) : null

    // localStorage 是用户可编辑的，脏数据不能一路流到请求头里
    if (
      typeof parsed !== 'object' ||
      parsed === null ||
      typeof (parsed as AuthSession).accessToken !== 'string'
    ) {
      return null
    }
    return parsed as AuthSession
  } catch {
    // 隐私模式下 localStorage 可能直接抛异常，或存了非 JSON 内容
    return null
  }
}

const session = shallowRef<AuthSession | null>(readStored())

export function getSession(): AuthSession | null {
  return session.value
}

export function getAccessToken(): string | null {
  return session.value?.accessToken ?? null
}

/**
 * 写入会话。
 *
 * 先更新内存再落盘：即使 localStorage 不可用（写不进去），
 * 本次会话内的功能仍然正常，只是刷新后需要重新登录。
 */
export function setSession(next: AuthSession): void {
  session.value = next
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  } catch {
    /* 存不下就算了，内存里还有 */
  }
}

export function clearSession(): void {
  session.value = null
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* 清不掉也无妨，内存里已置空，不会再被读出来 */
  }
}
