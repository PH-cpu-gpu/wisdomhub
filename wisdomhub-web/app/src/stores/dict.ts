import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { listAll as fetchAllTags, listHot as fetchHotTags } from '@/api/modules/tag'
import type { ID } from '@/types/common'
import type { HotTagVO, TagVO } from '@/types/content'

const CACHE_TTL = 10 * 60 * 1000

export const useDictStore = defineStore('dict', () => {
  const tags = ref<TagVO[]>([])
  const tagsLoadedAt = ref(0)
  const tagsLoading = ref(false)
  let tagsPromise: Promise<void> | null = null

  const hotTags = ref<HotTagVO[]>([])
  const hotLimit = ref(10)
  const hotLoadedAt = ref(0)
  const hotLoading = ref(false)
  const hotPromises = new Map<number, Promise<void>>()

  const tagMap = computed<Record<ID, TagVO>>(() =>
    Object.fromEntries(tags.value.map((tag) => [tag.tagId, tag])),
  )

  function isTagsFresh(): boolean {
    return tags.value.length > 0 && Date.now() - tagsLoadedAt.value < CACHE_TTL
  }

  function isHotFresh(limit: number): boolean {
    return (
      hotTags.value.length > 0 &&
      hotLimit.value === limit &&
      Date.now() - hotLoadedAt.value < CACHE_TTL
    )
  }

  async function loadTags(force = false): Promise<void> {
    if (!force && isTagsFresh()) return
    if (tagsPromise) return tagsPromise

    const promise = (async () => {
      tagsLoading.value = true
      try {
        tags.value = await fetchAllTags()
        tagsLoadedAt.value = Date.now()
      } catch {
        // 保留旧缓存；错误提示由 http 层统一处理
      } finally {
        tagsLoading.value = false
        tagsPromise = null
      }
    })()

    tagsPromise = promise
    return promise
  }

  async function loadHotTags(limit = 10, force = false): Promise<void> {
    const normalized = Math.max(1, Math.trunc(limit))
    if (!force && isHotFresh(normalized)) return

    const existing = hotPromises.get(normalized)
    if (existing) return existing

    const promise = (async () => {
      hotLoading.value = true
      try {
        hotTags.value = await fetchHotTags(normalized)
        hotLimit.value = normalized
        hotLoadedAt.value = Date.now()
      } catch {
        // 保留旧缓存
      } finally {
        hotLoading.value = false
        hotPromises.delete(normalized)
      }
    })()

    hotPromises.set(normalized, promise)
    return promise
  }

  function getTag(tagId: ID): TagVO | undefined {
    return tagMap.value[tagId]
  }

  function clear(): void {
    tags.value = []
    tagsLoadedAt.value = 0
    hotTags.value = []
    hotLimit.value = 10
    hotLoadedAt.value = 0
    tagsPromise = null
    hotPromises.clear()
  }

  return {
    tags,
    tagsLoading,
    hotTags,
    hotLoading,
    tagMap,
    loadTags,
    loadHotTags,
    getTag,
    clear,
  }
})
