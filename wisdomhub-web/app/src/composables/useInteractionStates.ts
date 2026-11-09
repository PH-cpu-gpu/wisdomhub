import { computed, reactive, ref, toValue, watch, type MaybeRefOrGetter } from 'vue'

import { getState } from '@/api/modules/interaction'
import type { ID } from '@/types/common'
import type { ContentTargetType, InteractionStateVO, InteractionTargetRef } from '@/types/interaction'

import { patchInteractionCount } from './useInteractionCounts'

const CACHE_TTL = 60 * 1000
/** /state 只能单目标查，一次页内并发压到 5，避免一页回答打出几十个请求 */
const MAX_CONCURRENT = 5

const cache = reactive<Record<string, InteractionStateVO>>({})
const fetchedAt = new Map<string, number>()
const inFlight = new Set<string>()
const pending = ref(0)

export type InteractionStateSource = MaybeRefOrGetter<InteractionTargetRef[] | null | undefined>

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

/** 回写单目标状态，同时把计数同步到共用缓存，避免两套数字 */
export function patchInteractionState(
  targetType: ContentTargetType,
  targetId: ID,
  patch: Partial<InteractionStateVO>,
): void {
  const key = keyOf(targetType, targetId)
  const current = cache[key] ?? {
    liked: false,
    collected: false,
    likeCount: 0,
    commentCount: 0,
    favoriteCount: 0,
  }
  const next = { ...current, ...patch }
  cache[key] = next
  fetchedAt.set(key, Date.now())

  // 只同步本次真正改到的计数字段，避免把未加载的项覆盖成 0
  const countPatch: {
    likeCount?: number
    commentCount?: number
    favoriteCount?: number
  } = {}
  if (patch.likeCount !== undefined) countPatch.likeCount = patch.likeCount
  if (patch.commentCount !== undefined) countPatch.commentCount = patch.commentCount
  if (patch.favoriteCount !== undefined) countPatch.favoriteCount = patch.favoriteCount
  if (Object.keys(countPatch).length) {
    patchInteractionCount(targetType, targetId, countPatch)
  }
}

/**
 * 用户维度的互动状态（liked / collected）。
 * 只用于详情类页面：列表批量接口不返回这些字段。
 */
export function useInteractionStates(...sources: InteractionStateSource[]) {
  const loading = computed(() => pending.value > 0)

  async function ensureStates(targets: readonly InteractionTargetRef[]): Promise<void> {
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
      for (const group of chunk(missing, MAX_CONCURRENT)) {
        await Promise.allSettled(
          group.map(async (target) => {
            try {
              const state = await getState(target.targetType, target.targetId, {
                skipErrorToast: true,
              })
              cache[keyOf(target.targetType, target.targetId)] = state
              fetchedAt.set(keyOf(target.targetType, target.targetId), Date.now())
              patchInteractionCount(target.targetType, target.targetId, {
                likeCount: state.likeCount,
                commentCount: state.commentCount,
                favoriteCount: state.favoriteCount,
              })
            } catch {
              // 单个目标失败不影响同页其他回答
            }
          }),
        )
      }
    } finally {
      keys.forEach((key) => inFlight.delete(key))
      pending.value = Math.max(0, pending.value - 1)
    }
  }

  /** 取缓存状态；未加载或失败返回 undefined，由调用方按未点赞/未收藏渲染 */
  function stateFor(
    targetType: ContentTargetType,
    targetId: ID,
  ): InteractionStateVO | undefined {
    return cache[keyOf(targetType, targetId)]
  }

  const patchState = patchInteractionState

  for (const source of sources) {
    watch(
      () => toValue(source),
      (targets) => {
        if (!targets?.length) return
        void ensureStates(targets)
      },
      { immediate: true },
    )
  }

  return { states: cache, loading, stateFor, ensureStates, patchState }
}
