import type { ID } from '@/types/common'
import type { ContentTargetType } from '@/types/interaction'

/** 点赞点击冷却：挡住连点，避免每次来回都打一遍库与 MQ */
const CLICK_COOLDOWN_MS = 500

/**
 * 按目标做点击节流。冷却期内返回 true，调用方直接忽略本次点击；
 * 未在冷却期则记录本次点击并返回 false。
 */
export function useLikeCooldown() {
  const lastClickAt = new Map<string, number>()

  function isThrottled(targetType: ContentTargetType, targetId: ID): boolean {
    const key = `${targetType}:${targetId}`
    const last = lastClickAt.get(key)
    const now = Date.now()
    if (typeof last === 'number' && now - last < CLICK_COOLDOWN_MS) return true
    lastClickAt.set(key, now)
    return false
  }

  return { isThrottled }
}
