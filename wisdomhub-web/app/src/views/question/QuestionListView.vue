<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { list } from '@/api/modules/question'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import QuestionCard from '@/components/business/QuestionCard.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useSeo } from '@/composables/useSeo'
import type { QuestionListItemVO } from '@/types/qa'

const PAGE_SIZE = 5

const route = useRoute()
const router = useRouter()

const sort = computed<'new' | 'hot'>(() => (route.query.sort === 'hot' ? 'hot' : 'new'))
const page = computed(() => {
  const parsed = Number(route.query.page)
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1
})
const tabModel = computed({
  get: () => sort.value,
  set: (value: string) => {
    void router.push({ query: { ...route.query, sort: value, page: undefined } })
  },
})
const tabItems = [
  { label: '最新', value: 'new' },
  { label: '最热', value: 'hot' },
]

const questions = ref<QuestionListItemVO[]>([])
const loading = ref(false)
const error = ref('')
const total = ref(0)

const { authors, ensureAuthors } = useAuthorBrief()
let requestId = 0

async function loadQuestions(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await list(
      { sort: sort.value, page: page.value, pageSize: PAGE_SIZE },
      { skipErrorToast: true },
    )
    if (request !== requestId) return

    questions.value = result.items
    total.value = result.total
  } catch (err) {
    if (request !== requestId) return
    questions.value = []
    total.value = 0
    error.value = isApiError(err) ? err.message : '问题加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

function setPage(value: number): void {
  void router.push({ query: { ...route.query, page: value } })
}

watch(
  [sort, page],
  () => {
    void loadQuestions()
  },
  { immediate: true },
)

watch(
  questions,
  () => {
    if (!questions.value.length) return
    void ensureAuthors([...new Set(questions.value.map((question) => question.userId))])
  },
  { immediate: true },
)

useSeo({
  title: '问答',
  description: '最新与最热问题列表，查看已解决标记、回答与收藏数。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="questions-page">
      <header class="questions-head">
        <div>
          <p class="questions-eyebrow">[ Q&amp;A / QUESTIONS ]</p>
          <h1>问答</h1>
          <p class="questions-lede">把问题讲清楚，答案才有机会被看见。</p>
        </div>
        <RouterLink class="questions-ask" :to="{ name: 'ask' }">+ 提问</RouterLink>
      </header>

      <div class="questions-toolbar">
        <WhTabs v-model="tabModel" :items="tabItems" />
        <span class="questions-count">{{ total }} 个问题</span>
      </div>

      <div v-if="loading" class="questions-state" aria-live="polite">正在加载问题…</div>

      <div v-else-if="error" class="questions-state questions-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadQuestions">重试</WhButton>
      </div>

      <div v-else-if="!questions.length" class="questions-empty">
        <p>还没有问题。</p>
        <RouterLink class="questions-link" :to="{ name: 'ask' }"> 提第一个问题 </RouterLink>
      </div>

      <div v-else class="questions-list">
        <QuestionCard
          v-for="question in questions"
          :key="question.questionId"
          :question="question"
          :author="authors[question.userId]"
        />
      </div>

      <div v-if="total > PAGE_SIZE" class="questions-pagination">
        <WhPagination :page="page" :page-size="PAGE_SIZE" :total="total" @update:page="setPage" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.questions-page {
  display: grid;
  gap: var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
}

.questions-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
}

.questions-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.questions-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.questions-lede {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.questions-ask {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding-inline: var(--space-4);
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--sans);
  font-size: var(--text-sm);
  font-weight: var(--weight-semibold);
  text-decoration: none;
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.questions-ask:hover {
  background: var(--ink);
  color: var(--acid);
}

.questions-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.questions-count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.questions-list {
  display: grid;
}

.questions-list :deep(.question-card:first-child) {
  border-top: var(--border);
}

.questions-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.questions-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.questions-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.questions-link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.questions-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .questions-head {
    align-items: flex-start;
  }
}
</style>
