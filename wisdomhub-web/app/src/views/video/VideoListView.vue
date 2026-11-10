<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { list as listVideos } from '@/api/modules/video'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhSelect from '@/components/base/WhSelect.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import VideoCard from '@/components/video/VideoCard.vue'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useInteractionCounts } from '@/composables/useInteractionCounts'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore, useDictStore } from '@/stores'
import { CONTENT_TARGET_TYPE } from '@/types/interaction'
import type { VideoListItemVO } from '@/types/video'

const PAGE_SIZE = 8

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const dict = useDictStore()
void dict.loadTags()

function queryString(value: unknown): string {
  if (Array.isArray(value)) return String(value[0] ?? '').trim()
  return typeof value === 'string' ? value.trim() : ''
}

const page = computed(() => {
  const parsed = Number(queryString(route.query.page))
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1
})
const authorId = computed(() => queryString(route.query.authorId))
const tagId = computed(() => queryString(route.query.tagId))
const tagOptions = computed(() => dict.tags.map((tag) => ({ label: tag.name, value: tag.tagId })))
const tagModel = computed({
  get: () => tagId.value || null,
  set: (value: string | number | null) => {
    const query: Record<string, string> = {}
    if (authorId.value) query.authorId = authorId.value
    if (value !== null && value !== undefined && String(value) !== '') {
      query.tagId = String(value)
    }
    void router.push({ name: 'video-list', query })
  },
})

const items = ref<VideoListItemVO[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')

const { authors, ensureAuthors } = useAuthorBrief()
const { countsFor } = useInteractionCounts(() =>
  items.value.map((item) => ({
    targetType: CONTENT_TARGET_TYPE.VIDEO,
    targetId: item.videoId,
  })),
)

const filterAuthor = computed(() => (authorId.value ? authors[authorId.value] : undefined))

let request = 0

async function loadVideos(): Promise<void> {
  const current = (request += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listVideos(
      {
        page: page.value,
        pageSize: PAGE_SIZE,
        authorId: authorId.value || undefined,
        tagId: tagId.value || undefined,
      },
      { skipErrorToast: true },
    )
    if (current !== request) return

    items.value = result.items
    total.value = result.total
    const ids = new Set(result.items.map((item) => item.authorId))
    if (authorId.value) ids.add(authorId.value)
    if (ids.size) void ensureAuthors([...ids])
  } catch (err) {
    if (current !== request) return
    items.value = []
    total.value = 0
    error.value = isApiError(err) ? err.message : '视频列表加载失败，请稍后重试。'
  } finally {
    if (current === request) loading.value = false
  }
}

function setPage(value: number): void {
  if (value === page.value) return
  const query: Record<string, string> = {}
  if (value > 1) query.page = String(value)
  if (authorId.value) query.authorId = authorId.value
  if (tagId.value) query.tagId = tagId.value
  void router.push({ name: 'video-list', query })
}

function clearAuthor(): void {
  const query: Record<string, string> = {}
  if (tagId.value) query.tagId = tagId.value
  void router.push({ name: 'video-list', query })
}

function clearTag(): void {
  const query: Record<string, string> = {}
  if (authorId.value) query.authorId = authorId.value
  void router.push({ name: 'video-list', query })
}

function clearFilters(): void {
  void router.push({ name: 'video-list' })
}

watch([page, authorId, tagId], () => void loadVideos(), { immediate: true })

useSeo({
  title: '视频',
  description: '技术视频：上传、观看、点赞收藏与评论。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="video-library">
      <header class="video-library__head">
        <div>
          <p class="video-library__eyebrow">[ VIDEO / LIBRARY ]</p>
          <h1>视频</h1>
          <p class="video-library__lede">按发布时间倒序，只展示已发布的视频。</p>
        </div>
        <div class="video-library__actions">
          <RouterLink v-if="auth.isLoggedIn()" class="video-library__link" :to="{ name: 'video-mine' }">
            我的视频
          </RouterLink>
          <RouterLink class="video-library__cta" :to="{ name: 'video-upload' }">上传视频</RouterLink>
        </div>
      </header>

      <div class="video-library__filters">
        <WhSelect
          v-model="tagModel"
          :options="tagOptions"
          clearable
          size="sm"
          placeholder="按标签筛选"
        />
        <div v-if="authorId || tagId" class="video-library__filter">
          <span v-if="authorId">作者：{{ filterAuthor?.username ?? authorId }}</span>
          <span v-if="tagId">标签：{{ dict.getTag(tagId)?.name ?? tagId }}</span>
          <button v-if="authorId" type="button" @click="clearAuthor">清除作者</button>
          <button v-if="tagId" type="button" @click="clearTag">清除标签</button>
          <button type="button" @click="clearFilters">全部清除</button>
        </div>
      </div>

      <div v-if="loading" class="video-library__grid" aria-live="polite">
        <div v-for="index in 4" :key="index" class="video-library__skeleton"></div>
      </div>

      <div v-else-if="error" class="video-library__state video-library__state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadVideos">重试</WhButton>
      </div>

      <div v-else-if="!items.length" class="video-library__state">
        <p>{{ authorId || tagId ? '当前筛选下还没有视频。' : '还没有视频，来发布第一个吧。' }}</p>
        <RouterLink
          v-if="!authorId && !tagId"
          class="video-library__link"
          :to="{ name: 'video-upload' }"
        >
          去上传视频
        </RouterLink>
      </div>

      <div v-else class="video-library__grid">
        <VideoCard
          v-for="item in items"
          :key="item.videoId"
          :video="item"
          :author="authors[item.authorId]"
          :counts="countsFor(CONTENT_TARGET_TYPE.VIDEO, item.videoId)"
        />
      </div>

      <div v-if="total > PAGE_SIZE" class="video-library__pagination">
        <WhPagination :page="page" :page-size="PAGE_SIZE" :total="total" @update:page="setPage" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.video-library {
  display: grid;
  gap: clamp(20px, 3vw, 32px);
  padding-block: clamp(16px, 4vw, 40px) var(--space-12);
}

.video-library__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.video-library__eyebrow {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.video-library__head h1 {
  margin-top: var(--space-2);
  font-family: var(--serif);
  font-size: clamp(1.9rem, 4vw, 2.8rem);
  line-height: var(--leading-tight);
}

.video-library__lede {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.video-library__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.video-library__cta {
  padding: 8px 12px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--acid);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: none;
}

.video-library__link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.video-library__filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.video-library__filters :deep(.wh-select) {
  min-width: 180px;
}

.video-library__filter {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-library__filter button {
  border: 0;
  background: transparent;
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  cursor: pointer;
}

.video-library__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: clamp(16px, 2.5vw, 28px);
}

.video-library__skeleton {
  aspect-ratio: 16 / 10;
  border: 1px solid var(--line-strong);
  background: color-mix(in srgb, var(--ink) 6%, transparent);
}

.video-library__state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.video-library__state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.video-library__pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .video-library__grid {
    grid-template-columns: 1fr;
  }
}
</style>
