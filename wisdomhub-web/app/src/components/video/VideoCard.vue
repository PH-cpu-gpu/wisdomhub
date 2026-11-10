<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import AuthorChip from '@/components/business/AuthorChip.vue'
import { resolveApiUrl } from '@/api/http'
import type { InteractionCountVO } from '@/types/interaction'
import type { MyVideoVO, VideoListItemVO } from '@/types/video'
import type { UserBriefVO } from '@/types/user'
import { formatDateTime } from '@/utils'
import { formatVideoDuration } from '@/utils/video'

const props = withDefaults(
  defineProps<{
    video: VideoListItemVO | MyVideoVO
    author?: UserBriefVO | null
    /** 赞/评论/收藏由列表页批量取互动计数后传入 */
    counts?: InteractionCountVO | null
    showAuthor?: boolean
    /** 我的视频列表展示状态徽标 */
    showStatus?: boolean
  }>(),
  {
    showAuthor: true,
    showStatus: false,
  },
)

const coverFailed = ref(false)

const statusLabel = computed(() => {
  if (!props.showStatus || !('status' in props.video)) return ''
  if (props.video.status === 2) return '已下架'
  if (props.video.status === 1) return '已发布'
  return ''
})

const visibleTags = computed(() => props.video.tags.slice(0, 2))
const extraTagCount = computed(() => Math.max(0, props.video.tags.length - 2))
const to = computed(() => ({
  name: 'video-detail',
  params: { videoId: props.video.videoId },
}))

watch(
  () => props.video.coverUrl,
  () => {
    coverFailed.value = false
  },
)
</script>

<template>
  <article class="video-card">
    <RouterLink class="video-card__cover" :to="to" :aria-label="`播放视频：${video.title}`">
      <img
        v-if="!coverFailed && video.coverUrl"
        class="video-card__image"
        :src="resolveApiUrl(video.coverUrl)"
        :alt="video.title"
        loading="lazy"
        @error="coverFailed = true"
      />
      <span v-else class="video-card__placeholder" aria-hidden="true">▶</span>
      <span class="video-card__duration">{{ formatVideoDuration(video.durationMs) }}</span>
      <span v-if="statusLabel" class="video-card__status">{{ statusLabel }}</span>
    </RouterLink>

    <div class="video-card__body">
      <h3 class="video-card__title">
        <RouterLink :to="to">{{ video.title }}</RouterLink>
      </h3>
      <p v-if="video.summary" class="video-card__summary">{{ video.summary }}</p>

      <div v-if="visibleTags.length" class="video-card__tags">
        <span v-for="tag in visibleTags" :key="tag.tagId" class="video-card__tag">
          {{ tag.name }}
        </span>
        <span v-if="extraTagCount" class="video-card__tag">+{{ extraTagCount }}</span>
      </div>

      <div class="video-card__meta">
        <AuthorChip v-if="showAuthor" :author="author" size="xs" />
        <span>{{ video.viewCount }} 次播放</span>
        <time :datetime="video.publishTime">
          {{ formatDateTime(video.publishTime, 'YYYY-MM-DD') }}
        </time>
      </div>

      <div class="video-card__stats">
        <span>赞 <b>{{ counts?.likeCount ?? '—' }}</b></span>
        <span>评论 <b>{{ counts?.commentCount ?? '—' }}</b></span>
        <span>收藏 <b>{{ counts?.favoriteCount ?? '—' }}</b></span>
      </div>
    </div>
  </article>
</template>

<style scoped>
.video-card {
  display: grid;
  gap: var(--space-3);
  min-width: 0;
}

.video-card__cover {
  position: relative;
  display: block;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  border: var(--border);
  background: #14161c;
}

.video-card__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform var(--dur) var(--ease);
}

.video-card__cover:hover .video-card__image {
  transform: scale(1.03);
}

.video-card__placeholder {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  color: var(--acid);
  font-size: 28px;
}

.video-card__duration {
  position: absolute;
  right: 8px;
  bottom: 8px;
  padding: 1px 6px;
  background: rgb(11 11 14 / 78%);
  color: #fff;
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-card__status {
  position: absolute;
  top: 8px;
  left: 8px;
  padding: 1px 6px;
  border: 1px solid var(--acid);
  background: rgb(11 11 14 / 72%);
  color: var(--acid);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-card__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.video-card__title {
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
}

.video-card__title a {
  display: -webkit-box;
  overflow: hidden;
  text-decoration: none;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.video-card__title a:hover {
  color: var(--verm-deep);
}

.video-card__summary {
  display: -webkit-box;
  overflow: hidden;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.video-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.video-card__tag {
  padding: 2px 6px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-card__stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2) var(--space-4);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-card__stats b {
  color: var(--ink);
  font-weight: var(--weight-semibold);
}
</style>
