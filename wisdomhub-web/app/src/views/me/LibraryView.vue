<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { listFavorites } from '@/api/modules/article'
import { uncollect } from '@/api/modules/interaction'
import { listFavorites as listQuestionFavorites } from '@/api/modules/question'
import { clearHistory, deleteHistory, listHistory } from '@/api/modules/user'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import ArticleCard from '@/components/business/ArticleCard.vue'
import QuestionCard from '@/components/business/QuestionCard.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import type { ArticleListItemVO } from '@/types/content'
import type { QuestionListItemVO } from '@/types/qa'
import type { BrowseHistoryVO } from '@/types/user'
import { formatDateTime } from '@/utils'

type LibraryTab = 'favorites' | 'questions' | 'history'

const route = useRoute()
const router = useRouter()

const activeTab = computed<LibraryTab>(() => {
  if (route.name === 'me-history') return 'history'
  if (route.query.tab === 'questions') return 'questions'
  return 'favorites'
})
const tabItems = [
  { label: '文章收藏', value: 'favorites' },
  { label: '问题收藏', value: 'questions' },
  { label: '浏览历史', value: 'history' },
]
const tabModel = computed({
  get: () => activeTab.value,
  set: (value: string) => {
    if (value === 'history') void router.push({ name: 'me-history' })
    else if (value === 'questions') {
      void router.push({ name: 'me-favorites', query: { tab: 'questions' } })
    } else {
      void router.push({ name: 'me-favorites' })
    }
  },
})

const {
  page: favoritePage,
  pageSize: favoritePageSize,
  total: favoriteTotal,
  setTotal: setFavoriteTotal,
  reset: resetFavorites,
} = usePagination({ pageSize: 5 })
const {
  page: historyPage,
  pageSize: historyPageSize,
  total: historyTotal,
  setTotal: setHistoryTotal,
  reset: resetHistory,
} = usePagination({ pageSize: 5 })
const {
  page: questionPage,
  pageSize: questionPageSize,
  total: questionTotal,
  setTotal: setQuestionTotal,
  reset: resetQuestions,
} = usePagination({ pageSize: 5 })

const favorites = ref<ArticleListItemVO[]>([])
const favoritesLoading = ref(false)
const favoritesError = ref('')

const history = ref<BrowseHistoryVO[]>([])
const historyLoading = ref(false)
const historyError = ref('')

const questionFavorites = ref<QuestionListItemVO[]>([])
const questionLoading = ref(false)
const questionError = ref('')

const { authors } = useArticleAuthors(() => favorites.value)
const { ensureAuthors } = useAuthorBrief()

let favoriteRequest = 0
let historyRequest = 0
let questionRequest = 0

async function loadFavorites(): Promise<void> {
  const request = (favoriteRequest += 1)
  favoritesLoading.value = true
  favoritesError.value = ''

  try {
    const result = await listFavorites(
      { page: favoritePage.value, pageSize: favoritePageSize.value },
      { skipErrorToast: true },
    )
    if (request !== favoriteRequest) return

    favorites.value = result.items
    setFavoriteTotal(result.total)
  } catch (error) {
    if (request !== favoriteRequest) return
    favorites.value = []
    setFavoriteTotal(0)
    favoritesError.value = isApiError(error) ? error.message : '收藏加载失败，请稍后重试。'
  } finally {
    if (request === favoriteRequest) favoritesLoading.value = false
  }
}

async function loadHistory(): Promise<void> {
  const request = (historyRequest += 1)
  historyLoading.value = true
  historyError.value = ''

  try {
    const result = await listHistory(
      { page: historyPage.value, pageSize: historyPageSize.value },
      { skipErrorToast: true },
    )
    if (request !== historyRequest) return

    history.value = result.items
    setHistoryTotal(result.total)
  } catch (error) {
    if (request !== historyRequest) return
    history.value = []
    setHistoryTotal(0)
    historyError.value = isApiError(error) ? error.message : '浏览历史加载失败，请稍后重试。'
  } finally {
    if (request === historyRequest) historyLoading.value = false
  }
}

async function loadQuestionFavorites(): Promise<void> {
  const request = (questionRequest += 1)
  questionLoading.value = true
  questionError.value = ''

  try {
    const result = await listQuestionFavorites(
      { page: questionPage.value, pageSize: questionPageSize.value },
      { skipErrorToast: true },
    )
    if (request !== questionRequest) return

    questionFavorites.value = result.items
    setQuestionTotal(result.total)
    if (result.items.length) {
      void ensureAuthors([...new Set(result.items.map((question) => question.userId))])
    }
  } catch (error) {
    if (request !== questionRequest) return
    questionFavorites.value = []
    setQuestionTotal(0)
    questionError.value = isApiError(error) ? error.message : '问题收藏加载失败，请稍后重试。'
  } finally {
    if (request === questionRequest) questionLoading.value = false
  }
}

