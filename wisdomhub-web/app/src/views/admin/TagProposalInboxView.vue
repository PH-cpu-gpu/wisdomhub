<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { listProposals } from '@/api/modules/tag-proposal'
import WhButton from '@/components/base/WhButton.vue'
import WhEmpty from '@/components/base/WhEmpty.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import { useDictStore } from '@/stores'
import type { ID } from '@/types/common'
import type { TagProposalStatus, TagProposalVO } from '@/types/tag-proposal'
import {
  PROPOSAL_STATUS_LABELS,
  PROPOSAL_TYPE_LABELS,
  evidenceSummary,
  formatDateTime,
  proposerLabel,
  relationTypeLabel,
} from '@/utils'

type StatusFilter = 'all' | `${TagProposalStatus}`

const STATUS_TABS = [
  { label: '全部', value: 'all' },
  { label: '待审', value: '0' },
  { label: '已通过', value: '1' },
  { label: '已拒绝', value: '2' },
  { label: '已过期', value: '3' },
]

const dict = useDictStore()
const proposals = ref<TagProposalVO[]>([])
const loading = ref(false)
const error = ref('')
const status = ref<StatusFilter>('all')
const { page, pageSize, total, setTotal } = usePagination({ pageSize: 10 })

let requestId = 0

const emptyHint = computed(() =>
  status.value === 'all'
    ? '被抽取丢弃的实体攒够文章证据后会升级成提案，AI 也可以在对话里主动申请。'
    : '当前状态下没有提案，换个状态看看。',
)

function tagName(tagId: ID | null | undefined): string {
  if (!tagId) return '未知技术'
  return dict.tagMap[tagId]?.name ?? `#${tagId}`
}

function proposalTitle(proposal: TagProposalVO): string {
  if (proposal.proposalType === 1) return proposal.name || proposal.targetKey
  return `${tagName(proposal.fromTagId)} → ${tagName(proposal.toTagId)}`
}

function proposalSubtitle(proposal: TagProposalVO): string {
  if (proposal.proposalType === 1) {
    return [proposal.category, proposal.description].filter(Boolean).join(' · ') || '新增标签'
  }
  return `${relationTypeLabel(proposal.relationType)} · 新增关系`
}

function detailLink(proposal: TagProposalVO) {
  return { name: 'tag-proposal-detail', params: { proposalId: proposal.id } }
}

