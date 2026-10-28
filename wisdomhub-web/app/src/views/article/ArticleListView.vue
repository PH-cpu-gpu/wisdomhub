<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { list as listArticles } from '@/api/modules/article'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import WhTabs from '@/components/base/WhTabs.vue'
import ArticleCard from '@/components/business/ArticleCard.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { useAsyncData } from '@/composables/useAsyncData'
import { useSeo } from '@/composables/useSeo'
import { useDictStore } from '@/stores'

type Sort = 'new' | 'hot'

const PAGE_SIZE = 10
const SORT_ITEMS = [
  { label: '最新', value: 'new' },
  { label: '最热', value: 'hot' },
]

const route = useRoute()
const router = useRouter()
const dict = useDictStore()

function queryString(value: unknown): string {
  if (Array.isArray(value)) return String(value[0] ?? '').trim()
  return typeof value === 'string' ? value.trim() : ''
}

const page = computed(() => {
  const parsed = Number(queryString(route.query.page))
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1
})
const sort = computed<Sort>(() => (queryString(route.query.sort) === 'hot' ? 'hot' : 'new'))
const tagId = computed(() => queryString(route.query.tagId))
const authorId = computed(() => queryString(route.query.authorId))
const hasFilters = computed(() => Boolean(tagId.value || authorId.value))
const sortHint = computed(() => (sort.value === 'hot' ? '按热度排序' : '按发布时间排序'))

const {
  data: articlePage,
  loading,
  error,
  execute: loadArticles,
} = useAsyncData(
  () =>
    listArticles(
      {
        page: page.value,
        pageSize: PAGE_SIZE,
        sort: sort.value,
        tagId: tagId.value || undefined,
        authorId: authorId.value || undefined,
      },
      { skipErrorToast: true },
    ),
  { immediate: false },
)

const { authors } = useArticleAuthors(() => articlePage.value?.items)
const items = computed(() => articlePage.value?.items ?? [])
const total = computed(() => articlePage.value?.total ?? 0)
const activeTag = computed(() => (tagId.value ? dict.tagMap[tagId.value] : undefined))
const filterAuthor = computed(() => (authorId.value ? authors[authorId.value] : undefined))
const errorMessage = computed(() => {
  if (!error.value) return ''
  return isApiError(error.value) ? error.value.message : '文章加载失败，请稍后重试。'
})
const archiveTop = ref<HTMLElement | null>(null)

interface QueryUpdate {
  page?: number
  sort?: Sort
  tagId?: string
  authorId?: string
}

function pushQuery(next: QueryUpdate): void {
  const query: Record<string, string> = {}
  const nextPage = next.page ?? 1
  if (nextPage > 1) query.page = String(nextPage)

  const nextSort = next.sort ?? sort.value
  if (nextSort === 'hot') query.sort = 'hot'

  const nextTag = next.tagId ?? tagId.value
  if (nextTag) query.tagId = nextTag

  const nextAuthor = next.authorId ?? authorId.value
  if (nextAuthor) query.authorId = nextAuthor

  void router.push({ name: 'article-list', query })
}

function setSort(value: string): void {
  const next: Sort = value === 'hot' ? 'hot' : 'new'
  if (next === sort.value) return
  pushQuery({ sort: next, page: 1 })
}

function setTag(value: string): void {
  if (value === tagId.value) return
  pushQuery({ tagId: value, page: 1 })
}

function setPage(value: number): void {
  if (value === page.value) return
  pushQuery({ page: value })
}

function clearAuthor(): void {
  if (!authorId.value) return
  pushQuery({ authorId: '', page: 1 })
}

function clearFilters(): void {
  void router.push({ name: 'article-list' })
}

watch(
  [page, sort, tagId, authorId],
  () => {
    void loadArticles()
  },
  { immediate: true },
)

