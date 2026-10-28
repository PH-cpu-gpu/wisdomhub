<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { tags as searchTags } from '@/api/modules/search'
import { listAll, listHot } from '@/api/modules/tag'
import WhButton from '@/components/base/WhButton.vue'
import WhInput from '@/components/base/WhInput.vue'
import SearchHighlight from '@/components/business/SearchHighlight.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useSeo } from '@/composables/useSeo'
import type { HotTagVO, TagVO } from '@/types/content'
import type { TagSearchVO } from '@/types/search'

const CATEGORIES = [
  '人工智能与数据',
  '编程语言',
  '中间件与运维',
  '后端与框架',
  '数据库',
  '前端',
  '架构与分布式',
  '移动与嵌入式',
  '计算机基础',
  '开发实践',
]

const tags = ref<TagVO[]>([])
const hot = ref<HotTagVO[]>([])
const loading = ref(true)
const error = ref('')

const keyword = ref('')
const searchResults = ref<TagSearchVO[]>([])
const searching = ref(false)
const searchError = ref('')
const searched = ref(false)

const groups = computed(() =>
  CATEGORIES.map((category) => ({
    category,
    tags: tags.value.filter((tag) => tag.category === category),
  })).filter((group) => group.tags.length > 0),
)

async function loadTags(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const [all, hotList] = await Promise.all([listAll(), listHot(10, { skipErrorToast: true })])
    tags.value = all
    hot.value = hotList
  } catch (err) {
    error.value = isApiError(err) ? err.message : '标签加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function submitSearch(): Promise<void> {
  const value = keyword.value.trim()
  if (!value) {
    searchResults.value = []
    searched.value = false
    return
  }

  searching.value = true
  searchError.value = ''
  try {
    const result = await searchTags(
      { keyword: value, page: 1, pageSize: 50 },
      { skipErrorToast: true },
    )
    searchResults.value = result.items
    searched.value = true
  } catch (err) {
    searchResults.value = []
    searchError.value = isApiError(err) ? err.message : '标签搜索失败，请稍后重试。'
  } finally {
    searching.value = false
  }
}

function clearSearch(): void {
  keyword.value = ''
  searchResults.value = []
  searching.value = false
  searchError.value = ''
  searched.value = false
}

onMounted(loadTags)

useSeo({
  title: '技术标签',
  description: '按方向浏览技术标签，进入技术详情或对应文章。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="tag-library">
      <header class="tag-library__head">
        <p class="tag-library__eyebrow">[ TAG LIBRARY ]</p>
        <h1>技术标签</h1>
        <p class="tag-library__intro">按方向浏览技术标签，进入技术详情或对应文章。</p>
        <form class="tag-library__search" @submit.prevent="submitSearch">
          <WhInput
            v-model="keyword"
            size="lg"
            placeholder="搜索标签，例如 Spring Boot"
            aria-label="搜索标签"
            clearable
          />
          <WhButton type="submit" size="lg">搜索</WhButton>
          <button v-if="searched" class="tag-library__clear" type="button" @click="clearSearch">
            清空
          </button>
        </form>
      </header>

      <section v-if="hot.length" class="tag-hot">
        <div class="tag-section-title">
          <h2>热门标签</h2>
          <span>[ HOT ]</span>
        </div>
        <div class="tag-hot__list">
          <RouterLink
            v-for="tag in hot"
            :key="tag.tagId"
            class="tag-hot__item"
            :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
          >
            <span>{{ tag.name }}</span>
            <span class="tag-hot__count">{{ tag.articleCount }} 篇</span>
          </RouterLink>
        </div>
      </section>

      <section v-if="searched" class="tag-search">
        <div class="tag-section-title">
          <h2>标签搜索</h2>
          <span>{{ searchResults.length }} 个</span>
        </div>

        <p v-if="searching" class="tag-state" aria-live="polite">正在搜索标签…</p>

        <div v-else-if="searchError" class="tag-state tag-state--error" role="alert">
          <p>{{ searchError }}</p>
          <WhButton size="sm" variant="ghost" @click="submitSearch">重试</WhButton>
        </div>

        <div v-else-if="!searchResults.length" class="tag-empty">没有匹配的标签。</div>

        <div v-else class="tag-search__list">
          <article v-for="tag in searchResults" :key="tag.tagId" class="tag-search__item">
            <div>
              <p class="tag-search__eyebrow">[ {{ tag.category }} ]</p>
              <h3>
                <RouterLink :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }">
                  <SearchHighlight :html="tag.highlight.name" :fallback="tag.name" />
                </RouterLink>
              </h3>
              <p class="tag-search__description">
                <SearchHighlight :html="tag.highlight.description" :fallback="tag.description" />
              </p>
            </div>
            <RouterLink
              class="tag-search__articles"
              :to="{ name: 'article-list', query: { tagId: tag.tagId } }"
            >
              查看文章
            </RouterLink>
          </article>
        </div>
      </section>

      <div v-if="loading" class="tag-state" aria-live="polite">正在加载标签库…</div>

      <div v-else-if="error" class="tag-state tag-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadTags">重试</WhButton>
      </div>

      <div v-else-if="!groups.length" class="tag-empty">标签库暂无内容。</div>

      <section v-else class="tag-groups">
        <div v-for="group in groups" :key="group.category" class="tag-group">
          <div class="tag-section-title">
            <h2>{{ group.category }}</h2>
            <span>{{ group.tags.length }} 个标签</span>
          </div>
          <div class="tag-group__list">
            <article v-for="tag in group.tags" :key="tag.tagId" class="tag-card">
              <div class="tag-card__head">
                <h3>
                  <RouterLink :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }">
                    {{ tag.name }}
                  </RouterLink>
                </h3>
                <span>{{ group.category }}</span>
              </div>
              <p class="tag-card__description">{{ tag.description }}</p>
              <div class="tag-card__actions">
                <RouterLink
                  class="tag-card__link"
                  :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
                >
                  技术页
                </RouterLink>
                <RouterLink
                  class="tag-card__link"
                  :to="{ name: 'article-list', query: { tagId: tag.tagId } }"
                >
                  文章
                </RouterLink>
              </div>
            </article>
          </div>
        </div>
      </section>
    </div>
  </PageContainer>
