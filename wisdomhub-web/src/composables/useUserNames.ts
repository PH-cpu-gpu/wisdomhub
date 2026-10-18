import { ref } from 'vue'

import { listUserBriefs, type UserBrief } from '@/api/user'

/**
 * `userId → 用户名与头像` 的共享缓存。
 *
 * 文章/收藏列表只给 `authorId`，而显示"谁写的"要用户名 —— 跨服务 JOIN 被禁止（原则六），
 * 只能再问一次 user-service。这个模块负责：
 *   1. **去重 + 批量**：一页 N 条里作者常常重复，按 ID 去重后只发一次请求（不是 N 次）
 *   2. **跨页面缓存**：同一个作者在别的页面出现时不再问
 *   3. **兜底**：拉不到（用户已删 / 请求失败）就退化成「用户 1234」，不阻塞渲染
 */
const briefById = ref<Record<string, UserBrief>>({})

/** 已经问过的 ID。服务端没返回的（用户被删）也记在这里，免得每页重复问同一个不存在的人 */
const requested = new Set<string>()

export async function loadUserNames(userIds: (string | null | undefined)[]): Promise<void> {
  const missing = [...new Set(userIds.filter((id): id is string => Boolean(id)))].filter(
    (id) => !(id in briefById.value) && !requested.has(id),
  )
  if (missing.length === 0) return

  missing.forEach((id) => requested.add(id))
  try {
    const briefs = await listUserBriefs(missing, { silent: true })
    const next = { ...briefById.value }
    for (const brief of briefs) {
      next[brief.userId] = brief
    }
    // 整体替换而不是逐键赋值：ref 的响应式才会触发
    briefById.value = next
  } catch {
    // 请求失败不算"问过"：下次进页面再试
    missing.forEach((id) => requested.delete(id))
  }
}

/** 显示用的作者名。没拿到就退化成 ID 尾 4 位（19 位雪花 ID 整串没法看） */
export function userName(userId: string | null | undefined): string {
  if (!userId) return ''
  const name = briefById.value[userId]?.username
  return name ? name : `用户 ${String(userId).slice(-4)}`
}

/** 作者头像。没拿到或用户没设头像时返回 null，由 UserAvatar 退化成昵称首字母。 */
export function userAvatar(userId: string | null | undefined): string | null {
  if (!userId) return null
  return briefById.value[userId]?.avatar ?? null
}
