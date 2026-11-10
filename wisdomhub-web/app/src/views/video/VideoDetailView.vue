<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { resolveApiUrl } from '@/api/http'
import {
  collect as collectVideo,
  like as likeVideo,
  uncollect as uncollectVideo,
  unlike as unlikeVideo,
} from '@/api/modules/interaction'
import { detail as fetchVideoDetail } from '@/api/modules/video'
import WhButton from '@/components/base/WhButton.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import { toast } from '@/components/base/toast'
import AuthorChip from '@/components/business/AuthorChip.vue'
import CommentSection from '@/components/business/CommentSection.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import VideoPlayer from '@/components/video/VideoPlayer.vue'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useInteractionCounts } from '@/composables/useInteractionCounts'
import { useInteractionStates } from '@/composables/useInteractionStates'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import { CONTENT_TARGET_TYPE } from '@/types/interaction'
import type { VideoDetailVO } from '@/types/video'
import { formatDateTime } from '@/utils'
import { formatVideoDuration } from '@/utils/video'

const MOCK_MODE = import.meta.env.VITE_USE_MOCK === 'true'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const videoId = computed(() => String(route.params.videoId ?? ''))
const detail = ref<VideoDetailVO | null>(null)
const loading = ref(true)
const error = ref('')
const notFound = ref(false)
const likePending = ref(false)
const collectPending = ref(false)
const commentTotal = ref<number | null>(null)

const { authors, ensureAuthors } = useAuthorBrief()
const {
  stateFor,
  patchState,
} = useInteractionStates(() =>
  videoId.value
    ? [{ targetType: CONTENT_TARGET_TYPE.VIDEO, targetId: videoId.value }]
    : [],
)
const { patchCount } = useInteractionCounts()

const videoState = computed(() =>
  detail.value ? stateFor(CONTENT_TARGET_TYPE.VIDEO, detail.value.videoId) : undefined,
)
const author = computed(() => (detail.value ? authors[detail.value.authorId] : undefined))
const statusLabel = computed(() => {
  if (!detail.value) return ''
  if (detail.value.status === 2) return '已下架'
  if (detail.value.status !== 1) return '未发布'
  return ''
})

let request = 0

async function loadDetail(): Promise<void> {
  const id = videoId.value
  const current = (request += 1)
  loading.value = true
  error.value = ''
  notFound.value = false
  detail.value = null
  commentTotal.value = null

  if (!id) {
    loading.value = false
    notFound.value = true
    return
  }

  try {
    const data = await fetchVideoDetail(id, { skipErrorToast: true })
    if (current !== request) return
    detail.value = data
    void ensureAuthors([data.authorId])
  } catch (err) {
    if (current !== request) return
    if (isApiError(err) && err.code === 404) notFound.value = true
    else error.value = isApiError(err) ? err.message : '视频加载失败，请稍后重试。'
  } finally {
    if (current === request) loading.value = false
  }
}

