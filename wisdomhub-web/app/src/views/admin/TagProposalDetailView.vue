<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { isApiError } from '@/api/error'
import { approveProposal, getProposal, rejectProposal } from '@/api/modules/tag-proposal'
import WhButton from '@/components/base/WhButton.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useSeo } from '@/composables/useSeo'
import { useDictStore } from '@/stores'
import type { ID } from '@/types/common'
import type { TagProposalVO } from '@/types/tag-proposal'
import {
  PROPOSAL_STATUS_LABELS,
  PROPOSAL_TYPE_LABELS,
  evidenceRows,
  formatDateTime,
  proposerLabel,
  relationTypeLabel,
} from '@/utils'

type SubmitAction = '' | 'approve' | 'reject'

const route = useRoute()
const dict = useDictStore()

const proposal = ref<TagProposalVO | null>(null)
const loading = ref(false)
const error = ref('')
const missing = ref(false)
const forbidden = ref(false)
const submitting = ref<SubmitAction>('')

const proposalId = computed(() => String(route.params.proposalId ?? ''))
const isPending = computed(() => proposal.value?.status === 0)
const statusLabel = computed(() =>
  proposal.value ? PROPOSAL_STATUS_LABELS[proposal.value.status] : '',
)
const evidence = computed(() => evidenceRows(proposal.value?.evidence))

let requestId = 0

function tagName(tagId: ID | null | undefined): string {
  if (!tagId) return '未知技术'
  return dict.tagMap[tagId]?.name ?? `#${tagId}`
}

const title = computed(() => {
  const current = proposal.value
  if (!current) return '提案详情'
  if (current.proposalType === 1) return current.name || current.targetKey
  return `${tagName(current.fromTagId)} → ${tagName(current.toTagId)}`
})

