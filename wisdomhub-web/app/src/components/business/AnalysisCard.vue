<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { analysis as fetchAnalysis } from '@/api/modules/agent'
import { tags as searchTags } from '@/api/modules/search'
import WhButton from '@/components/base/WhButton.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import { toast } from '@/components/base/toast'
import { isApiError } from '@/api/error'
import type { ID } from '@/types/common'
import type { AgentAnalysisVO } from '@/types/agent'

const props = defineProps<{
  articleId: ID
}>()

const MAX_POLLS = 15
const POLL_START = 3000
const POLL_STEP = 500
const POLL_MAX = 10000

const router = useRouter()

const analysis = ref<AgentAnalysisVO | null>(null)
const loading = ref(false)
const error = ref('')
const timedOut = ref(false)
const resolving = ref('')

let attempts = 0
let timer: ReturnType<typeof setTimeout> | null = null
let requestId = 0

const status = computed(() => {
  if (error.value) return 'error'
  return analysis.value?.status ?? 'pending'
})
const statusLabel = computed(() => {
  if (status.value === 'done') return '已完成'
  if (status.value === 'error') return '分析失败'
  return timedOut.value ? '未完成' : '分析中'
})
const isWaiting = computed(() => status.value === 'pending' && !timedOut.value)
const isTimeout = computed(() => status.value === 'pending' && timedOut.value)
const errorMessage = computed(
  () => error.value || analysis.value?.message || '文章分析失败，请稍后重试。',
)
const topic = computed(() => analysis.value?.topic || '')
const entities = computed(() => analysis.value?.entities ?? [])
const knowledgePoints = computed(() => analysis.value?.knowledgePoints ?? [])

function clearTimer(): void {
  if (timer !== null) {
    clearTimeout(timer)
    timer = null
  }
}

function reset(): void {
  requestId += 1
  clearTimer()
  analysis.value = null
  loading.value = false
  error.value = ''
  timedOut.value = false
  resolving.value = ''
  attempts = 0
}

function pollDelay(attempt: number): number {
  return Math.min(POLL_START + attempt * POLL_STEP, POLL_MAX)
}

async function load(trigger = false): Promise<void> {
  const id = props.articleId
  if (!id) return

  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const data = await fetchAnalysis(id, { skipErrorToast: true }, trigger)
    if (request !== requestId) return

    analysis.value = data
    if (data.status === 'pending') {
      if (attempts >= MAX_POLLS) {
        timedOut.value = true
        clearTimer()
        return
      }
      const attempt = attempts
      attempts += 1
      timedOut.value = false
      clearTimer()
      timer = setTimeout(() => {
        void load()
      }, pollDelay(attempt))
    } else {
      timedOut.value = false
      clearTimer()
    }
  } catch (err) {
    if (request !== requestId) return
    error.value = isApiError(err) ? err.message : 'AI 分析加载失败，请稍后重试。'
    clearTimer()
  } finally {
    if (request === requestId) loading.value = false
  }
}

function retry(): void {
  attempts = 0
  timedOut.value = false
  error.value = ''
  void load(true)
}

async function openEntity(entity: string): Promise<void> {
  if (resolving.value) return

  resolving.value = entity
  try {
    const result = await searchTags(
      { keyword: entity, page: 1, pageSize: 1 },
      { skipErrorToast: true },
    )
    const tag = result.items[0]
    if (tag) {
      void router.push({ name: 'tech-detail', params: { tagId: tag.tagId } })
    } else {
      toast.info('未找到对应标签')
    }
  } catch {
    toast.error('标签搜索失败，请稍后重试')
  } finally {
    resolving.value = ''
  }
}

watch(
  () => props.articleId,
  () => {
    reset()
    void load(true)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  requestId += 1
  clearTimer()
})
</script>

