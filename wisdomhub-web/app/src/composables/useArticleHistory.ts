import { recordHistory } from '@/api/modules/user'
import { useAuthStore } from '@/stores'
import type { ID } from '@/types/common'

const reported = new Set<ID>()

/**
 * 浏览历史上报：登录用户每个 SPA 会话内同一篇文章只上报一次。
 * 失败时移除标记，下次进入可重试。
 */
export function useArticleHistory() {
  const auth = useAuthStore()

  async function report(articleId: ID): Promise<void> {
    if (!articleId || !auth.isLoggedIn() || reported.has(articleId)) return

    reported.add(articleId)
    try {
      await recordHistory(articleId, { skipErrorToast: true })
    } catch {
      reported.delete(articleId)
    }
  }

  return { report }
}
