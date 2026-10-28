<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { isApiError } from '@/api/error'
import { prerequisites, tagDetail } from '@/api/modules/knowledge'
import { articles, questions } from '@/api/modules/search'
import WhPagination from '@/components/base/WhPagination.vue'
import AuthorChip from '@/components/business/AuthorChip.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useSeo } from '@/composables/useSeo'
import type { PrerequisiteTreeVO, TagDetailVO } from '@/types/knowledge'
import type { ArticleSearchVO, QuestionSearchVO } from '@/types/search'

const RELATION_LABELS: Record<string, string> = {
  BASED_ON: '基于',
  USES: '使用',
  PREREQUISITE: '前置',
  SIMILAR_TO: '相似',
  BELONGS_TO: '属于',
  ABOUT: '相关',
}

const PAGE_SIZE = 5

const route = useRoute()
const { authors, ensureAuthors } = useAuthorBrief()

const tagId = computed(() => String(route.params.tagId ?? ''))
const detail = ref<TagDetailVO | null>(null)
const tree = ref<PrerequisiteTreeVO | null>(null)

const articlesList = ref<ArticleSearchVO[]>([])
const articleTotal = ref(0)
const articlePage = ref(1)
const articleLoading = ref(false)
const articleError = ref('')

const questionsList = ref<QuestionSearchVO[]>([])
const questionTotal = ref(0)
const questionPage = ref(1)
const questionLoading = ref(false)
const questionError = ref('')

const loading = ref(false)
const error = ref('')
const notFound = ref(false)

let detailRequest = 0
let articleRequest = 0
let questionRequest = 0

const relationGroups = computed(() =>
  Object.entries(RELATION_LABELS)
    .map(([key, label]) => {
      const group = detail.value?.relations[key] ?? { out: [], in: [] }
      return { key, label, out: group.out, in: group.in }
    })
    .filter((group) => group.out.length > 0 || group.in.length > 0),
)

const aiTarget = computed(() => ({
  name: 'ai',
  query: {
    workflow: 'learning',
    technology: detail.value?.name ?? '',
  },
}))

async function loadDetail(): Promise<void> {
  const request = (detailRequest += 1)
  loading.value = true
  error.value = ''
  notFound.value = false
  detail.value = null
  tree.value = null

  try {
    const data = await tagDetail(tagId.value, { skipErrorToast: true })
    if (request !== detailRequest) return

    detail.value = data
    tree.value = await prerequisites(tagId.value, 5, { skipErrorToast: true })
    if (request !== detailRequest) return

    articlePage.value = 1
    questionPage.value = 1
    await Promise.allSettled([loadArticles(), loadQuestions()])
  } catch (err) {
    if (request !== detailRequest) return
    if (isApiError(err) && err.code === 404) notFound.value = true
    else error.value = isApiError(err) ? err.message : '技术详情加载失败，请稍后重试。'
  } finally {
    if (request === detailRequest) loading.value = false
  }
}

async function loadArticles(): Promise<void> {
  const request = (articleRequest += 1)
  articleLoading.value = true
  articleError.value = ''

  try {
    const result = await articles(
      { tagId: tagId.value, page: articlePage.value, pageSize: PAGE_SIZE },
      { skipErrorToast: true },
    )
    if (request !== articleRequest) return

    articlesList.value = result.items
    articleTotal.value = result.total
    if (result.items.length) {
      void ensureAuthors([...new Set(result.items.map((item) => item.authorId))])
    }
  } catch (err) {
    if (request !== articleRequest) return
    articlesList.value = []
    articleTotal.value = 0
    articleError.value = isApiError(err) ? err.message : '相关文章加载失败，请稍后重试。'
  } finally {
    if (request === articleRequest) articleLoading.value = false
  }
}

async function loadQuestions(): Promise<void> {
  if (!detail.value) return

  const request = (questionRequest += 1)
  questionLoading.value = true
  questionError.value = ''

  try {
    const result = await questions(
      {
        keyword: detail.value.name,
        page: questionPage.value,
        pageSize: PAGE_SIZE,
      },
      { skipErrorToast: true },
    )
    if (request !== questionRequest) return

    questionsList.value = result.items
    questionTotal.value = result.total
    if (result.items.length) {
      void ensureAuthors([...new Set(result.items.map((item) => item.userId))])
    }
  } catch (err) {
    if (request !== questionRequest) return
    questionsList.value = []
    questionTotal.value = 0
    questionError.value = isApiError(err) ? err.message : '相关问题加载失败，请稍后重试。'
  } finally {
    if (request === questionRequest) questionLoading.value = false
  }
}

function setArticlePage(value: number): void {
  articlePage.value = value
}

