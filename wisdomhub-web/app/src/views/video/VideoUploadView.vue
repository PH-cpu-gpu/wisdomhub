<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { publish } from '@/api/modules/video'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhTextarea from '@/components/base/WhTextarea.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useSeo } from '@/composables/useSeo'
import { useVideoUpload, type UploadPhase } from '@/composables/useVideoUpload'
import { useDictStore } from '@/stores'
import type { ID } from '@/types/common'
import {
  ACCEPTED_VIDEO_TYPES,
  ACCEPT_ATTRIBUTE,
  DAILY_UPLOAD_LIMIT,
  MAX_VIDEO_DURATION_SECONDS,
  MAX_VIDEO_SIZE_BYTES,
  MEDIA_ASSET_STATUS_LABEL,
} from '@/types/video'
import { formatBytes, formatVideoDuration } from '@/utils/video'

const dict = useDictStore()
const tags = computed(() => dict.tags)
const {
  file,
  durationMs,
  phase,
  progress,
  speedBps,
  error,
  asset,
  resumeHint,
  active,
  selectFile,
  start,
  pause,
  retry,
  reset,
  restore,
  forgetPending,
} = useVideoUpload()

const title = ref('')
const summary = ref('')
const tagIds = ref<ID[]>([])
const publishing = ref(false)
const publishedId = ref<ID | null>(null)
const dragging = ref(false)

const PHASE_LABEL: Record<UploadPhase, string> = {
  idle: '待开始',
  validating: '读取信息中',
  ticketing: '申请凭证中',
  uploading: '上传中',
  paused: '已暂停',
  completing: '确认上传中',
  processing: '转码/审核中',
  ready: '可发布',
  failed: '失败',
}

const STEP_ITEMS = ['选择文件', '上传', '转码审核', '发布信息'] as const

const stepIndex = computed(() => {
  if (publishedId.value) return STEP_ITEMS.length
  if (phase.value === 'ready') return 3
  if (phase.value === 'processing') return 2
  if (phase.value === 'failed' && asset.value) return 2
  if (['ticketing', 'uploading', 'paused', 'completing'].includes(phase.value)) return 1
  return 0
})

const phaseLabel = computed(() => PHASE_LABEL[phase.value])
const statusLabel = computed(() =>
  asset.value ? (MEDIA_ASSET_STATUS_LABEL[asset.value.status] ?? '处理中') : '',
)

const canStart = computed(
  () =>
    Boolean(file.value) &&
    !active() &&
    phase.value !== 'processing' &&
    phase.value !== 'ready' &&
    !publishedId.value,
)

const startLabel = computed(() => {
  if (resumeHint.value) return '继续上传'
  if (phase.value === 'failed') return '重新上传'
  return '开始上传'
})

const canPublish = computed(
  () =>
    phase.value === 'ready' &&
    Boolean(title.value.trim()) &&
    !publishing.value &&
    !publishedId.value,
)

const acceptHint = computed(() =>
  ACCEPTED_VIDEO_TYPES.map((item) => item.label).join(' / '),
)

const limitHint = computed(
  () =>
    `单个文件不超过 ${formatBytes(MAX_VIDEO_SIZE_BYTES)}、${MAX_VIDEO_DURATION_SECONDS / 60} 分钟，每日 ${DAILY_UPLOAD_LIMIT} 次`,
)

const fileSizeLabel = computed(() => (file.value ? formatBytes(file.value.size) : '—'))
const durationLabel = computed(() => {
  if (durationMs.value === null) return '时长未知'
  return formatVideoDuration(durationMs.value)
})
const speedLabel = computed(() => (speedBps.value > 0 ? `${formatBytes(speedBps.value)}/s` : ''))

const assetDurationLabel = computed(() => formatVideoDuration(asset.value?.durationMs))
const assetResolutionLabel = computed(() =>
  asset.value?.width && asset.value?.height ? `${asset.value.width}×${asset.value.height}` : '—',
)

async function handleSelect(next: File): Promise<void> {
  publishedId.value = null
  resetPublishForm()
  await selectFile(next)
}

function onFileChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const next = input.files?.[0]
  input.value = ''
  if (next) void handleSelect(next)
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  if (active()) return
  const next = event.dataTransfer?.files?.[0]
  if (next) void handleSelect(next)
}

function onDragOver(event: DragEvent): void {
  event.preventDefault()
  if (!active()) dragging.value = true
}

function onDragLeave(): void {
  dragging.value = false
}

