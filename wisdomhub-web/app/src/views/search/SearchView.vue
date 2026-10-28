<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { articles, hotWords, questions, tags, users } from '@/api/modules/search'
import WhAvatar from '@/components/base/WhAvatar.vue'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhSelect from '@/components/base/WhSelect.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import AuthorChip from '@/components/business/AuthorChip.vue'
import SearchHighlight from '@/components/business/SearchHighlight.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useSeo } from '@/composables/useSeo'
import { useDictStore } from '@/stores'
import type {
  ArticleSearchVO,
  HotWordVO,
  QuestionSearchVO,
  TagSearchVO,
  UserSearchVO,
} from '@/types/search'
import { toBackendDateTime } from '@/utils'

type SearchType = 'article' | 'question' | 'user' | 'tag'

const PAGE_SIZE = 10
const TYPES: SearchType[] = ['article', 'question', 'user', 'tag']

const route = useRoute()
const router = useRouter()
const dict = useDictStore()
const { authors, ensureAuthors } = useAuthorBrief()

function queryString(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

const keyword = computed(() => queryString(route.query.keyword).trim())
const type = computed<SearchType>(() => {
  const value = queryString(route.query.type)
  return TYPES.includes(value as SearchType) ? (value as SearchType) : 'article'
})
const page = computed(() => {
  const parsed = Number(route.query.page)
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1
})
const sort = computed(() => {
  const value = queryString(route.query.sort)
  if (type.value === 'user' || type.value === 'tag') {
    return value === 'new' ? 'new' : 'relevance'
  }
  return value === 'new' || value === 'hot' ? value : 'relevance'
})
const tagId = computed(() => queryString(route.query.tagId))
const start = computed(() => queryString(route.query.start))
const end = computed(() => queryString(route.query.end))

const searchInput = ref(keyword.value)
const hot = ref<HotWordVO[]>([])
const hotLoading = ref(false)
const loading = ref(false)
const error = ref('')
const total = ref(0)

const articleResults = ref<ArticleSearchVO[]>([])
const questionResults = ref<QuestionSearchVO[]>([])
const userResults = ref<UserSearchVO[]>([])
const tagResults = ref<TagSearchVO[]>([])

const startInput = ref(start.value)
const endInput = ref(end.value)

let requestId = 0

const tabItems = [
  { label: '文章', value: 'article' },
  { label: '问题', value: 'question' },
  { label: '用户', value: 'user' },
  { label: '标签', value: 'tag' },
]
const tabModel = computed({
  get: () => type.value,
  set: (value: string) => {
    void router.push({
      query: {
        ...route.query,
        type: value,
        page: undefined,
        sort: undefined,
        tagId: undefined,
        start: undefined,
        end: undefined,
      },
    })
  },
})

const sortOptions = computed(() =>
  type.value === 'user' || type.value === 'tag'
    ? [
        { label: '相关', value: 'relevance' },
        { label: '最新', value: 'new' },
      ]
    : [
        { label: '相关', value: 'relevance' },
        { label: '最新', value: 'new' },
        { label: '最热', value: 'hot' },
      ],
)
const sortModel = computed({
  get: () => sort.value,
  set: (value: string | number | null) => {
    const next = value === null ? 'relevance' : String(value)
    pushQuery({ sort: next === 'relevance' ? undefined : next, page: undefined })
  },
})

const tagOptions = computed(() => dict.tags.map((tag) => ({ label: tag.name, value: tag.tagId })))
const tagModel = computed({
  get: () => tagId.value || null,
  set: (value: string | number | null) => {
    pushQuery({ tagId: value === null ? undefined : value, page: undefined })
  },
})

const hasFilters = computed(
  () =>
    sort.value !== 'relevance' ||
    Boolean(tagId.value) ||
    Boolean(start.value) ||
    Boolean(end.value),
)
const hasResults = computed(
  () =>
    articleResults.value.length > 0 ||
    questionResults.value.length > 0 ||
    userResults.value.length > 0 ||
    tagResults.value.length > 0,
)

function pushQuery(patch: Record<string, string | number | undefined>): void {
  void router.push({ query: { ...route.query, ...patch } })
}

function submitSearch(): void {
  const value = searchInput.value.trim()
  if (!value) return
  pushQuery({ keyword: value, page: undefined })
}

function searchHot(value: string): void {
  searchInput.value = value
  submitSearch()
}

function applyDates(): void {
  pushQuery({
    start: startInput.value || undefined,
    end: endInput.value || undefined,
    page: undefined,
  })
}

function clearFilters(): void {
  startInput.value = ''
  endInput.value = ''
  pushQuery({
    sort: undefined,
    tagId: undefined,
    start: undefined,
    end: undefined,
    page: undefined,
  })
}

function setPage(value: number): void {
  pushQuery({ page: value })
}

async function loadHotWords(): Promise<void> {
  hotLoading.value = true
  try {
    hot.value = await hotWords({ days: 7, size: 10 }, { skipErrorToast: true })
  } catch {
    hot.value = []
  } finally {
    hotLoading.value = false
  }
}

async function loadResults(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''
  total.value = 0
  articleResults.value = []
  questionResults.value = []
  userResults.value = []
  tagResults.value = []

  const startTime = start.value ? toBackendDateTime(`${start.value}T00:00:00`) : undefined
  const endTime = end.value ? toBackendDateTime(`${end.value}T23:59:59`) : undefined

  try {
    if (type.value === 'article') {
      const result = await articles(
        {
          keyword: keyword.value || undefined,
          sort: sort.value as 'relevance' | 'new' | 'hot',
          tagId: tagId.value || undefined,
          startTime,
          endTime,
          page: page.value,
          pageSize: PAGE_SIZE,
        },
        { skipErrorToast: true },
      )
      if (request !== requestId) return
      articleResults.value = result.items
      total.value = result.total
      if (result.items.length) {
        void ensureAuthors([...new Set(result.items.map((item) => item.authorId))])
      }
    } else if (type.value === 'question') {
      const result = await questions(
        {
          keyword: keyword.value || undefined,
          sort: sort.value as 'relevance' | 'new' | 'hot',
          startTime,
          endTime,
          page: page.value,
          pageSize: PAGE_SIZE,
        },
        { skipErrorToast: true },
      )
      if (request !== requestId) return
      questionResults.value = result.items
      total.value = result.total
      if (result.items.length) {
        void ensureAuthors([...new Set(result.items.map((item) => item.userId))])
      }
    } else if (type.value === 'user') {
      const result = await users(
        {
          keyword: keyword.value || undefined,
          sort: sort.value as 'relevance' | 'new',
          page: page.value,
          pageSize: PAGE_SIZE,
        },
        { skipErrorToast: true },
      )
      if (request !== requestId) return
      userResults.value = result.items
      total.value = result.total
    } else {
      const result = await tags(
        {
          keyword: keyword.value || undefined,
          sort: sort.value as 'relevance' | 'new',
          page: page.value,
          pageSize: PAGE_SIZE,
        },
        { skipErrorToast: true },
      )
      if (request !== requestId) return
      tagResults.value = result.items
      total.value = result.total
    }
  } catch (err) {
    if (request !== requestId) return
    error.value = isApiError(err) ? err.message : '搜索失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

watch(
  [keyword, type, page, sort, tagId, start, end],
  () => {
    startInput.value = start.value
    endInput.value = end.value
    void loadResults()
  },
  { immediate: true },
)

watch(keyword, () => {
  searchInput.value = keyword.value
})

onMounted(() => {
  void loadHotWords()
  void dict.loadTags()
})

useSeo({
  title: '搜索',
  description: '搜索文章、问题、用户与标签。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="search-page">
      <header class="search-head">
        <p class="search-eyebrow">[ SEARCH ]</p>
        <h1>搜索</h1>
        <form class="search-bar" @submit.prevent="submitSearch">
          <WhInput
            v-model="searchInput"
            size="lg"
            placeholder="搜索文章、问题、用户或标签"
            aria-label="搜索关键词"
            clearable
          />
          <WhButton type="submit" size="lg">搜索</WhButton>
        </form>
        <div v-if="hot.length" class="search-hot">
          <span class="search-hot__label">热搜</span>
          <button
            v-for="item in hot"
            :key="item.keyword"
            class="search-hot__item"
            type="button"
            @click="searchHot(item.keyword)"
          >
            {{ item.keyword }}
          </button>
        </div>
        <p v-else-if="hotLoading" class="search-hot__hint">正在加载热搜…</p>
      </header>

      <div class="search-toolbar">
        <WhTabs v-model="tabModel" :items="tabItems" />
        <span class="search-total">{{ total }} 条结果</span>
      </div>

      <section class="search-filters" aria-label="搜索筛选">
        <WhSelect v-model="sortModel" :options="sortOptions" size="sm" aria-label="排序" />
        <WhSelect
          v-if="type === 'article'"
          v-model="tagModel"
          :options="tagOptions"
          placeholder="全部标签"
          size="sm"
          clearable
          aria-label="标签筛选"
        />
        <template v-if="type === 'article' || type === 'question'">
          <input
            v-model="startInput"
            class="search-date"
            type="date"
            aria-label="开始日期"
            @change="applyDates"
          />
          <span class="search-date__sep">—</span>
          <input
            v-model="endInput"
            class="search-date"
            type="date"
            aria-label="结束日期"
            @change="applyDates"
          />
        </template>
        <button v-if="hasFilters" class="search-clear" type="button" @click="clearFilters">
          清空筛选
        </button>
      </section>

      <div v-if="loading" class="search-state" aria-live="polite">正在搜索…</div>

      <div v-else-if="error" class="search-state search-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadResults">重试</WhButton>
      </div>

      <div v-else-if="!hasResults" class="search-empty">
        <p>{{ keyword ? '没有找到匹配结果。' : '输入关键词，或从热搜开始。' }}</p>
        <RouterLink class="search-link" :to="{ name: 'home' }">返回首页</RouterLink>
      </div>

      <div v-else class="search-results">
        <template v-if="type === 'article'">
          <article v-for="article in articleResults" :key="article.articleId" class="search-result">
            <p class="search-result__eyebrow">[ ARTICLE ]</p>
            <h2 class="search-result__title">
              <RouterLink :to="`/article/${article.articleId}`">
                <SearchHighlight :html="article.highlight.title" :fallback="article.title" />
              </RouterLink>
            </h2>
            <p class="search-result__summary">
              <SearchHighlight :html="article.highlight.summary" :fallback="article.summary" />
            </p>
            <div class="search-result__meta">
              <AuthorChip :author="authors[article.authorId]" size="xs" />
              <span>{{ article.viewCount }} 阅读</span>
              <span>{{ article.likeCount }} 赞</span>
            </div>
            <div v-if="article.tags.length" class="search-result__tags">
              <RouterLink
                v-for="tag in article.tags"
                :key="tag.tagId"
                :to="{ name: 'article-list', query: { tagId: tag.tagId } }"
              >
                {{ tag.name }}
              </RouterLink>
            </div>
          </article>
        </template>

        <template v-else-if="type === 'question'">
          <article
            v-for="question in questionResults"
            :key="question.questionId"
            class="search-result"
          >
            <p class="search-result__eyebrow">[ QUESTION ]</p>
            <h2 class="search-result__title">
              <RouterLink
                :to="{ name: 'question-detail', params: { questionId: question.questionId } }"
              >
                <SearchHighlight :html="question.highlight.title" :fallback="question.title" />
              </RouterLink>
            </h2>
            <p class="search-result__summary">
              <SearchHighlight :html="question.highlight.content" :fallback="question.content" />
            </p>
            <div class="search-result__meta">
              <AuthorChip :author="authors[question.userId]" size="xs" />
              <span>{{ question.answerCount }} 回答</span>
              <span>{{ question.favoriteCount }} 收藏</span>
            </div>
          </article>
        </template>

        <template v-else-if="type === 'user'">
          <RouterLink
            v-for="user in userResults"
            :key="user.userId"
            class="search-result search-result--user"
            :to="{ name: 'user-profile', params: { userId: user.userId } }"
          >
            <WhAvatar :src="user.avatar ?? undefined" :name="user.username" size="md" />
            <div>
              <h2 class="search-result__title">
                <SearchHighlight :html="user.highlight.username" :fallback="user.username" />
              </h2>
              <p class="search-result__summary">
                <SearchHighlight :html="user.highlight.bio" :fallback="user.bio" />
              </p>
            </div>
          </RouterLink>
        </template>

        <template v-else>
          <RouterLink
            v-for="tag in tagResults"
            :key="tag.tagId"
            class="search-result search-result--tag"
            :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
          >
            <p class="search-result__eyebrow">[ TAG / {{ tag.category }} ]</p>
            <h2 class="search-result__title">
              <SearchHighlight :html="tag.highlight.name" :fallback="tag.name" />
            </h2>
            <p class="search-result__summary">
              <SearchHighlight :html="tag.highlight.description" :fallback="tag.description" />
            </p>
          </RouterLink>
        </template>
      </div>

      <div v-if="total > PAGE_SIZE" class="search-pagination">
        <WhPagination :page="page" :page-size="PAGE_SIZE" :total="total" @update:page="setPage" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.search-page {
  display: grid;
  gap: var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
}

.search-head {
  display: grid;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--ink);
}

.search-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.search-head h1 {
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.search-bar {
  display: flex;
  gap: var(--space-2);
}

.search-bar :deep(.wh-field) {
  flex: 1;
}

.search-hot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.search-hot__label {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.search-hot__item {
  padding: 2px 8px;
  border: 1px solid var(--line-strong);
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  cursor: pointer;
}

.search-hot__item:hover {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.search-hot__hint {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.search-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.search-total {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.search-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-block: var(--space-2);
}

.search-date {
  min-height: 32px;
  padding: 4px 8px;
  border: 1px solid var(--line-strong);
  background: var(--surface);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.search-date__sep {
  color: var(--text-muted);
}

.search-clear {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  cursor: pointer;
}

.search-results {
  display: grid;
}

.search-result {
  display: grid;
  gap: var(--space-2);
  padding-block: var(--space-4);
  border-bottom: var(--border);
  color: inherit;
  text-decoration: none;
}

.search-result__eyebrow {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.search-result__title {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-snug);
}

.search-result__title a {
  color: inherit;
  text-decoration: none;
}

.search-result__title a:hover {
  color: var(--verm-deep);
}

.search-result__summary {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.search-result__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.search-result__tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.search-result__tags a {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: none;
}

.search-result__tags a:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.search-result--user {
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
}

.search-result--tag {
  cursor: pointer;
}

.search-result--tag:hover .search-result__title {
  color: var(--verm-deep);
}

.search-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.search-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.search-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.search-link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.search-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .search-bar {
    align-items: stretch;
    flex-direction: column;
  }

  .search-filters {
    align-items: flex-start;
  }
}
</style>