function setQuestionPage(value: number): void {
  questionPage.value = value
}

watch(tagId, () => void loadDetail(), { immediate: true })
watch(articlePage, () => void loadArticles())
watch(questionPage, () => void loadQuestions())

useSeo({
  title: '技术详情',
  description: '技术简介、关系、前置知识与相关文章。',
})
</script>

<template>
  <PageContainer tag="section">
    <div v-if="loading" class="tech-page tech-state" aria-live="polite">正在加载技术详情…</div>

    <div v-else-if="notFound" class="tech-page tech-state">
      <p>该技术不存在或已删除。</p>
      <RouterLink class="tech-link" :to="{ name: 'tag-list' }">返回标签库</RouterLink>
    </div>

    <div v-else-if="error" class="tech-page tech-state tech-state--error" role="alert">
      <p>{{ error }}</p>
      <RouterLink class="tech-link" :to="{ name: 'tag-list' }">返回标签库</RouterLink>
    </div>

    <div v-else-if="detail" class="tech-page">
      <header class="tech-head">
        <div class="tech-head__meta">
          <p class="tech-eyebrow">[ TECHNOLOGY / {{ detail.category }} ]</p>
          <RouterLink class="tech-link" :to="{ name: 'tag-list' }">全部标签</RouterLink>
        </div>
        <h1>{{ detail.name }}</h1>
        <p class="tech-description">{{ detail.description }}</p>
        <RouterLink class="tech-ai" :to="aiTarget"> 让 AI 规划学习路线 → </RouterLink>
      </header>

      <div class="tech-grid">
        <aside class="tech-relations">
          <header class="tech-section-head">
            <h2>相关技术</h2>
            <span>{{ relationGroups.length }} 类关系</span>
          </header>

          <div v-if="relationGroups.length" class="tech-relations__list">
            <section v-for="group in relationGroups" :key="group.key" class="tech-relation">
              <h3>
                <span class="tech-relation__label">{{ group.label }}</span>
                <span class="tech-relation__key">{{ group.key }}</span>
              </h3>
              <div v-if="group.out.length" class="tech-relation__row">
                <span class="tech-relation__arrow" aria-label="出向">→</span>
                <RouterLink
                  v-for="tag in group.out"
                  :key="`out-${tag.tagId}`"
                  class="tech-relation__tag"
                  :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
                >
                  {{ tag.name }}
                </RouterLink>
              </div>
              <div v-if="group.in.length" class="tech-relation__row">
                <span class="tech-relation__arrow" aria-label="入向">←</span>
                <RouterLink
                  v-for="tag in group.in"
                  :key="`in-${tag.tagId}`"
                  class="tech-relation__tag"
                  :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
                >
                  {{ tag.name }}
                </RouterLink>
              </div>
            </section>
          </div>

          <p v-else class="tech-empty">暂无关系数据。</p>
        </aside>

        <div class="tech-main">
          <section class="tech-section">
            <header class="tech-section-head">
              <h2>前置知识链</h2>
              <span v-if="tree">{{ tree.depth }} 层</span>
            </header>

            <div v-if="tree?.levels.length" class="tech-prereq">
              <div v-for="level in tree.levels" :key="level.depth" class="tech-prereq__level">
                <span class="tech-prereq__depth">L{{ level.depth }}</span>
                <RouterLink
                  v-for="tag in level.tags"
                  :key="tag.tagId"
                  class="tech-prereq__tag"
                  :to="{ name: 'tech-detail', params: { tagId: tag.tagId } }"
                >
                  {{ tag.name }}
                </RouterLink>
              </div>
              <p v-if="tree.truncated" class="tech-hint">还有更深的前置未展开。</p>
            </div>

            <p v-else class="tech-empty">暂无前置知识。</p>
          </section>

          <section class="tech-section">
            <header class="tech-section-head">
              <h2>相关文章</h2>
              <span>{{ articleTotal }} 篇</span>
            </header>

            <p v-if="articleLoading" class="tech-state-inline">正在加载相关文章…</p>
            <p v-else-if="articleError" class="tech-state-inline tech-state-inline--error">
              {{ articleError }}
            </p>
            <p v-else-if="!articlesList.length" class="tech-empty">暂无相关文章。</p>
            <div v-else class="tech-articles">
              <article
                v-for="article in articlesList"
                :key="article.articleId"
                class="tech-article"
              >
                <h3>
                  <RouterLink :to="`/article/${article.articleId}`">
                    {{ article.title }}
                  </RouterLink>
                </h3>
                <p>{{ article.summary }}</p>
                <div class="tech-meta">
                  <AuthorChip :author="authors[article.authorId]" size="xs" />
                  <span>{{ article.viewCount }} 阅读</span>
                  <span>{{ article.likeCount }} 赞</span>
                </div>
              </article>

              <WhPagination
                v-if="articleTotal > PAGE_SIZE"
                :page="articlePage"
                :page-size="PAGE_SIZE"
                :total="articleTotal"
                @update:page="setArticlePage"
              />
            </div>
          </section>

          <section class="tech-section">
            <header class="tech-section-head">
              <h2>相关问题</h2>
              <span>{{ questionTotal }} 个</span>
            </header>

            <p v-if="questionLoading" class="tech-state-inline">正在加载相关问题…</p>
            <p v-else-if="questionError" class="tech-state-inline tech-state-inline--error">
              {{ questionError }}
            </p>
            <p v-else-if="!questionsList.length" class="tech-empty">暂无相关问题。</p>
            <div v-else class="tech-questions">
              <article
                v-for="question in questionsList"
                :key="question.questionId"
                class="tech-question"
              >
                <h3>
                  <RouterLink
                    :to="{
                      name: 'question-detail',
                      params: { questionId: question.questionId },
                    }"
                  >
                    {{ question.title }}
                  </RouterLink>
                </h3>
                <p>{{ question.content }}</p>
                <div class="tech-meta">
                  <AuthorChip :author="authors[question.userId]" size="xs" />
                  <span>{{ question.answerCount }} 回答</span>
                  <span>{{ question.favoriteCount }} 收藏</span>
                </div>
              </article>

              <WhPagination
                v-if="questionTotal > PAGE_SIZE"
                :page="questionPage"
                :page-size="PAGE_SIZE"
                :total="questionTotal"
                @update:page="setQuestionPage"
              />
            </div>
          </section>
        </div>
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.tech-page {
  display: grid;
  gap: clamp(24px, 4vw, 40px);
  max-width: 1180px;
  margin: 0 auto;
}

