import { ref } from 'vue'

import * as authApi from '@/api/auth'
import type { RegisterPayload } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { toAccountPayload } from '@/utils/account'

import { useProfile } from './useProfile'

/**
 * 认证动作。
 *
 * 为什么是 composable 而不是塞进 `stores/user`：那个 store 刻意只做**本地会话的读写**
 * （请求层要读它，放进接口调用就成了 `http → store → http` 的循环依赖）。
 * 但「登录/注销」又不止一处要用（登录页 + 顶部导航的退出菜单），
 * 所以编排放在这里，store 只负责落会话。
 */

/**
 * 当前账号是否已绑邮箱。
 *
 * 模块级 ref：顶部导航与个人中心将来都要读它。**不落 localStorage** ——
 * 换页刷新后丢失，那时不再提示补绑正是想要的行为（auth-storage 也明说了别往里塞别的东西）。
 * `null` 表示「后端没告诉」，此时一律不提示，不做猜测。
 */
const hasEmail = ref<boolean | null>(null)

export function useAuth() {
  const user = useUserStore()

  async function login(account: string, password: string, config?: { silent?: boolean }) {
    const result = await authApi.login(toAccountPayload(account), password, config)
    applySession(result)
    return result
  }

  async function register(payload: RegisterPayload, config?: { silent?: boolean }) {
    const result = await authApi.register(payload, config)
    applySession(result)
    return result
  }

  function applySession(result: authApi.LoginResult): void {
    user.setSession({
      accessToken: result.accessToken,
      refreshToken: result.refreshToken,
    })
    hasEmail.value = result.hasEmail ?? null
    // 拿到 token 立刻把资料拉回来：顶部导航的头像/昵称要靠它
    void useProfile().load(true)
  }

  /**
   * 注销：先让后端把两个 token 拉黑（B1.9），再清本地。
   *
   * 后端失败也要清本地 —— 否则用户会卡在「点了退出还是已登录」。
   * 那种情况下服务端那份 token 要等自然过期，这是已知代价（token 有有效期 + 能拉黑）。
   */
  async function logout(): Promise<void> {
    const session = user.session
    if (session?.refreshToken) {
      try {
        await authApi.logout(session.accessToken, session.refreshToken)
      } catch {
        /* 拉黑失败不阻断本地退出 */
      }
    }
    user.clearSession()
    hasEmail.value = null
    // 清掉资料缓存，免得下一个登录的人看到上一个人的头像
    useProfile().clear()
  }

  /** 绑定 / 改绑邮箱（B1.10）。成功后标记已绑，之后不再提示 */
  async function bindEmail(email: string, code: string): Promise<void> {
    await authApi.bindEmail(email, code)
    hasEmail.value = true
  }

  return { login, register, logout, bindEmail, hasEmail }
}
