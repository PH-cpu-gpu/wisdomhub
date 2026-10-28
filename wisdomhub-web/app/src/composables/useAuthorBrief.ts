import { computed, reactive, ref } from 'vue'

import { listUserBrief } from '@/api/modules/user'
import type { ID } from '@/types/common'
import type { UserBriefVO } from '@/types/user'

const CACHE_TTL = 5 * 60 * 1000
const CACHE_CHUNK = 50

const cache = reactive<Record<ID, UserBriefVO>>({})
const fetchedAt = new Map<ID, number>()
const inFlight = new Set<ID>()
const pending = ref(0)

function isFresh(id: ID): boolean {
  const at = fetchedAt.get(id)
  return typeof at === 'number' && Date.now() - at < CACHE_TTL
}

function chunk<T>(items: T[], size: number): T[][] {
  const result: T[][] = []
  for (let index = 0; index < items.length; index += size) {
    result.push(items.slice(index, index + size))
  }
  return result
}

/**
 * 文章卡片 / 评论 / 通知共用的作者信息缓存。
 * 组件调用 ensureAuthors(ids) 后，从 authors[id] 取昵称头像；5 分钟 TTL。
 */
export function useAuthorBrief() {
  const loading = computed(() => pending.value > 0)

  async function ensureAuthors(ids: ID[]): Promise<void> {
    const missing = [...new Set(ids)].filter((id) => {
      if (!id) return false
      if (inFlight.has(id)) return false
      if (!cache[id]) return true
      return !isFresh(id)
    })

    if (!missing.length) return

    missing.forEach((id) => inFlight.add(id))
    pending.value += 1

    try {
      for (const group of chunk(missing, CACHE_CHUNK)) {
        try {
          const users = await listUserBrief(group, { skipErrorToast: true })
          const now = Date.now()
          for (const user of users) {
            cache[user.userId] = user
            fetchedAt.set(user.userId, now)
          }
        } catch {
          // 单批失败不阻塞其他批；下次调用会重试
        }
      }
    } finally {
      missing.forEach((id) => inFlight.delete(id))
      pending.value = Math.max(0, pending.value - 1)
    }
  }

  function getAuthor(id: ID): UserBriefVO | undefined {
    return cache[id]
  }

  function clear(): void {
    for (const key of Object.keys(cache)) delete cache[key]
    fetchedAt.clear()
    inFlight.clear()
    pending.value = 0
  }

  return {
    authors: cache,
    loading,
    ensureAuthors,
    getAuthor,
    clear,
  }
}