.tech-head {
  display: grid;
  gap: var(--space-3);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--ink);
}

.tech-head__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.tech-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.tech-link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.tech-head h1 {
  font-family: var(--serif);
  font-size: clamp(2rem, 5vw, 3.4rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.tech-description {
  max-width: 60ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.tech-ai {
  display: inline-flex;
  align-items: center;
  justify-self: start;
  padding: 8px 14px;
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-xs);
  text-decoration: none;
  transition: transform var(--dur) var(--ease);
}

.tech-ai:hover {
  transform: translate(-2px, -2px);
}

.tech-grid {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(0, 1.6fr);
  gap: clamp(20px, 4vw, 40px);
  align-items: start;
}

.tech-relations {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-3);
  border: var(--border);
  background: var(--surface);
}

.tech-section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-2);
  border-bottom: var(--border);
}

.tech-section-head h2 {
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
}

.tech-section-head span {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tech-relations__list {
  display: grid;
  gap: var(--space-3);
}

.tech-relation {
  display: grid;
  gap: var(--space-2);
  padding-bottom: var(--space-3);
  border-bottom: var(--border);
}

.tech-relation:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.tech-relation h3 {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.tech-relation__label {
  font-family: var(--serif);
  font-size: var(--text-base);
  font-weight: var(--weight-bold);
}

.tech-relation__key {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tech-relation__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.tech-relation__arrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-xs);
}

.tech-relation__tag,
.tech-prereq__tag {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: none;
}

.tech-relation__tag:hover,
.tech-prereq__tag:hover {
  border-color: var(--ink);
  background: var(--acid);
}

.tech-main {
  display: grid;
  gap: clamp(20px, 3vw, 32px);
}

.tech-section {
  display: grid;
  gap: var(--space-3);
}

.tech-prereq {
  display: grid;
  gap: var(--space-2);
}

.tech-prereq__level {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.tech-prereq__depth {
  min-width: 28px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tech-hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tech-articles,
.tech-questions {
  display: grid;
}

.tech-article,
.tech-question {
  display: grid;
  gap: var(--space-2);
  padding-block: var(--space-3);
  border-bottom: var(--border);
}

.tech-article h3,
.tech-question h3 {
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-bold);
}

.tech-article h3 a,
.tech-question h3 a {
  color: var(--ink);
  text-decoration: none;
}

.tech-article h3 a:hover,
.tech-question h3 a:hover {
  color: var(--verm-deep);
}

.tech-article p,
.tech-question p {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.tech-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.tech-state,
.tech-state-inline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.tech-state--error,
.tech-state-inline--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.tech-state-inline {
  padding: var(--space-4);
}

.tech-empty {
  padding: var(--space-4);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

@media (max-width: 960px) {
  .tech-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .tech-head h1 {
    font-size: 2rem;
  }
}
</style>