async function loadProposals(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listProposals(
      {
        status: status.value === 'all' ? undefined : (Number(status.value) as TagProposalStatus),
        page: page.value,
        pageSize: pageSize.value,
      },
      { skipErrorToast: true },
    )
    if (request !== requestId) return

    proposals.value = result.items
    setTotal(result.total)
  } catch (err) {
    if (request !== requestId) return
    proposals.value = []
    setTotal(0)
    error.value = isApiError(err) ? err.message : '提案加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

function changeStatus(value: string): void {
  if (value === status.value) return
  status.value = value as StatusFilter
  if (page.value !== 1) page.value = 1
}

watch([status, page, pageSize], () => void loadProposals(), { immediate: true })

void dict.loadTags()

useSeo({
  title: '提案收件箱',
  description: '管理员查看 AI 与系统提交的标签 / 关系提案，按状态筛选并分页。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="inbox-page">
      <header class="inbox-head">
        <div>
          <p class="inbox-eyebrow">[ TAG GOVERNANCE / ADMIN ]</p>
          <h1>提案收件箱</h1>
          <p class="inbox-lede">
            AI 与系统提交的标签 / 关系提案汇总在这里，只有管理员可见。
            <span v-if="!error">当前共 {{ total }} 条。</span>
          </p>
        </div>
        <div class="inbox-links">
          <RouterLink class="inbox-back" :to="{ name: 'tag-admin' }">标签管理 →</RouterLink>
          <RouterLink class="inbox-back" :to="{ name: 'tag-list' }">去标签库 →</RouterLink>
        </div>
      </header>

      <WhTabs :model-value="status" :items="STATUS_TABS" size="sm" @change="changeStatus" />

      <div v-if="loading" class="inbox-skeletons" aria-live="polite">
        <div v-for="index in 3" :key="index" class="inbox-skeleton">
          <WhSkeleton :lines="3" />
        </div>
        <span class="wh-sr-only">正在加载提案…</span>
      </div>

      <div v-else-if="error" class="inbox-state inbox-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadProposals">重试</WhButton>
      </div>

      <WhEmpty v-else-if="!proposals.length" title="暂无提案" :description="emptyHint" />

      <div v-else class="inbox-list">
        <article v-for="(proposal, index) in proposals" :key="proposal.id" class="proposal-row">
          <span class="proposal-row__index">
            PROP / {{ String((page - 1) * pageSize + index + 1).padStart(2, '0') }}
          </span>

          <div class="proposal-row__body">
            <div class="proposal-row__top">
              <span class="proposal-row__type">
                {{ PROPOSAL_TYPE_LABELS[proposal.proposalType] }}
              </span>
              <span class="proposal-chip" :class="`is-status-${proposal.status}`">
                {{ PROPOSAL_STATUS_LABELS[proposal.status] }}
              </span>
            </div>

            <h2 class="proposal-row__title">
              <RouterLink :to="detailLink(proposal)">{{ proposalTitle(proposal) }}</RouterLink>
            </h2>
            <p class="proposal-row__sub">{{ proposalSubtitle(proposal) }}</p>

            <div class="proposal-row__meta">
              <span>提交人：{{ proposerLabel(proposal.proposerType) }}</span>
              <time :datetime="proposal.createTime">
                提交 {{ formatDateTime(proposal.createTime) }}
              </time>
              <span v-if="proposal.decidedAt">处理 {{ formatDateTime(proposal.decidedAt) }}</span>
              <span v-if="evidenceSummary(proposal.evidence)" class="proposal-row__evidence">
                证据：{{ evidenceSummary(proposal.evidence) }}
              </span>
            </div>
          </div>

          <RouterLink class="proposal-row__action" :to="detailLink(proposal)">详情 →</RouterLink>
        </article>
      </div>

      <div v-if="total > pageSize" class="inbox-pagination">
        <WhPagination v-model:page="page" :page-size="pageSize" :total="total" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.inbox-page {
  display: grid;
  gap: var(--space-4);
  max-width: 1080px;
  margin: 0 auto;
}

.inbox-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.inbox-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.inbox-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.inbox-lede {
  max-width: 64ch;
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.inbox-links {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}

.inbox-back {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  white-space: nowrap;
}

.inbox-back:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.inbox-skeletons {
  display: grid;
  gap: var(--space-4);
  padding-block: var(--space-4);
  border-top: 1px solid var(--line);
}

.inbox-skeleton {
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--line);
}

.inbox-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.inbox-state--error {
  color: var(--verm-deep);
}

.inbox-list {
  display: grid;
  border-top: 1px solid var(--ink);
}

.proposal-row {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-4);
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--line);
}

.proposal-row__index {
  padding-top: 4px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.proposal-row__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.proposal-row__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.proposal-row__type {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
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

.proposal-row__title {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
  overflow-wrap: anywhere;
}

.proposal-row__title a:hover {
  color: var(--verm-deep);
}

.proposal-row__sub {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.proposal-row__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.proposal-row__evidence {
  color: var(--verm-deep);
}

.proposal-row__action {
  padding-top: 4px;
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  white-space: nowrap;
}

.proposal-row__action:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.inbox-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .proposal-row {
    grid-template-columns: minmax(0, 1fr);
  }

  .proposal-row__index,
  .proposal-row__action {
    display: none;
  }

  .proposal-row__title {
    font-size: var(--text-lg);
  }
}
</style>