async function load(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''
  missing.value = false
  forbidden.value = false

  try {
    const result = await getProposal(proposalId.value, { skipErrorToast: true })
    if (request !== requestId) return
    proposal.value = result
  } catch (err) {
    if (request !== requestId) return
    proposal.value = null
    const code = isApiError(err) ? err.code : 0
    if (code === 404) missing.value = true
    else if (code === 403) forbidden.value = true
    else error.value = isApiError(err) ? err.message : '提案加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

async function approve(): Promise<void> {
  const current = proposal.value
  if (!current || submitting.value) return

  const ok = await confirm({
    title: '通过提案',
    message: `通过后会立即创建${current.proposalType === 1 ? '标签' : '关系'}并投影到知识图谱，确定通过「${title.value}」？`,
    confirmText: '通过',
  })
  if (!ok) return

  await submit('approve')
}

async function reject(): Promise<void> {
  const current = proposal.value
  if (!current || submitting.value) return

  const ok = await confirm({
    title: '拒绝提案',
    message: `拒绝后不再处理这条提案，同一目标需要重新提交才会再次审核，确定拒绝「${title.value}」？`,
    confirmText: '拒绝',
    danger: true,
  })
  if (!ok) return

  await submit('reject')
}

async function submit(action: Exclude<SubmitAction, ''>): Promise<void> {
  if (submitting.value) return

  const id = proposalId.value
  submitting.value = action
  try {
    if (action === 'approve') await approveProposal(id, { skipErrorToast: true })
    else await rejectProposal(id, { skipErrorToast: true })
    toast.success(action === 'approve' ? '提案已通过' : '提案已拒绝')
  } catch (err) {
    const code = isApiError(err) ? err.code : 0
    const message = isApiError(err) ? err.message : '操作失败，请稍后重试。'
    // 409：另一个管理员抢先处理，或落地时资源已被占用；刷新后由最新状态说明情况
    if (code === 409) toast.warning(`${message}，已刷新最新状态`)
    else toast.error(message)
  } finally {
    submitting.value = ''
    await load()
  }
}

watch(proposalId, () => void load(), { immediate: true })

void dict.loadTags()

useSeo({
  title: computed(() => (proposal.value ? `${title.value} · 提案` : '提案详情')),
  description: '提案内容、触发证据与审批操作；同意会立即落地标签或关系。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="detail-page">
      <div class="detail-bar">
        <RouterLink class="detail-back" :to="{ name: 'tag-proposal-inbox' }">
          ← 返回收件箱
        </RouterLink>
        <span class="detail-eyebrow">[ TAG GOVERNANCE / ADMIN ]</span>
      </div>

      <div v-if="loading" class="detail-card" aria-live="polite">
        <WhSkeleton :lines="6" />
        <span class="wh-sr-only">正在加载提案…</span>
      </div>

      <div v-else-if="forbidden || missing" class="detail-state">
        <h1>{{ forbidden ? '没有权限查看该提案' : '提案不存在' }}</h1>
        <p>
          {{
            forbidden
              ? '管理员名单可能在 Nacos 里被调整过，可以重新登录后再试。'
              : '它可能已被处理或删除，回收件箱看看其他提案。'
          }}
        </p>
        <RouterLink class="detail-link" :to="{ name: 'tag-proposal-inbox' }">
          返回收件箱
        </RouterLink>
      </div>

      <div v-else-if="error" class="detail-state detail-state--error" role="alert">
        <h1>加载失败</h1>
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="load">重试</WhButton>
      </div>

      <template v-else-if="proposal">
        <header class="detail-head">
          <p class="detail-eyebrow">
            {{ PROPOSAL_TYPE_LABELS[proposal.proposalType] }} / PROPOSAL
          </p>
          <h1>{{ title }}</h1>
          <p class="detail-sub">
            <span class="proposal-chip" :class="`is-status-${proposal.status}`">
              {{ statusLabel }}
            </span>
            <span>提案 #{{ proposal.id }}</span>
            <span>提交 {{ formatDateTime(proposal.createTime) }}</span>
          </p>
        </header>

        <div class="detail-grid">
          <section class="detail-block">
            <h2>提案内容</h2>
            <dl class="detail-list">
              <template v-if="proposal.proposalType === 1">
                <div>
                  <dt>名称</dt>
                  <dd>{{ proposal.name || '—' }}</dd>
                </div>
                <div>
                  <dt>分组</dt>
                  <dd>{{ proposal.category || '—' }}</dd>
                </div>
                <div>
                  <dt>描述</dt>
                  <dd>{{ proposal.description || '—' }}</dd>
                </div>
              </template>
              <template v-else>
                <div>
                  <dt>起点</dt>
                  <dd>{{ tagName(proposal.fromTagId) }}</dd>
                </div>
                <div>
                  <dt>关系</dt>
                  <dd>{{ relationTypeLabel(proposal.relationType) }}</dd>
                </div>
                <div>
                  <dt>终点</dt>
                  <dd>{{ tagName(proposal.toTagId) }}</dd>
                </div>
              </template>
              <div>
                <dt>去重键</dt>
                <dd class="detail-mono">{{ proposal.targetKey }}</dd>
              </div>
            </dl>
          </section>

          <section class="detail-block">
            <h2>流转</h2>
            <dl class="detail-list">
              <div>
                <dt>提交人</dt>
                <dd>{{ proposerLabel(proposal.proposerType) }}</dd>
              </div>
              <div>
                <dt>提交时间</dt>
                <dd>{{ formatDateTime(proposal.createTime) }}</dd>
              </div>
              <div>
                <dt>处理人</dt>
                <dd>{{ proposal.decidedBy ? `#${proposal.decidedBy}` : '—' }}</dd>
              </div>
              <div>
                <dt>处理时间</dt>
                <dd>{{ proposal.decidedAt ? formatDateTime(proposal.decidedAt) : '—' }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <section class="detail-block">
          <h2>触发证据</h2>
          <dl v-if="evidence.length" class="detail-list detail-list--wide">
            <div v-for="row in evidence" :key="row.key">
              <dt>{{ row.label }}</dt>
              <dd>{{ row.value }}</dd>
            </div>
          </dl>
          <p v-else class="detail-muted">这条提案没有附带证据。</p>
        </section>

        <section v-if="isPending" class="detail-actions">
          <div class="detail-actions__text">
            <h2>审批</h2>
            <p class="detail-muted">
              通过会立即创建{{ proposal.proposalType === 1 ? '标签' : '关系' }}并投影到知识图谱；拒绝后同一目标需要重新提交才会再次审核。
            </p>
          </div>
          <div class="detail-actions__buttons">
            <WhButton
              size="sm"
              variant="ghost"
              :disabled="Boolean(submitting)"
              :loading="submitting === 'reject'"
              @click="reject"
            >
              拒绝
            </WhButton>
            <WhButton
              size="sm"
              :disabled="Boolean(submitting)"
              :loading="submitting === 'approve'"
              @click="approve"
            >
              通过
            </WhButton>
          </div>
        </section>

        <section v-else class="detail-actions detail-actions--done">
          <div class="detail-actions__text">
            <h2>已处理</h2>
            <p class="detail-muted">这条提案已经是「{{ statusLabel }}」，不再提供审批操作。</p>
          </div>
          <RouterLink class="detail-link" :to="{ name: 'tag-proposal-inbox' }">
            返回收件箱继续处理 →
          </RouterLink>
        </section>
      </template>
    </div>
  </PageContainer>
</template>

<style scoped>
.detail-page {
  display: grid;
  gap: var(--space-4);
  max-width: 1080px;
  margin: 0 auto;
}

.detail-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.detail-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.detail-back,
.detail-link {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  white-space: nowrap;
}

.detail-back:hover,
.detail-link:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.detail-head {
  display: grid;
  gap: var(--space-2);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--ink);
}

.detail-head h1 {
  font-family: var(--serif);
  font-size: clamp(1.75rem, 4vw, 2.75rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  overflow-wrap: anywhere;
}

.detail-sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.proposal-chip {
  padding: 2px 7px;
  border: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.proposal-chip.is-status-0 {
  border-color: var(--verm);
  background: var(--verm);
  color: var(--text-inverse);
}

.proposal-chip.is-status-1 {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.proposal-chip.is-status-2 {
  border-color: var(--line-strong);
  color: var(--text-muted);
}

.proposal-chip.is-status-3 {
  border-color: var(--violet);
  color: var(--violet);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
}

.detail-card,
.detail-block {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  border: var(--border);
  background: var(--surface);
}

.detail-block h2 {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-wider);
  color: var(--text-muted);
  text-transform: uppercase;
}

.detail-list {
  display: grid;
  gap: var(--space-2);
  margin: 0;
}

.detail-list > div {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr);
  gap: var(--space-3);
  align-items: start;
}

.detail-list--wide > div {
  grid-template-columns: 7rem minmax(0, 1fr);
}

.detail-list dt {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.04em;
}

.detail-list dd {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
  overflow-wrap: anywhere;
}

.detail-mono {
  font-family: var(--mono);
  font-size: var(--text-xs);
}

.detail-muted {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.detail-state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-10) 0;
  border-top: 1px solid var(--ink);
}

.detail-state h1 {
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
}

.detail-state p {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.detail-state--error p {
  color: var(--verm-deep);
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--ink);
  background: var(--bone-2);
}

.detail-actions__text {
  display: grid;
  gap: var(--space-2);
  max-width: 60ch;
}

.detail-actions__text h2 {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  font-weight: var(--weight-bold);
  letter-spacing: var(--tracking-wider);
  color: var(--text-muted);
  text-transform: uppercase;
}

.detail-actions__buttons {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.detail-actions--done {
  border-color: var(--line-strong);
  background: transparent;
}

@media (max-width: 720px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .detail-actions {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
