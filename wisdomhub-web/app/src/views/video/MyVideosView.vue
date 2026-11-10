<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import {
  mine as listMyVideos,
  offline as offlineVideo,
  online as onlineVideo,
  remove as removeVideo,
} from '@/api/modules/video'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import VideoCard from '@/components/video/VideoCard.vue'
import { useInteractionCounts } from '@/composables/useInteractionCounts'
import { useSeo } from '@/composables/useSeo'
import type { ID } from '@/types/common'
import { CONTENT_TARGET_TYPE } from '@/types/interaction'
import type { MyVideoVO } from '@/types/video'

const PAGE_SIZE = 8

const route = useRoute()
const router = useRouter()

function queryString(value: unknown): string {
  if (Array.isArray(value)) return String(value[0] ?? '').trim()
  return typeof value === 'string' ? value.trim() : ''
}

const page = computed(() => {
  const parsed = Number(queryString(route.query.page))
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1
})

const items = ref<MyVideoVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const actingId = ref<ID | null>(null)

const { countsFor } = useInteractionCounts(() =>
  items.value.map((item) => ({
    targetType: CONTENT_TARGET_TYPE.VIDEO,
    targetId: item.videoId,
  })),
)

let request = 0

async function loadVideos(): Promise<void> {
  const current = (request += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listMyVideos(
      { page: page.value, pageSize: PAGE_SIZE },
      { skipErrorToast: true },
    )
    if (current !== request) return

    items.value = result.items
    total.value = result.total
  } catch (err) {
    if (current !== request) return
    items.value = []
    total.value = 0
    error.value = isApiError(err) ? err.message : '我的视频加载失败，请稍后重试。'
  } finally {
    if (current === request) loading.value = false
  }
}

function setPage(value: number): void {
  if (value === page.value) return
  void router.push({
    name: 'video-mine',
    query: value > 1 ? { page: String(value) } : {},
  })
}

async function afterChange(): Promise<void> {
  if (items.value.length === 1 && page.value > 1) {
    setPage(page.value - 1)
    return
  }
  await loadVideos()
}

async function handleOffline(item: MyVideoVO): Promise<void> {
  if (actingId.value) return
  const ok = await confirm({
    title: '下架视频',
    message: `确定下架「${item.title}」？下架后会从列表和搜索中消失，可随时重新发布。`,
    confirmText: '下架',
  })
  if (!ok) return
  actingId.value = item.videoId
  try {
    await offlineVideo(item.videoId, { skipErrorToast: true })
    toast.success('视频已下架')
    await afterChange()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '下架失败，请稍后重试。')
  } finally {
    actingId.value = null
  }
}

async function handleOnline(item: MyVideoVO): Promise<void> {
  if (actingId.value) return
  const ok = await confirm({
    title: '重新发布',
    message: `确定重新发布「${item.title}」？`,
    confirmText: '重新发布',
  })
  if (!ok) return
  actingId.value = item.videoId
  try {
    await onlineVideo(item.videoId, { skipErrorToast: true })
    toast.success('视频已重新发布')
    await afterChange()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '重新发布失败，请稍后重试。')
  } finally {
    actingId.value = null
  }
}

async function handleDelete(item: MyVideoVO): Promise<void> {
  if (actingId.value) return
  const ok = await confirm({
    title: '删除视频',
    message: `确定删除「${item.title}」？删除后评论、点赞和收藏会一并清理，且不可恢复。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  actingId.value = item.videoId
  try {
    await removeVideo(item.videoId, { skipErrorToast: true })
    toast.success('视频已删除')
    await afterChange()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '删除失败，请稍后重试。')
  } finally {
    actingId.value = null
  }
}

watch(page, () => void loadVideos(), { immediate: true })

useSeo({
  title: '我的视频',
  description: '管理自己发布的视频：查看状态、播放数与详情。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="my-videos">
      <header class="my-videos__head">
        <div>
          <p class="my-videos__eyebrow">[ VIDEO / MINE ]</p>
          <h1>我的视频</h1>
          <p class="my-videos__lede">包含已发布与已下架；转码中的资产尚未发布，不会出现在这里。</p>
        </div>
        <div class="my-videos__actions">
          <RouterLink class="my-videos__link" :to="{ name: 'video-list' }">全部视频</RouterLink>
          <RouterLink class="my-videos__cta" :to="{ name: 'video-upload' }">上传视频</RouterLink>
        </div>
      </header>

      <div v-if="loading" class="my-videos__grid" aria-live="polite">
        <div v-for="index in 3" :key="index" class="my-videos__skeleton"></div>
      </div>

      <div v-else-if="error" class="my-videos__state my-videos__state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadVideos">重试</WhButton>
      </div>

      <div v-else-if="!items.length" class="my-videos__state">
        <p>你还没有发布过视频。</p>
        <RouterLink class="my-videos__link" :to="{ name: 'video-upload' }">去上传第一个视频</RouterLink>
      </div>

      <div v-else class="my-videos__grid">
        <div v-for="item in items" :key="item.videoId" class="my-videos__item">
          <VideoCard
            :video="item"
            :counts="countsFor(CONTENT_TARGET_TYPE.VIDEO, item.videoId)"
            :show-author="false"
            show-status
          />
          <div class="my-videos__item-actions">
            <WhButton
              v-if="item.status === 1"
              size="sm"
              variant="ghost"
              :loading="actingId === item.videoId"
              :disabled="actingId !== null && actingId !== item.videoId"
              @click="handleOffline(item)"
            >
              下架
            </WhButton>
            <WhButton
              v-if="item.status === 2"
              size="sm"
              variant="ghost"
              :loading="actingId === item.videoId"
              :disabled="actingId !== null && actingId !== item.videoId"
              @click="handleOnline(item)"
            >
              重新发布
            </WhButton>
            <WhButton
              size="sm"
              variant="danger"
              :loading="actingId === item.videoId"
              :disabled="actingId !== null && actingId !== item.videoId"
              @click="handleDelete(item)"
            >
              删除
            </WhButton>
          </div>
        </div>
      </div>

      <div v-if="total > PAGE_SIZE" class="my-videos__pagination">
        <WhPagination :page="page" :page-size="PAGE_SIZE" :total="total" @update:page="setPage" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.my-videos {
  display: grid;
  gap: clamp(20px, 3vw, 32px);
  padding-block: clamp(16px, 4vw, 40px) var(--space-12);
}

.my-videos__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.my-videos__eyebrow {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.my-videos__head h1 {
  margin-top: var(--space-2);
  font-family: var(--serif);
  font-size: clamp(1.9rem, 4vw, 2.8rem);
  line-height: var(--leading-tight);
}

.my-videos__lede {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.my-videos__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.my-videos__cta {
  padding: 8px 12px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--acid);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: none;
}

.my-videos__link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.my-videos__item {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.my-videos__item-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-2);
}

.my-videos__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: clamp(16px, 2.5vw, 28px);
}

.my-videos__skeleton {
  aspect-ratio: 16 / 10;
  border: 1px solid var(--line-strong);
  background: color-mix(in srgb, var(--ink) 6%, transparent);
}

.my-videos__state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.my-videos__state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.my-videos__pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .my-videos__grid {
    grid-template-columns: 1fr;
  }
}
</style>
