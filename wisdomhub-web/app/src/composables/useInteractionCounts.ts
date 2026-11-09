import { computed, reactive, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'

import { batchCounts } from '@/api/modules/interaction'
import type { ID } from '@/types/common'
import type { ContentTargetType, InteractionCountVO, InteractionTargetRef } from '@/types/interaction'

const CACHE_TTL = 60 * 1000
/** 后端 /counts 单次上限 */
const MAX_BATCH = 100

const cache = reactive<Record<string, InteractionCountVO>>({})
const fetchedAt = new Map<string, number>()
const inFlight = new Set<string>()
const pending = ref(0)

export type InteractionCountSource = MaybeRefOrGetter<InteractionTargetRef[] | null | undefined>

function keyOf(targetType: ContentTargetType, targetId: ID): string {
  return `${targetType}:${targetId}`
}

function isFresh(key: string): boolean {
  const at = fetchedAt.get(key)
  return typeof at === 'number' && Date.now() - at < CACHE_TTL
}

function chunk<T>(items: T[], size: number): T[][] {
  const result: T[][] = []
  for (let index = 0; index < items.length; index += size) {
    result.push(items.slice(index, index + size))
  }
  return result
}

/** 回写单目标计数；详情页点赞/收藏成功后用，列表返回时不再等一次批取 */
export function patchInteractionCount(
  targetType: ContentTargetType,
  targetId: ID,
  patch: Partial<Pick<InteractionCountVO, 'likeCount' | 'commentCount' | 'favoriteCount'>>,
): void {
  const key = keyOf(targetType, targetId)
  const current = cache[key] ?? {
    targetType,
    targetId,
    likeCount: 0,
    commentCount: 0,
    favoriteCount: 0,
  }
  cache[key] = { ...current, ...patch }
  fetchedAt.set(key, Date.now())
}

/**
 * 互动计数批取：一页只发一次 /api/interaction/counts。
 * 与用户状态（liked/collected）无关，状态由详情页单独调 /state。
 */
export function useInteractionCounts(...sources: InteractionCountSource[]) {
  const loading = computed(() => pending.value > 0)

  async function ensureCounts(targets: readonly InteractionTargetRef[]): Promise<void> {
    const missing: InteractionTargetRef[] = []
    const picked = new Set<string>()

    for (const target of targets) {
      if (!target?.targetId) continue
      const key = keyOf(target.targetType, target.targetId)
      if (picked.has(key) || inFlight.has(key)) continue
      if (cache[key] && isFresh(key)) continue
      picked.add(key)
      missing.push(target)
    }

    if (!missing.length) return

    const keys = missing.map((target) => keyOf(target.targetType, target.targetId))
    keys.forEach((key) => inFlight.add(key))
    pending.value += 1

    try {
      for (const group of chunk(missing, MAX_BATCH)) {
        try {
          const result = await batchCounts(group, { skipErrorToast: true })
          const now = Date.now()
          for (const item of result.items) {
            cache[keyOf(item.targetType, item.targetId)] = item
            fetchedAt.set(keyOf(item.targetType, item.targetId), now)
          }
        } catch {
          // 单批失败不阻塞页面：计数先留空，下次进入会重试
        }
      }
    } finally {
      keys.forEach((key) => inFlight.delete(key))
      pending.value = Math.max(0, pending.value - 1)
    }
  }

  /** 取缓存里的计数；未加载或加载失败时返回 undefined，由调用方决定展示成什么 */
  function countsFor(targetType: ContentTargetType, targetId: ID): InteractionCountVO | undefined {
    return cache[keyOf(targetType, targetId)]
  }

  /** 详情页点赞/收藏成功后回写计数，返回列表时不用再等一次批取 */
  const patchCount = patchInteractionCount

  for (const source of sources) {
    watch(
      () => toValue(source),
      (targets) => {
        if (!targets?.length) return
        void ensureCounts(targets)
      },
      { immediate: true },
    )
  }

  return { counts: cache, loading, countsFor, ensureCounts, patchCount }
}