function requireLogin(): boolean {
  if (auth.isLoggedIn()) return true
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function toggleLike(): Promise<void> {
  const current = videoState.value
  if (!detail.value || !current || likePending.value) return
  if (!requireLogin()) return

  const id = detail.value.videoId
  const previous = { liked: current.liked, count: current.likeCount }
  const nextLiked = !previous.liked
  patchState(CONTENT_TARGET_TYPE.VIDEO, id, {
    liked: nextLiked,
    likeCount: Math.max(0, previous.count + (nextLiked ? 1 : -1)),
  })
  likePending.value = true

  try {
    const count = nextLiked
      ? await likeVideo(CONTENT_TARGET_TYPE.VIDEO, id, { skipErrorToast: true })
      : await unlikeVideo(CONTENT_TARGET_TYPE.VIDEO, id, { skipErrorToast: true })
    patchState(CONTENT_TARGET_TYPE.VIDEO, id, {
      liked: nextLiked,
      likeCount: typeof count === 'number' ? count : previous.count,
    })
  } catch (err) {
    patchState(CONTENT_TARGET_TYPE.VIDEO, id, {
      liked: previous.liked,
      likeCount: previous.count,
    })
    toast.error(isApiError(err) ? err.message : '点赞失败，请稍后重试。')
  } finally {
    likePending.value = false
  }
}

async function toggleCollect(): Promise<void> {
  const current = videoState.value
  if (!detail.value || !current || collectPending.value) return
  if (!requireLogin()) return

  const id = detail.value.videoId
  const previous = { collected: current.collected, count: current.favoriteCount }
  const nextCollected = !previous.collected
  patchState(CONTENT_TARGET_TYPE.VIDEO, id, {
    collected: nextCollected,
    favoriteCount: Math.max(0, previous.count + (nextCollected ? 1 : -1)),
  })
  collectPending.value = true

  try {
    const count = nextCollected
      ? await collectVideo(CONTENT_TARGET_TYPE.VIDEO, id, { skipErrorToast: true })
      : await uncollectVideo(CONTENT_TARGET_TYPE.VIDEO, id, { skipErrorToast: true })
    patchState(CONTENT_TARGET_TYPE.VIDEO, id, {
      collected: nextCollected,
      favoriteCount: typeof count === 'number' ? count : previous.count,
    })
  } catch (err) {
    patchState(CONTENT_TARGET_TYPE.VIDEO, id, {
      collected: previous.collected,
      favoriteCount: previous.count,
    })
    toast.error(isApiError(err) ? err.message : '收藏失败，请稍后重试。')
  } finally {
    collectPending.value = false
  }
}

function handleCommentTotal(total: number): void {
  commentTotal.value = total
  if (!detail.value) return
  patchState(CONTENT_TARGET_TYPE.VIDEO, detail.value.videoId, { commentCount: total })
  patchCount(CONTENT_TARGET_TYPE.VIDEO, detail.value.videoId, { commentCount: total })
}

watch(videoId, () => void loadDetail(), { immediate: true })

useSeo({
  title: computed(() => detail.value?.title ?? '视频'),
  description: computed(() => detail.value?.summary ?? '在 WisdomHub 观看技术视频。'),
  type: 'video.other',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="video-detail">
      <div v-if="loading" class="video-detail__loading">
        <WhSkeleton :lines="1" height="28px" />
        <WhSkeleton :lines="3" height="12px" />
        <div class="video-detail__skeleton-player"></div>
      </div>

      <div v-else-if="notFound" class="video-state">
        <p class="video-detail__eyebrow">[ 404 / VIDEO ]</p>
        <h1>视频不存在或已删除</h1>
        <p>链接可能已失效，或者视频已被作者删除。</p>
        <RouterLink class="video-state__link" :to="{ name: 'home' }">回到首页</RouterLink>
      </div>

      <div v-else-if="error" class="video-state video-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadDetail">重试</WhButton>
      </div>

      <template v-else-if="detail">
        <header class="video-detail__head">
          <p class="video-detail__eyebrow">[ VIDEO / {{ detail.videoId }} ]</p>
          <div class="video-detail__title-row">
            <h1>{{ detail.title }}</h1>
            <span v-if="statusLabel" class="video-detail__status">{{ statusLabel }}</span>
          </div>

          <div class="video-detail__meta">
            <AuthorChip :author="author" size="sm" />
            <span class="video-detail__dot" aria-hidden="true">·</span>
            <time :datetime="detail.publishTime">
              {{ formatDateTime(detail.publishTime, 'YYYY年MM月DD日') }}
            </time>
            <span class="video-detail__dot" aria-hidden="true">·</span>
            <span>{{ detail.viewCount }} 次播放</span>
            <span class="video-detail__dot" aria-hidden="true">·</span>
            <span>{{ formatVideoDuration(detail.durationMs) }}</span>
            <span class="video-detail__dot" aria-hidden="true">·</span>
            <span>{{ commentTotal ?? videoState?.commentCount ?? '—' }} 评论</span>
          </div>
        </header>

        <VideoPlayer
          :src="detail.playlistUrl"
          :poster="resolveApiUrl(detail.coverUrl)"
          :title="detail.title"
          :mock-mode="MOCK_MODE"
        />

        <div class="video-detail__body">
          <p v-if="detail.summary" class="video-detail__summary">{{ detail.summary }}</p>

          <div v-if="detail.tags.length" class="video-detail__tags" aria-label="视频标签">
            <span v-for="tag in detail.tags" :key="tag.tagId" class="video-detail__tag">
              {{ tag.name }}
              <span class="video-detail__tag-category">{{ tag.category }}</span>
            </span>
          </div>

          <footer class="video-detail__actions">
            <button
              class="video-action"
              :class="{ 'is-active': videoState?.liked }"
              type="button"
              :aria-pressed="videoState?.liked ?? false"
              :disabled="likePending"
              @click="toggleLike"
            >
              <span aria-hidden="true">♥</span>
              <span>{{ videoState?.liked ? '已赞' : '点赞' }} {{ videoState?.likeCount ?? '—' }}</span>
            </button>
            <button
              class="video-action"
              :class="{ 'is-active': videoState?.collected }"
              type="button"
              :aria-pressed="videoState?.collected ?? false"
              :disabled="collectPending"
              @click="toggleCollect"
            >
              <span aria-hidden="true">☆</span>
              <span
                >{{ videoState?.collected ? '已收藏' : '收藏' }}
                {{ videoState?.favoriteCount ?? '—' }}</span
              >
            </button>
            <span class="video-detail__stat">{{ detail.viewCount }} 次播放</span>
          </footer>
        </div>

        <CommentSection
          :target-id="detail.videoId"
          :target-type="7"
          @total-change="handleCommentTotal"
        />
      </template>
    </div>
  </PageContainer>
</template>

<style scoped>
.video-detail {
  display: grid;
  gap: clamp(16px, 2.5vw, 24px);
  padding-block: clamp(16px, 4vw, 40px) var(--space-12);
}

.video-detail__loading {
  display: grid;
  gap: var(--space-4);
}

.video-detail__skeleton-player {
  aspect-ratio: 16 / 9;
  border: 1px solid var(--line-strong);
  background: color-mix(in srgb, var(--ink) 6%, transparent);
}

.video-detail__head {
  display: grid;
  gap: var(--space-2);
}

.video-detail__eyebrow {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.video-detail__title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-3);
}

.video-detail__title-row h1 {
  font-family: var(--serif);
  font-size: clamp(1.5rem, 3.2vw, 2.2rem);
  line-height: var(--leading-tight);
}

.video-detail__status {
  padding: 2px 8px;
  border: 1px solid var(--verm);
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-detail__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-detail__dot {
  color: var(--line-strong);
}

.video-detail__body {
  display: grid;
  gap: var(--space-4);
}

.video-detail__summary {
  max-width: 78ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  white-space: pre-line;
}

.video-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.video-detail__tag {
  display: inline-flex;
  align-items: baseline;
  gap: 6px;
  padding: 3px 8px;
  border: 1px solid var(--line-strong);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-detail__tag-category {
  color: var(--text-muted);
}

.video-detail__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-4);
  border-top: var(--border);
}

.video-action {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 8px 12px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  transition:
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.video-action:hover:not(:disabled) {
  border-color: var(--ink);
  color: var(--ink);
}

.video-action.is-active {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.video-action:disabled {
  opacity: 0.6;
  cursor: wait;
}

.video-detail__stat {
  margin-left: auto;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.video-state h1 {
  font-family: var(--serif);
  font-size: var(--text-xl);
  color: var(--ink);
}

.video-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.video-state__link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 640px) {
  .video-detail__stat {
    margin-left: 0;
  }
}
</style>
