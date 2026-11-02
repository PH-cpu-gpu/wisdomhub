import { defineStore } from 'pinia'
import { ref } from 'vue'

import { isApiError } from '@/api/error'
import { listProposals } from '@/api/modules/tag-proposal'

/**
 * 管理员身份在后端 Nacos（wisdomhub.admin.user-ids），登录态里没有角色字段。
 * 这里用一次受保护接口探测：200=管理员，401/403=非管理员，
 * 其它错误算「未知」——入口隐藏，但路由守卫放行让页面自己展示错误态。
 */
export const useAdminStore = defineStore('admin', () => {
  const userId = ref<string | null>(null)
  const isAdmin = ref<boolean | null>(null)
  const checking = ref(false)
  let pending: Promise<boolean | null> | null = null

  async function probe(): Promise<boolean | null> {
    try {
      await listProposals({ page: 1, pageSize: 1 }, { skipErrorToast: true })
      return true
    } catch (error) {
      if (isApiError(error) && (error.code === 401 || error.code === 403)) return false
      return null
    }
  }

  /** 同一个用户只探一次；未知结果下次再探 */
  async function ensure(currentUserId: string | null): Promise<boolean | null> {
    if (!currentUserId) {
      reset()
      return false
    }
    if (userId.value === currentUserId && isAdmin.value !== null) return isAdmin.value
    if (pending) return pending

    userId.value = currentUserId
    checking.value = true
    pending = probe()
      .then((result) => {
        if (userId.value === currentUserId) isAdmin.value = result
        return result
      })
      .finally(() => {
        pending = null
        checking.value = false
      })

    return pending
  }

  function reset(): void {
    userId.value = null
    isAdmin.value = null
    checking.value = false
    pending = null
  }

  return {
    userId,
    isAdmin,
    checking,
    ensure,
    reset,
  }
})
