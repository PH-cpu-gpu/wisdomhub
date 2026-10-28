<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { list as listDrafts, remove as deleteDraft } from '@/api/modules/draft'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import type { DraftBriefVO } from '@/types/content'
import { formatDateTime } from '@/utils'

const drafts = ref<DraftBriefVO[]>([])
const loading = ref(false)
const error = ref('')

const { page, pageSize, total, setTotal } = usePagination({ pageSize: 10 })

let requestId = 0

async function loadDrafts(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listDrafts(
      { page: page.value, pageSize: pageSize.value },
      { skipErrorToast: true },
    )
    if (request !== requestId) return

    drafts.value = result.items
    setTotal(result.total)
  } catch (err) {
    if (request !== requestId) return
    drafts.value = []
    setTotal(0)
    error.value = isApiError(err) ? err.message : '草稿加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

async function removeDraft(draft: DraftBriefVO): Promise<void> {
  const ok = await confirm({
    title: '删除草稿',
    message: `删除后不可恢复，确定删除《${draft.title || '无标题草稿'}》？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  try {
    await deleteDraft(draft.articleId, { skipErrorToast: true })
    toast.success('草稿已删除')
    if (drafts.value.length === 1 && page.value > 1) page.value -= 1
    else await loadDrafts()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '删除失败，请稍后重试。')
  }
}

watch(
  [page, pageSize],
  () => {
    void loadDrafts()
  },
  { immediate: true },
)

useSeo({
  title: '草稿箱',
  description: '未发布草稿的分页列表、继续编辑与删除。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="drafts-page">
      <header class="drafts-head">
        <div>
          <p class="drafts-eyebrow">[ DRAFTS / ME ]</p>
          <h1>草稿箱</h1>
        </div>
        <p class="drafts-count">{{ total }} 篇草稿</p>
      </header>
      <p class="drafts-lede">未发布的内容会保存在这里，继续编辑或删除。</p>

      <div v-if="loading" class="drafts-state" aria-live="polite">正在加载草稿…</div>

      <div v-else-if="error" class="drafts-state drafts-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadDrafts">重试</WhButton>
      </div>

      <div v-else-if="!drafts.length" class="drafts-empty">
        <p>还没有草稿。</p>
        <RouterLink class="drafts-link" :to="{ name: 'editor-new' }"> 开始写文章 </RouterLink>
      </div>

      <div v-else class="drafts-list">
        <article v-for="draft in drafts" :key="draft.articleId" class="draft-item">
          <RouterLink
            class="draft-main"
            :to="{ name: 'editor-edit', params: { articleId: draft.articleId } }"
          >
            <span class="draft-id">DRAFT / {{ draft.articleId }}</span>
            <h2 :class="{ 'is-untitled': !draft.title }">
              {{ draft.title || '无标题草稿' }}
            </h2>
            <time class="draft-time" :datetime="draft.updateTime">
              最后编辑 {{ formatDateTime(draft.updateTime, 'YYYY-MM-DD HH:mm') }}
            </time>
          </RouterLink>
          <div class="draft-actions">
            <RouterLink
              class="draft-edit"
              :to="{ name: 'editor-edit', params: { articleId: draft.articleId } }"
            >
              继续编辑
            </RouterLink>
            <button class="draft-delete" type="button" @click="removeDraft(draft)">删除</button>
          </div>
        </article>
      </div>

      <div v-if="total > pageSize" class="drafts-pagination">
        <WhPagination v-model:page="page" :page-size="pageSize" :total="total" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.drafts-page {
  display: grid;
  gap: var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
}

.drafts-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.drafts-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.drafts-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.drafts-count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.drafts-lede {
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.drafts-list {
  display: grid;
}

.draft-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding-block: var(--space-4);
  border-bottom: var(--border);
}

.draft-main {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.draft-id {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.draft-main h2 {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-snug);
  transition: color var(--dur) var(--ease);
}

.draft-main:hover h2 {
  color: var(--verm-deep);
}

.draft-main h2.is-untitled {
  color: var(--text-muted);
  font-style: italic;
}

.draft-time {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.draft-actions {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-shrink: 0;
}

.draft-edit {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.draft-delete {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  transition: color var(--dur) var(--ease);
}

.draft-delete:hover {
  color: var(--verm-deep);
}

.drafts-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.drafts-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.drafts-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.drafts-link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.drafts-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .draft-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .draft-actions {
    align-self: flex-end;
  }
}
</style>