function startUpload(): void {
  void start()
}

async function retryUpload(): Promise<void> {
  await retry()
}

function pauseUpload(): void {
  pause()
}

async function resetUpload(): Promise<void> {
  if (active()) {
    const ok = await confirm({
      title: '移除文件',
      message: '当前上传会中断，确定移除这个文件吗？',
      confirmText: '移除',
      danger: true,
    })
    if (!ok) return
  }
  reset()
  publishedId.value = null
  resetPublishForm()
}

function toggleTag(tagId: ID): void {
  if (tagIds.value.includes(tagId)) {
    tagIds.value = tagIds.value.filter((id) => id !== tagId)
    return
  }
  if (tagIds.value.length >= 10) {
    toast.info('最多选择 10 个标签')
    return
  }
  tagIds.value = [...tagIds.value, tagId]
}

function resetPublishForm(): void {
  title.value = ''
  summary.value = ''
  tagIds.value = []
}

async function submitPublish(): Promise<void> {
  if (!asset.value || publishing.value) return
  const nextTitle = title.value.trim()
  if (!nextTitle) {
    toast.info('请填写标题')
    return
  }
  if (nextTitle.length > 100) {
    toast.info('标题不能超过 100 个字符')
    return
  }

  publishing.value = true
  try {
    const id = await publish(
      {
        assetId: asset.value.assetId,
        title: nextTitle,
        summary: summary.value.trim() || undefined,
        tagIds: [...tagIds.value],
      },
      { skipErrorToast: true },
    )
    publishedId.value = id
    forgetPending()
    toast.success('视频已发布')
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '发布失败，请稍后重试。')
  } finally {
    publishing.value = false
  }
}

function startAnother(): void {
  reset()
  publishedId.value = null
  resetPublishForm()
}

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  if (!active()) return
  event.preventDefault()
  event.returnValue = ''
}

onBeforeRouteLeave(async () => {
  if (!active()) return true
  return await confirm({
    title: '离开上传页',
    message: '上传尚未完成，离开会中断本次上传；凭证过期前可回来继续。',
    confirmText: '离开',
    danger: true,
  })
})

onMounted(() => {
  window.addEventListener('beforeunload', handleBeforeUnload)
  void dict.loadTags()
  void restore()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', handleBeforeUnload)
})