watch(page, () => {
  void nextTick(() => {
    archiveTop.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
})

onMounted(() => {
  void dict.loadTags()
})

useSeo({
  title: '文章',
  description: '从最新发布到最热讨论，按标签或作者筛选。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="archive-page">
      <header ref="archiveTop" class="archive-head">
        <p class="archive-eyebrow">[ CONTENT / ARTICLES ]</p>
        <div class="archive-title-row">
          <h1>文章</h1>
          <span class="archive-count">{{ total }} 篇</span>
        </div>
        <p class="archive-lede">从最新发布到最热讨论，按标签或作者筛选。</p>
      </header>

      <div class="archive-toolbar">
        <WhTabs :model-value="sort" :items="SORT_ITEMS" @update:model-value="setSort" />
        <span class="archive-toolbar__hint">{{ sortHint }}</span>
      </div>

      <div class="archive-tags" aria-label="标签筛选">
        <span class="archive-tags__label">标签</span>
        <button
          class="archive-tag"
          :class="{ 'is-active': !tagId }"
          type="button"
          @click="setTag('')"
        >
          全部
        </button>
        <button
          v-for="tag in dict.tags"
          :key="tag.tagId"
          class="archive-tag"
          :class="{ 'is-active': tagId === tag.tagId }"
          type="button"
          @click="setTag(tag.tagId)"
        >
          {{ tag.name }}
        </button>
        <span v-if="dict.tagsLoading" class="archive-tags__loading">标签加载中…</span>
      </div>

      <div v-if="hasFilters" class="archive-active">
        <span class="archive-active__label">当前筛选</span>
        <button v-if="tagId" class="archive-filter" type="button" @click="setTag('')">
          {{ activeTag?.name ?? `标签 ${tagId}` }}
          <span aria-hidden="true">×</span>
        </button>
        <button v-if="authorId" class="archive-filter" type="button" @click="clearAuthor">
          {{ filterAuthor?.username ?? `用户 ${authorId}` }}
          <span aria-hidden="true">×</span>
        </button>
        <button class="archive-clear" type="button" @click="clearFilters">清除全部</button>
      </div>

      <div v-if="loading" class="archive-skeleton" aria-busy="true">
        <div v-for="index in 3" :key="index" class="archive-skeleton__card">
          <WhSkeleton width="28%" height="10px" />
          <WhSkeleton :lines="2" height="18px" />
          <WhSkeleton width="86%" :lines="2" height="10px" />
        </div>
      </div>

      <div v-else-if="errorMessage" class="archive-state archive-state--error" role="alert">
        <p>{{ errorMessage }}</p>
        <WhButton size="sm" variant="ghost" @click="loadArticles">重试</WhButton>
      </div>

      <div v-else-if="!items.length" class="archive-state">
        <p class="archive-state__title">没有找到匹配的文章。</p>
        <p class="archive-state__hint">换个标签或作者，或者清除筛选后再看看。</p>
        <div class="archive-state__actions">
          <WhButton v-if="hasFilters" size="sm" variant="ghost" @click="clearFilters">
            清除筛选
          </WhButton>
          <RouterLink class="archive-state__link" :to="{ name: 'home' }"> 回到首页 </RouterLink>
        </div>
      </div>

      <div v-else class="archive-list">
        <ArticleCard
          v-for="article in items"
          :key="article.articleId"
          :article="article"
          :author="authors[article.authorId]"
        />
      </div>

      <div v-if="total > 0" class="archive-pagination">
        <WhPagination :page="page" :page-size="PAGE_SIZE" :total="total" @update:page="setPage" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.archive-page {
  display: grid;
  gap: clamp(24px, 3vw, 36px);
  max-width: 1080px;
  margin: 0 auto;
}

.archive-head {
  display: grid;
  gap: var(--space-2);
  scroll-margin-top: 96px;
}

.archive-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.archive-title-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
}

.archive-title-row h1 {
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.archive-count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.archive-lede {
  max-width: 52ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.archive-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.archive-toolbar :deep(.wh-tabs) {
  border-bottom: 0;
}

.archive-toolbar__hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.archive-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: var(--border);
}

.archive-tags__label {
  margin-right: var(--space-1);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.archive-tags__loading {
  color: var(--text-muted);
  font-size: var(--text-xs);
}

.archive-tag {
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

.archive-tag:hover {
  border-color: var(--ink);
  color: var(--ink);
}

.archive-tag.is-active {
  border-color: var(--ink);
  background: var(--ink);
  color: var(--acid);
}

.archive-active {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-4);
  border: 1px dashed var(--line-strong);
}

.archive-active__label {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.archive-filter,
.archive-clear {
  border: 0;
  background: transparent;
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.archive-filter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 7px;
  border: 1px solid var(--ink);
  color: var(--ink);
}

.archive-filter:hover {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.archive-clear {
  margin-left: auto;
  padding: 0;
  color: var(--verm-deep);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.archive-list {
  display: grid;
}

.archive-list :deep(.article-card:first-child) {
  border-top: 0;
}

.archive-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

.archive-state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.archive-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.archive-state__title {
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
}

.archive-state__hint {
  line-height: var(--leading-relaxed);
}

.archive-state__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
}

.archive-state__link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.archive-skeleton {
  display: grid;
  gap: 0;
}

.archive-skeleton__card {
  display: grid;
  gap: var(--space-3);
  padding-block: var(--space-5);
  border-top: var(--border);
}

.archive-skeleton__card:first-child {
  border-top: 0;
}

@media (max-width: 640px) {
  .archive-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .archive-active {
    padding: var(--space-3);
  }

  .archive-clear {
    margin-left: 0;
  }

  .archive-state {
    padding: var(--space-5);
  }
}
</style>