<template>
  <section class="analysis-card">
    <header class="analysis-head">
      <div>
        <p class="analysis-eyebrow">[ AI / ANALYSIS ]</p>
        <h2>AI 分析</h2>
      </div>
      <span class="analysis-status" :class="`is-${status}`">{{ statusLabel }}</span>
    </header>

    <div v-if="isWaiting" class="analysis-pending" aria-live="polite">
      <div class="analysis-skeleton">
        <WhSkeleton width="26%" height="10px" />
        <WhSkeleton :lines="2" height="14px" />
        <WhSkeleton width="42%" height="10px" />
      </div>
      <p class="analysis-hint">正在分析文章的主题、实体与知识点…</p>
    </div>

    <div v-else-if="isTimeout" class="analysis-pending analysis-pending--timeout" aria-live="polite">
      <p class="analysis-timeout__title">分析尚未完成</p>
      <p class="analysis-hint">
        文章可能仍在排队或生成中。稍后重新检查即可；如果长时间没有结果，可以重新发布或编辑文章触发分析。
      </p>
      <WhButton size="sm" variant="ghost" @click="retry">重新检查</WhButton>
    </div>

    <div v-else-if="status === 'error'" class="analysis-error" role="alert">
      <p>{{ errorMessage }}</p>
      <WhButton size="sm" variant="ghost" @click="retry">重试</WhButton>
    </div>

    <div v-else-if="status === 'done'" class="analysis-done">
      <div class="analysis-topic">
        <p class="analysis-label">主题</p>
        <p class="analysis-topic__text">{{ topic || '未命名主题' }}</p>
      </div>

      <div v-if="entities.length" class="analysis-group">
        <p class="analysis-label">实体</p>
        <div class="analysis-entities">
          <button
            v-for="entity in entities"
            :key="entity"
            class="analysis-entity"
            type="button"
            :disabled="Boolean(resolving)"
            :aria-busy="resolving === entity"
            @click="openEntity(entity)"
          >
            {{ entity }}
            <span aria-hidden="true">→</span>
          </button>
        </div>
      </div>

      <div v-if="knowledgePoints.length" class="analysis-group">
        <p class="analysis-label">知识点</p>
        <ol class="analysis-points">
          <li v-for="(point, index) in knowledgePoints" :key="point">
            <span>{{ String(index + 1).padStart(2, '0') }}</span>
            <span>{{ point }}</span>
          </li>
        </ol>
      </div>
    </div>
  </section>
</template>

<style scoped>
.analysis-card {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-6);
  padding: clamp(18px, 3vw, 28px);
  border: 1px solid var(--ink);
  background: var(--surface);
  box-shadow: var(--shadow-hard-sm);
}

.analysis-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.analysis-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.analysis-head h2 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.analysis-status {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
}

.analysis-status.is-done {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
}

.analysis-status.is-error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.analysis-pending,
.analysis-error,
.analysis-done {
  display: grid;
  gap: var(--space-4);
}

.analysis-pending--timeout {
  justify-items: start;
}

.analysis-skeleton {
  display: grid;
  gap: var(--space-3);
}

.analysis-hint {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.analysis-timeout__title {
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
}

.analysis-error {
  justify-items: start;
  padding: var(--space-4);
  border: 1px dashed var(--verm);
  color: var(--verm-deep);
  font-size: var(--text-sm);
}

.analysis-label {
  margin-bottom: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
}

.analysis-topic {
  padding-bottom: var(--space-3);
  border-bottom: var(--border);
}

.analysis-topic__text {
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-snug);
}

.analysis-group {
  display: grid;
}

.analysis-entities {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.analysis-entity {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 9px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  transition:
    border-color var(--dur) var(--ease),
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.analysis-entity:hover:not(:disabled) {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.analysis-entity:disabled {
  opacity: 0.55;
  cursor: wait;
}

.analysis-points {
  display: grid;
  gap: var(--space-2);
}

.analysis-points li {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: var(--space-2);
  padding-block: var(--space-2);
  border-top: var(--border);
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.analysis-points li span:first-child {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

@media (max-width: 640px) {
  .analysis-card {
    box-shadow: none;
  }
}
</style>
