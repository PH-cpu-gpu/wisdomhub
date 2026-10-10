import { ref } from 'vue'

import { getUserProfile, type UserProfile } from '@/api/user'
import { getSession } from '@/utils/auth-storage'
import { readUserId } from '@/utils/jwt'

/**
 * 当前用户资料的**共享缓存**。
 *
 * 为什么放 composable 而不是 `stores/user`：那个 store 刻意只做本地会话读写
 * （请求层要读它，塞进接口调用就成 `http → store → http`）。这里跟 `useAuth` 同一路子。
 *
 * 消费方：顶部导航的头像/昵称、个人中心那一页。**不要**每个组件各拉一次。
 *
 * 注意这份资料里**没有 email**（后端 `UserProfileVO` 对外公开，邮箱不进；要判断有没有绑邮箱
 * 看登录响应里的 `hasEmail`）。
 */
const profile = ref<UserProfile | null>(null)

/** 并发去重：多个组件同时挂载时只发一次请求 */
let pending: Promise<UserProfile | null> | null = null

export function useProfile() {
  /**
   * 拉自己的资料。未登录直接返回 null（不报错——游客页面也会用到这个 composable）。
   * 失败也返回 null：顶部导航退化成「用户名首字母」或「U」，不该因为拉资料失败整页报错。
   */
  function load(force = false): Promise<UserProfile | null> {
    const userId = readUserId(getSession()?.accessToken)
    if (!userId) {
      profile.value = null
      return Promise.resolve(null)
    }
    if (profile.value && !force) return Promise.resolve(profile.value)
    if (pending) return pending

    pending = getUserProfile(userId, { silent: true })
      .then((result) => {
        profile.value = result
        return result
      })
      .catch(() => null)
      .finally(() => {
        pending = null
      })

    return pending
  }

  /** 本地更新（改完资料、传完头像后调，省一次请求） */
  function setProfile(next: UserProfile | null): void {
    profile.value = next
  }

  /** 注销时清掉，避免下一个登录的人看到上一个人的头像 */
  function clear(): void {
    profile.value = null
    pending = null
  }

  return { profile, load, setProfile, clear }
}