</template>

<style scoped>
.tag-library {
  display: grid;
  gap: clamp(24px, 4vw, 40px);
  max-width: 1180px;
  margin: 0 auto;
}

.tag-library__head {
  display: grid;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--ink);
}

.tag-library__eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.tag-library__head h1 {
  font-family: var(--serif);
  font-size: clamp(2rem, 5vw, 3.4rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.tag-library__intro {
  max-width: 52ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.tag-library__search {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-2);
}

.tag-library__search :deep(.wh-field) {
  flex: 1;
  min-width: 220px;
}

.tag-library__clear {
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

.tag-section-title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: var(--border);
}

.tag-section-title h2 {
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
}

.tag-section-title span {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tag-hot__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  padding-top: var(--space-3);
}

.tag-hot__item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 10px;
  border: 1px solid var(--line-strong);
  color: var(--ink);
  font-size: var(--text-sm);
  text-decoration: none;
  transition:
    border-color var(--dur) var(--ease),
    background var(--dur) var(--ease);
}

.tag-hot__item:hover {
  border-color: var(--ink);
  background: var(--acid);
}

.tag-hot__count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tag-search,
.tag-search__list {
  display: grid;
  gap: var(--space-3);
}

.tag-search__list {
  padding-top: var(--space-3);
}

.tag-search__item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3);
  border: var(--border);
  background: var(--surface);
}

.tag-search__eyebrow {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tag-search__item h3 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-lg);
}

.tag-search__item h3 a {
  color: var(--ink);
  text-decoration: none;
}

.tag-search__item h3 a:hover {
  color: var(--verm-deep);
}

.tag-search__description {
  margin-top: var(--space-1);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.tag-search__articles {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.tag-groups {
  display: grid;
  gap: clamp(20px, 3vw, 32px);
}

.tag-group {
  display: grid;
  gap: var(--space-3);
}

.tag-group__list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-2);
}

.tag-card {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3);
  border: var(--border);
  background: var(--surface);
}

.tag-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.tag-card__head h3 {
  font-family: var(--serif);
  font-size: var(--text-base);
  font-weight: var(--weight-bold);
}

.tag-card__head h3 a {
  color: var(--ink);
  text-decoration: none;
}

.tag-card__head h3 a:hover {
  color: var(--verm-deep);
}

.tag-card__head span {
  flex-shrink: 0;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tag-card__description {
  color: var(--text-muted);
  font-size: var(--text-xs);
  line-height: var(--leading-relaxed);
}

.tag-card__actions {
  display: flex;
  gap: var(--space-3);
  padding-top: var(--space-2);
  border-top: var(--border);
}

.tag-card__link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.tag-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.tag-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.tag-empty {
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

@media (max-width: 960px) {
  .tag-group__list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .tag-library__search {
    align-items: stretch;
    flex-direction: column;
  }

  .tag-group__list {
    grid-template-columns: 1fr;
  }

  .tag-search__item {
    grid-template-columns: 1fr;
  }
}
</style>