async function removeFavorite(article: ArticleListItemVO): Promise<void> {
  const ok = await confirm({
    title: '取消收藏',
    message: `确定取消收藏《${article.title}》？`,
    confirmText: '取消收藏',
    danger: true,
  })
  if (!ok) return

  try {
    await uncollect(article.articleId)
    toast.success('已取消收藏')
    if (favorites.value.length === 1 && favoritePage.value > 1) favoritePage.value -= 1
    else await loadFavorites()
  } catch (error) {
    toast.error(isApiError(error) ? error.message : '取消收藏失败，请稍后重试。')
  }
}

async function removeHistoryItem(item: BrowseHistoryVO): Promise<void> {
  const ok = await confirm({
    title: '删除浏览记录',
    message: `确定删除《${item.title}》的浏览记录？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  try {
    await deleteHistory(item.articleId, { skipErrorToast: true })
    toast.success('浏览记录已删除')
    if (history.value.length === 1 && historyPage.value > 1) historyPage.value -= 1
    else await loadHistory()
  } catch (error) {
    toast.error(isApiError(error) ? error.message : '删除失败，请稍后重试。')
  }
}

async function clearAllHistory(): Promise<void> {
  if (!history.value.length) return

  const ok = await confirm({
    title: '清空浏览历史',
    message: '清空后不可恢复，确定继续？',
    confirmText: '清空',
    danger: true,
  })
  if (!ok) return

  try {
    await clearHistory({ skipErrorToast: true })
    resetHistory()
    setHistoryTotal(0)
    history.value = []
    toast.success('浏览历史已清空')
  } catch (error) {
    toast.error(isApiError(error) ? error.message : '清空失败，请稍后重试。')
  }
}

watch(
  [activeTab, favoritePage, favoritePageSize],
  () => {
    if (activeTab.value === 'favorites') void loadFavorites()
  },
  { immediate: true },
)

watch(
  [activeTab, historyPage, historyPageSize],
  () => {
    if (activeTab.value === 'history') void loadHistory()
  },
  { immediate: true },
)

watch(
  [activeTab, questionPage, questionPageSize],
  () => {
    if (activeTab.value === 'questions') void loadQuestionFavorites()
  },
  { immediate: true },
)

watch(activeTab, (tab) => {
  if (tab === 'favorites') resetFavorites()
  else if (tab === 'questions') resetQuestions()
  else resetHistory()
})

useSeo({
  title: '收藏与历史',
  description: '收藏的文章、问题与最近浏览记录。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="library-page">
      <header class="library-head">
        <p class="library-eyebrow">[ LIBRARY / ME ]</p>
        <div class="library-title-row">
          <h1>收藏与历史</h1>
        </div>
        <p class="library-lede">收藏的文章与最近浏览，都留在这里。</p>
      </header>

      <WhTabs v-model="tabModel" :items="tabItems" />

      <section v-if="activeTab === 'favorites'" class="library-panel">
        <header class="library-panel__head">
          <h2>文章收藏</h2>
          <span>{{ favoriteTotal }} 篇</span>
        </header>

        <div v-if="favoritesLoading" class="library-state" aria-live="polite">正在加载收藏…</div>

        <div v-else-if="favoritesError" class="library-state library-state--error" role="alert">
          <p>{{ favoritesError }}</p>
          <WhButton size="sm" variant="ghost" @click="loadFavorites">重试</WhButton>
        </div>

        <div v-else-if="!favorites.length" class="library-empty">
          <p>还没有收藏文章。</p>
          <RouterLink class="library-link" :to="{ name: 'article-list' }"> 去发现文章 </RouterLink>
        </div>

        <div v-else class="library-list">
          <div v-for="article in favorites" :key="article.articleId" class="library-favorite">
            <ArticleCard :article="article" :author="authors[article.authorId]" compact />
            <div class="library-favorite__actions">
              <button
                class="library-action library-action--danger"
                type="button"
                @click="removeFavorite(article)"
              >
                取消收藏
              </button>
            </div>
          </div>
        </div>

        <div v-if="favoriteTotal > favoritePageSize" class="library-pagination">
          <WhPagination
            v-model:page="favoritePage"
            :page-size="favoritePageSize"
            :total="favoriteTotal"
          />
        </div>
      </section>

      <section v-else-if="activeTab === 'questions'" class="library-panel">
        <header class="library-panel__head">
          <h2>问题收藏</h2>
          <span>{{ questionTotal }} 个</span>
        </header>

        <div v-if="questionLoading" class="library-state" aria-live="polite">正在加载问题收藏…</div>

        <div v-else-if="questionError" class="library-state library-state--error" role="alert">
          <p>{{ questionError }}</p>
          <WhButton size="sm" variant="ghost" @click="loadQuestionFavorites">重试</WhButton>
        </div>

        <div v-else-if="!questionFavorites.length" class="library-empty">
          <p>还没有收藏问题。</p>
          <RouterLink class="library-link" :to="{ name: 'question-list' }"> 去逛逛问答 </RouterLink>
        </div>

        <div v-else class="library-question-list">
          <QuestionCard
            v-for="question in questionFavorites"
            :key="question.questionId"
            :question="question"
            :author="authors[question.userId]"
          />
        </div>

        <div v-if="questionTotal > questionPageSize" class="library-pagination">
          <WhPagination
            v-model:page="questionPage"
            :page-size="questionPageSize"
            :total="questionTotal"
          />
        </div>
      </section>

      <section v-else class="library-panel">
        <header class="library-panel__head">
          <h2>浏览历史</h2>
          <div class="library-panel__actions">
            <span>{{ historyTotal }} 条</span>
            <button
              v-if="history.length"
              class="library-action library-action--danger"
              type="button"
              @click="clearAllHistory"
            >
              清空
            </button>
          </div>
        </header>

        <div v-if="historyLoading" class="library-state" aria-live="polite">正在加载浏览历史…</div>

        <div v-else-if="historyError" class="library-state library-state--error" role="alert">
          <p>{{ historyError }}</p>
          <WhButton size="sm" variant="ghost" @click="loadHistory">重试</WhButton>
        </div>

        <div v-else-if="!history.length" class="library-empty">
          <p>还没有浏览记录。</p>
          <RouterLink class="library-link" :to="{ name: 'article-list' }"> 去逛逛文章 </RouterLink>
        </div>

        <div v-else class="library-history-list">
          <article v-for="item in history" :key="item.articleId" class="library-history">
            <RouterLink
              class="library-history__main"
              :to="{ name: 'article-detail', params: { articleId: item.articleId } }"
            >
              <h3>{{ item.title }}</h3>
              <p>{{ item.summary }}</p>
            </RouterLink>
            <div class="library-history__meta">
              <time :datetime="item.updateTime">
                {{ formatDateTime(item.updateTime, 'YYYY-MM-DD HH:mm') }}
              </time>
              <button
                class="library-action library-action--danger"
                type="button"
                @click="removeHistoryItem(item)"
              >
                删除
              </button>
            </div>
          </article>
        </div>

        <div v-if="historyTotal > historyPageSize" class="library-pagination">
          <WhPagination
            v-model:page="historyPage"
            :page-size="historyPageSize"
            :total="historyTotal"
          />
        </div>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.library-page {
  display: grid;
  gap: clamp(20px, 3vw, 32px);
  max-width: 1080px;
  margin: 0 auto;
}

.library-head {
  display: grid;
  gap: var(--space-2);
}

.library-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.library-title-row h1 {
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.library-lede {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.library-panel {
  display: grid;
  gap: var(--space-2);
}

.library-panel__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.library-panel__head h2 {
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.library-panel__head > span,
.library-panel__actions > span {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.library-panel__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.library-question-list {
  display: grid;
}

.library-question-list :deep(.question-card:first-child) {
  border-top: var(--border);
}

.library-favorite {
  display: grid;
  gap: var(--space-2);
  padding-bottom: var(--space-4);
  border-bottom: var(--border);
}

.library-favorite:first-child {
  border-top: var(--border);
}

.library-favorite :deep(.article-card) {
  border-top: 0;
}

.library-favorite__actions {
  display: flex;
  justify-content: flex-end;
}

.library-action {
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

.library-action:hover {
  color: var(--ink);
}

.library-action--danger:hover {
  color: var(--verm-deep);
}

.library-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.library-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.library-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.library-link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.library-history-list {
  display: grid;
}

.library-history {
  display: grid;
  gap: var(--space-2);
  padding-block: var(--space-4);
  border-top: var(--border);
}

.library-history:first-child {
  border-top: 0;
}

.library-history__main {
  display: grid;
  gap: var(--space-2);
  color: inherit;
  text-decoration: none;
}

.library-history__main h3 {
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
  transition: color var(--dur) var(--ease);
}

.library-history__main:hover h3 {
  color: var(--verm-deep);
}

.library-history__main p {
  max-width: 72ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.library-history__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.library-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .library-history__meta {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