useSeo({
  title: '上传视频',
  description: '选择视频直传对象存储，转码与审核完成后即可发布。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="video-upload">
      <header class="video-upload__head">
        <p class="video-upload__eyebrow">[ VIDEO / UPLOAD ]</p>
        <h1>上传视频</h1>
        <p class="video-upload__lede">
          视频直接上传到对象存储，转码与审核在后台完成。支持 {{ acceptHint }}，{{ limitHint }}。
        </p>
      </header>

      <ol class="video-steps" aria-label="上传步骤">
        <li
          v-for="(step, index) in STEP_ITEMS"
          :key="step"
          class="video-step"
          :class="{ 'is-active': index === stepIndex, 'is-done': index < stepIndex }"
        >
          <span class="video-step__index">{{ String(index + 1).padStart(2, '0') }}</span>
          <span class="video-step__label">{{ step }}</span>
        </li>
      </ol>

      <section class="video-panel">
        <header class="video-panel__head">
          <h2>选择文件</h2>
          <span>{{ phaseLabel }}</span>
        </header>

        <label
          class="video-drop"
          :class="{ 'is-dragging': dragging, 'is-disabled': active() }"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop.prevent="onDrop"
        >
          <input
            class="video-drop__input"
            type="file"
            :accept="ACCEPT_ATTRIBUTE"
            :disabled="active()"
            @change="onFileChange"
          />
          <span class="video-drop__icon" aria-hidden="true">＋</span>
          <span class="video-drop__title">点击选择，或把视频拖进来</span>
          <span class="video-drop__hint">{{ acceptHint }} · {{ limitHint }}</span>
        </label>

        <div v-if="file" class="video-file">
          <div class="video-file__main">
            <p class="video-file__name">{{ file.name }}</p>
            <p class="video-file__meta">
              <span>{{ fileSizeLabel }}</span>
              <span aria-hidden="true">·</span>
              <span>{{ durationLabel }}</span>
              <span aria-hidden="true">·</span>
              <span>{{ phase === 'validating' ? '读取中…' : phaseLabel }}</span>
            </p>
          </div>
          <div class="video-file__actions">
            <WhButton v-if="canStart" size="sm" variant="primary" @click="startUpload">
              {{ startLabel }}
            </WhButton>
            <WhButton
              v-if="phase === 'uploading' || phase === 'ticketing'"
              size="sm"
              variant="ghost"
              @click="pauseUpload"
            >
              暂停
            </WhButton>
            <WhButton
              v-if="phase === 'failed' || phase === 'paused'"
              size="sm"
              variant="ghost"
              @click="retryUpload"
            >
              重试
            </WhButton>
            <WhButton size="sm" variant="ghost" :disabled="active()" @click="resetUpload">
              移除
            </WhButton>
          </div>
        </div>

        <p v-if="resumeHint" class="video-note">
          检测到未完成的上传：继续会重新传输文件，但不会消耗新的上传额度。
        </p>
        <p v-if="error" class="video-error" role="alert">{{ error }}</p>
      </section>

      <section v-if="phase === 'uploading' || phase === 'paused' || progress > 0" class="video-panel">
        <header class="video-panel__head">
          <h2>上传进度</h2>
          <span>{{ phaseLabel }}</span>
        </header>
        <div
          class="video-progress"
          role="progressbar"
          :aria-valuenow="progress"
          aria-valuemin="0"
          aria-valuemax="100"
        >
          <span class="video-progress__bar" :style="{ width: `${progress}%` }"></span>
        </div>
        <div class="video-progress__meta">
          <span>{{ progress }}%</span>
          <span v-if="speedLabel">{{ speedLabel }}</span>
        </div>
        <p v-if="phase === 'paused'" class="video-note">
          已暂停。凭证过期前点「重试」会重新传输文件，不会重新申请额度。
        </p>
      </section>

      <section v-if="asset" class="video-panel">
        <header class="video-panel__head">
          <h2>处理状态</h2>
          <span>{{ statusLabel }}</span>
        </header>
        <dl class="video-status">
          <div>
            <dt>时长</dt>
            <dd>{{ assetDurationLabel }}</dd>
          </div>
          <div>
            <dt>分辨率</dt>
            <dd>{{ assetResolutionLabel }}</dd>
          </div>
          <div>
            <dt>文件大小</dt>
            <dd>{{ formatBytes(asset.fileSize) }}</dd>
          </div>
        </dl>
        <p v-if="phase === 'processing'" class="video-note">
          转码与审核在后台进行，本页每 3 秒自动刷新，可以先去做别的事。
        </p>
        <p v-if="phase === 'failed' && asset.failReason" class="video-error" role="alert">
          {{ asset.failReason }}
        </p>
      </section>

      <section v-if="phase === 'ready' && !publishedId" class="video-panel">
        <header class="video-panel__head">
          <h2>发布信息</h2>
          <span>可发布</span>
        </header>

        <div class="video-field">
          <label class="video-field__label" for="video-title">
            <span>标题</span>
            <span>{{ title.length }}/100</span>
          </label>
          <WhInput
            id="video-title"
            v-model="title"
            :maxlength="100"
            placeholder="用一句话说清视频讲什么"
          />
        </div>

        <div class="video-field">
          <label class="video-field__label" for="video-summary">
            <span>简介</span>
            <span>{{ summary.length }}/1000</span>
          </label>
          <WhTextarea
            id="video-summary"
            v-model="summary"
            :maxlength="1000"
            :rows="3"
            auto-resize
            placeholder="补充内容要点、适合谁看（可选）"
          />
        </div>

        <div class="video-field">
          <div class="video-field__label">
            <span>标签</span>
            <span>{{ tagIds.length }}/10</span>
          </div>
          <div class="video-tag-picker">
            <button
              v-for="tag in tags"
              :key="tag.tagId"
              class="video-tag"
              :class="{ 'is-active': tagIds.includes(tag.tagId) }"
              type="button"
              :disabled="publishing || (tagIds.length >= 10 && !tagIds.includes(tag.tagId))"
              @click="toggleTag(tag.tagId)"
            >
              {{ tag.name }}
            </button>
          </div>
          <p class="video-field__hint">从已有标签库选择，最多 10 个。</p>
        </div>

        <div class="video-publish-actions">
          <WhButton
            variant="primary"
            size="md"
            :loading="publishing"
            :disabled="!canPublish"
            @click="submitPublish"
          >
            发布视频
          </WhButton>
          <WhButton variant="ghost" size="md" :disabled="publishing" @click="startAnother">
            再传一个
          </WhButton>
        </div>
      </section>

      <section v-if="publishedId" class="video-panel video-panel--success">
        <p class="video-upload__eyebrow">[ PUBLISHED ]</p>
        <h2>视频已发布</h2>
        <p class="video-success__meta">
          视频 ID：{{ publishedId }}。可前往视频详情页查看与互动，或在「我的视频」中管理。
        </p>
        <div class="video-success__actions">
          <WhButton variant="primary" size="md" @click="startAnother">再传一个</WhButton>
          <RouterLink
            class="video-success__link"
            :to="{ name: 'video-detail', params: { videoId: String(publishedId) } }"
          >
            查看视频
          </RouterLink>
          <RouterLink class="video-success__link" :to="{ name: 'video-mine' }">我的视频</RouterLink>
          <RouterLink class="video-success__link" :to="{ name: 'home' }">回到首页</RouterLink>
        </div>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.video-upload {
  display: grid;
  gap: clamp(20px, 3vw, 32px);
  padding-block: clamp(16px, 4vw, 40px) var(--space-12);
}

.video-upload__head {
  display: grid;
  gap: var(--space-3);
  max-width: 72ch;
}

.video-upload__eyebrow {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.video-upload__head h1 {
  font-family: var(--serif);
  font-size: clamp(1.9rem, 4vw, 2.8rem);
  line-height: var(--leading-tight);
}

.video-upload__lede {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.video-steps {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--space-2);
  padding: 0;
  margin: 0;
  list-style: none;
  border-top: var(--border);
}

.video-step {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  padding-block: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  border-bottom: 2px solid transparent;
}

.video-step__index {
  color: var(--line-strong);
}

.video-step.is-active {
  color: var(--ink);
  border-bottom-color: var(--ink);
}

.video-step.is-done {
  color: var(--ink);
}

.video-step.is-done .video-step__index {
  color: var(--verm-deep);
}

.video-panel {
  display: grid;
  gap: var(--space-4);
  padding: clamp(16px, 3vw, 28px);
  border: var(--border);
  background: var(--paper, transparent);
}

.video-panel__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.video-panel__head h2 {
  font-family: var(--serif);
  font-size: var(--text-xl);
}

.video-panel__head span {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-drop {
  position: relative;
  display: grid;
  justify-items: center;
  gap: var(--space-2);
  padding: clamp(28px, 6vw, 56px) var(--space-4);
  border: 1px dashed var(--line-strong);
  text-align: center;
  cursor: pointer;
  transition:
    border-color var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.video-drop:hover,
.video-drop.is-dragging {
  border-color: var(--ink);
  background: color-mix(in srgb, var(--acid) 12%, transparent);
}

.video-drop.is-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.video-drop__input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: inherit;
}

.video-drop__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--ink);
  font-size: 18px;
  line-height: 1;
}

.video-drop__title {
  font-family: var(--serif);
  font-size: var(--text-lg);
}

.video-drop__hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-file {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-top: var(--space-4);
  border-top: var(--border);
}

.video-file__main {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.video-file__name {
  overflow: hidden;
  font-weight: var(--weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-file__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-file__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.video-progress {
  position: relative;
  height: 8px;
  border: 1px solid var(--ink);
  background: transparent;
}

.video-progress__bar {
  display: block;
  height: 100%;
  background: var(--ink);
  transition: width 180ms linear;
}

.video-progress__meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-status {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--space-3);
  margin: 0;
}

.video-status div {
  display: grid;
  gap: 2px;
}

.video-status dt {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-status dd {
  margin: 0;
  font-weight: var(--weight-semibold);
}

.video-note {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.video-error {
  padding: var(--space-3);
  border: 1px solid var(--verm);
  color: var(--verm-deep);
  font-size: var(--text-sm);
}

.video-field {
  display: grid;
  gap: var(--space-2);
}

.video-field__label {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.video-field__hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.video-tag-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.video-tag {
  padding: 4px 8px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  transition:
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.video-tag.is-active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--acid);
}

.video-tag:disabled {
  opacity: 0.5;
}

.video-panel--success {
  border-color: var(--ink);
}

.video-panel--success h2 {
  font-family: var(--serif);
  font-size: var(--text-xl);
}

.video-success__meta {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.video-publish-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.video-success__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.video-success__link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 640px) {
  .video-file {
    align-items: flex-start;
    flex-direction: column;
  }

  .video-file__actions {
    width: 100%;
  }
}
</style>
