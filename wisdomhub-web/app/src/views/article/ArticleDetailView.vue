<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { detail as fetchArticleDetail } from '@/api/modules/article'
import {
  collect as collectArticle,
  like as likeArticle,
  uncollect as uncollectArticle,
  unlike as unlikeArticle,
} from '@/api/modules/interaction'
import { related as fetchRelated } from '@/api/modules/recommendation'
import WhButton from '@/components/base/WhButton.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import ArticleCard from '@/components/business/ArticleCard.vue'
import ArticlePaywall from '@/components/business/ArticlePaywall.vue'
import AnalysisCard from '@/components/business/AnalysisCard.vue'
import AuthorChip from '@/components/business/AuthorChip.vue'
import CommentSection from '@/components/business/CommentSection.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { useArticleHistory } from '@/composables/useArticleHistory'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { ArticleDetailVO } from '@/types/content'
import type { RecommendationItemVO } from '@/types/recommendation'
import { formatDateTime } from '@/utils'
import { sanitizeHtml } from '@/utils/sanitize'

interface TocItem {
  id: string
  text: string
  level: number
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { report } = useArticleHistory()

const articleId = computed(() => String(route.params.articleId ?? ''))
const detail = ref<ArticleDetailVO | null>(null)
const contentHtml = ref('')
const toc = ref<TocItem[]>([])
const detailLoading = ref(true)
const detailError = ref('')
const notFound = ref(false)

const related = ref<RecommendationItemVO[]>([])
const relatedLoading = ref(false)
const relatedError = ref('')
const commentTotal = ref<number | null>(null)

const likePending = ref(false)
const collectPending = ref(false)

const { authors } = useArticleAuthors(related)
const { ensureAuthors } = useAuthorBrief()
const detailAuthor = computed(() => (detail.value ? authors[detail.value.authorId] : undefined))

let detailRequest = 0
let relatedRequest = 0

function prepareContent(html: string | null): void {
  contentHtml.value = ''
  toc.value = []
  if (!html) return

  const template = document.createElement('template')
  template.innerHTML = sanitizeHtml(html)
  const headings = [...template.content.querySelectorAll<HTMLHeadingElement>('h2, h3')]
  toc.value = headings.map((heading, index) => {
    const id = `section-${index + 1}`
    heading.id = id
    return {
      id,
      text: heading.textContent?.trim() || `第 ${index + 1} 节`,
      level: heading.tagName === 'H3' ? 3 : 2,
    }
  })
  contentHtml.value = template.innerHTML
}

async function loadDetail(): Promise<void> {
  const id = articleId.value
  const request = (detailRequest += 1)

  detailLoading.value = true
  detailError.value = ''
  notFound.value = false
  detail.value = null
  contentHtml.value = ''
  toc.value = []
  commentTotal.value = null

  if (!id) {
    detailLoading.value = false
    notFound.value = true
    return
  }

  try {
    const data = await fetchArticleDetail(id, { skipErrorToast: true })
    if (request !== detailRequest) return

    detail.value = data
    prepareContent(data.html)
    void ensureAuthors([data.authorId])
    void report(data.articleId)
  } catch (error) {
    if (request !== detailRequest) return

    if (isApiError(error)) {
      detailError.value = error.message
      notFound.value = error.code === 404
    } else {
      detailError.value = '文章加载失败，请稍后重试。'
    }
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}

async function loadRelated(): Promise<void> {
  const id = articleId.value
  if (!id) return

  const request = (relatedRequest += 1)
  relatedLoading.value = true
  relatedError.value = ''
  related.value = []

  try {
    const data = await fetchRelated(id, 6, { skipErrorToast: true })
    if (request !== relatedRequest) return
    related.value = data
  } catch (error) {
    if (request !== relatedRequest) return
    relatedError.value = isApiError(error) ? error.message : '相关推荐加载失败。'
  } finally {
    if (request === relatedRequest) relatedLoading.value = false
  }
}

function requireLogin(): boolean {
  if (auth.isLoggedIn()) return true
  void router.push({ name: 'login', query: { redirect: route.fullPath } })
  return false
}

async function toggleLike(): Promise<void> {
  if (!detail.value || likePending.value) return
  if (!requireLogin()) return

  const id = detail.value.articleId
  const previous = { liked: detail.value.liked, count: detail.value.likeCount }
  const nextLiked = !previous.liked
  detail.value = {
    ...detail.value,
    liked: nextLiked,
    likeCount: Math.max(0, previous.count + (nextLiked ? 1 : -1)),
  }
  likePending.value = true

  try {
    const count = nextLiked ? await likeArticle(id) : await unlikeArticle(id)
    if (detail.value?.articleId === id) {
      detail.value = {
        ...detail.value,
        liked: nextLiked,
        likeCount: typeof count === 'number' ? count : detail.value.likeCount,
      }
    }
  } catch {
    if (detail.value?.articleId === id) {
      detail.value = { ...detail.value, liked: previous.liked, likeCount: previous.count }
    }
  } finally {
    likePending.value = false
  }
}

async function toggleCollect(): Promise<void> {
  if (!detail.value || collectPending.value) return
  if (!requireLogin()) return

  const id = detail.value.articleId
  const previous = { collected: detail.value.collected, count: detail.value.favoriteCount }
  const nextCollected = !previous.collected
  detail.value = {
    ...detail.value,
    collected: nextCollected,
    favoriteCount: Math.max(0, previous.count + (nextCollected ? 1 : -1)),
  }
  collectPending.value = true

  try {
    const count = nextCollected ? await collectArticle(id) : await uncollectArticle(id)
    if (detail.value?.articleId === id) {
      detail.value = {
        ...detail.value,
        collected: nextCollected,
        favoriteCount: typeof count === 'number' ? count : detail.value.favoriteCount,
      }
    }
  } catch {
    if (detail.value?.articleId === id) {
      detail.value = {
        ...detail.value,
        collected: previous.collected,
        favoriteCount: previous.count,
      }
    }
  } finally {
    collectPending.value = false
  }
}

function scrollToSection(id: string): void {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(
  articleId,
  () => {
    void loadDetail()
    void loadRelated()
    void nextTick(() => {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    })
  },
  { immediate: true },
)

useSeo({
  title: computed(() => detail.value?.title ?? '文章详情'),
  description: computed(() => detail.value?.summary ?? 'WisdomHub 文章详情。'),
})
</script>

<template>
  <PageContainer tag="div">
    <div class="article-page">
      <div v-if="detailLoading" class="article-loading" aria-live="polite">
        <WhSkeleton width="18%" height="10px" />
        <WhSkeleton :lines="2" height="30px" />
        <WhSkeleton width="42%" height="10px" />
        <div class="article-loading__body">
          <WhSkeleton :lines="6" height="12px" />
          <WhSkeleton :lines="4" height="12px" />
        </div>
      </div>

      <div v-else-if="notFound" class="article-state">
        <p class="article-eyebrow">[ 404 / ARTICLE ]</p>
        <h1>文章不存在或已删除</h1>
        <p>链接可能已失效，或者文章已被作者删除。</p>
        <RouterLink class="article-state__link" :to="{ name: 'article-list' }">
          返回文章列表
        </RouterLink>
      </div>

      <div v-else-if="detailError" class="article-state article-state--error" role="alert">
        <p>{{ detailError }}</p>
        <WhButton size="sm" variant="ghost" @click="loadDetail">重试</WhButton>
      </div>

      <template v-else-if="detail">
        <div class="article-layout">
          <main class="article-main">
            <header class="article-head">
              <RouterLink class="article-back" :to="{ name: 'article-list' }">
                ← 全部文章
              </RouterLink>
              <p class="article-eyebrow">[ ARTICLE / {{ detail.articleId }} ]</p>
              <h1>{{ detail.title }}</h1>
              <p class="article-summary">{{ detail.summary }}</p>
              <div class="article-meta">
                <AuthorChip :author="detailAuthor" size="sm" />
                <span class="article-meta__dot" aria-hidden="true">·</span>
                <time :datetime="detail.publishTime">
                  {{ formatDateTime(detail.publishTime, 'YYYY年MM月DD日') }}
                </time>
                <span class="article-meta__dot" aria-hidden="true">·</span>
                <span>{{ detail.viewCount }} 阅读</span>
                <span class="article-meta__dot" aria-hidden="true">·</span>
                <span>{{ commentTotal ?? detail.commentCount }} 评论</span>
              </div>
            </header>

            <ArticlePaywall
              v-if="detail.locked"
              :columns="detail.columns"
              @purchased="loadDetail"
            />
            <div v-else class="article-prose" v-html="contentHtml"></div>

            <footer class="article-actions">
              <button
                class="article-action"
                :class="{ 'is-active': detail.liked }"
                type="button"
                :aria-pressed="detail.liked"
                :disabled="likePending"
                @click="toggleLike"
              >
                <span aria-hidden="true">♥</span>
                <span>{{ detail.liked ? '已赞' : '点赞' }} {{ detail.likeCount }}</span>
              </button>
              <button
                class="article-action"
                :class="{ 'is-active': detail.collected }"
                type="button"
                :aria-pressed="detail.collected"
                :disabled="collectPending"
                @click="toggleCollect"
              >
                <span aria-hidden="true">☆</span>
                <span>{{ detail.collected ? '已收藏' : '收藏' }} {{ detail.favoriteCount }}</span>
              </button>
              <span class="article-actions__stat">{{ detail.viewCount }} 阅读</span>
            </footer>

            <AnalysisCard :article-id="detail.articleId" />

            <CommentSection
              :target-id="detail.articleId"
              :target-type="1"
              @total-change="commentTotal = $event"
            />
          </main>

          <aside class="article-aside">
            <section class="article-author">
              <p class="article-aside__label">[ AUTHOR ]</p>
              <div class="article-author__main">
                <AuthorChip :author="detailAuthor" size="md" />
              </div>
              <p class="article-author__bio">
                {{ detailAuthor?.bio || '这位作者还没有写简介。' }}
              </p>
              <RouterLink
                v-if="detailAuthor"
                class="article-author__link"
                :to="{ name: 'user-profile', params: { userId: detailAuthor.userId } }"
              >
                查看作者主页 →
              </RouterLink>
            </section>

            <nav v-if="toc.length" class="article-toc">
              <p class="article-aside__label">[ CONTENTS ]</p>
              <p class="article-aside__title">目录</p>
              <ol>
                <li
                  v-for="item in toc"
                  :key="item.id"
                  class="article-toc__item"
                  :class="`article-toc__item--${item.level}`"
                >
                  <a :href="`#${item.id}`" @click.prevent="scrollToSection(item.id)">
                    {{ item.text }}
                  </a>
                </li>
              </ol>
            </nav>

            <section
              v-if="relatedLoading || relatedError || related.length"
              class="article-related"
            >
              <p class="article-aside__label">[ RELATED ]</p>
              <p class="article-aside__title">相关推荐</p>

              <div v-if="relatedLoading" class="article-related__loading" aria-live="polite">
                <WhSkeleton :lines="3" height="12px" />
                <WhSkeleton :lines="3" height="12px" />
              </div>
              <div v-else-if="relatedError" class="article-related__error" role="alert">
                <p>{{ relatedError }}</p>
                <WhButton size="sm" variant="ghost" @click="loadRelated">重试</WhButton>
              </div>
              <div v-else class="article-related__list">
                <ArticleCard
                  v-for="item in related"
                  :key="item.articleId"
                  :article="item"
                  :author="authors[item.authorId]"
                  :tags="item.tags"
                  compact
                />
              </div>
            </section>
          </aside>
        </div>
      </template>
    </div>
  </PageContainer>
</template>

<style scoped>
.article-page {
  max-width: 1200px;
  margin: 0 auto;
}

.article-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: clamp(32px, 5vw, 64px);
  align-items: start;
}

.article-main {
  min-width: 0;
}

.article-head {
  display: grid;
  gap: var(--space-3);
  padding-bottom: var(--space-6);
  border-bottom: var(--border);
}

.article-back {
  justify-self: start;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.06em;
  text-decoration: none;
  transition: color var(--dur) var(--ease);
}

.article-back:hover {
  color: var(--verm-deep);
}

.article-eyebrow,
.article-aside__label {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.article-head h1 {
  font-family: var(--serif);
  font-size: clamp(2rem, 3.6vw, 3.1rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.article-summary {
  max-width: 64ch;
  color: var(--text-muted);
  font-size: var(--text-md);
  line-height: var(--leading-relaxed);
}

.article-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  padding-top: var(--space-1);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.article-meta__dot {
  color: var(--line-strong);
}

.article-prose {
  max-width: 72ch;
  padding-block: clamp(24px, 4vw, 40px);
  color: var(--text-body);
  font-size: var(--text-md);
  line-height: 1.85;
}

.article-prose :deep(h2) {
  margin: 2.2em 0 0.7em;
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-bold);
  line-height: var(--leading-snug);
  scroll-margin-top: 96px;
}

.article-prose :deep(h3) {
  margin: 1.8em 0 0.6em;
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
  scroll-margin-top: 96px;
}

.article-prose :deep(p) {
  margin: 1em 0;
}

.article-prose :deep(a) {
  color: var(--verm-deep);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

.article-prose :deep(ul),
.article-prose :deep(ol) {
  margin: 1em 0;
  padding-left: 1.4em;
}

.article-prose :deep(li) {
  margin: 0.35em 0;
}

.article-prose :deep(blockquote) {
  margin: 1.6em 0;
  padding: var(--space-3) var(--space-5);
  border-left: 3px solid var(--acid);
  background: var(--surface);
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-lg);
}

.article-prose :deep(pre) {
  margin: 1.6em 0;
  padding: var(--space-4);
  overflow-x: auto;
  background: var(--ink);
  color: var(--bone);
  font-family: var(--mono);
  font-size: var(--text-sm);
  line-height: 1.65;
}

.article-prose :deep(pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
  font-size: inherit;
}

.article-prose :deep(code) {
  padding: 1px 5px;
  background: var(--surface-strong);
  color: var(--ink);
  font-family: var(--mono);
  font-size: 0.92em;
}

.article-prose :deep(img) {
  max-width: 100%;
  height: auto;
  border: var(--border);
}

.article-prose :deep(table) {
  width: 100%;
  margin: 1.4em 0;
  border-collapse: collapse;
  font-size: var(--text-sm);
}

.article-prose :deep(th),
.article-prose :deep(td) {
  padding: var(--space-2);
  border: var(--border);
  text-align: left;
}

.article-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding-block: var(--space-5);
  border-top: 1px solid var(--ink);
  border-bottom: var(--border);
}

.article-action {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  padding-inline: 14px;
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

.article-action:hover:not(:disabled) {
  border-color: var(--ink);
  color: var(--ink);
}

.article-action.is-active {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
}

.article-action:disabled {
  opacity: 0.55;
  cursor: wait;
}

.article-actions__stat {
  margin-left: auto;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.article-aside {
  position: sticky;
  top: 96px;
  display: grid;
  gap: var(--space-8);
}

.article-aside__label {
  margin-bottom: var(--space-2);
}

.article-aside__title {
  color: var(--ink);
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
}

.article-author {
  padding: var(--space-4);
  border: var(--border);
  background: var(--surface);
}

.article-author__main {
  margin-top: var(--space-2);
}

.article-author__bio {
  margin-top: var(--space-3);
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.article-author__link {
  display: inline-block;
  margin-top: var(--space-3);
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.article-toc {
  padding-top: var(--space-4);
  border-top: var(--border);
}

.article-toc ol {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.article-toc__item a {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-snug);
  text-decoration: none;
  transition: color var(--dur) var(--ease);
}

.article-toc__item a:hover {
  color: var(--verm-deep);
}

.article-toc__item--3 {
  padding-left: var(--space-4);
}

.article-related {
  padding-top: var(--space-4);
  border-top: var(--border);
}

.article-related__list {
  display: grid;
}

.article-related__list :deep(.article-card:first-child) {
  border-top: 0;
}

.article-related__loading {
  display: grid;
  gap: var(--space-4);
  margin-top: var(--space-3);
}

.article-related__error {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  margin-top: var(--space-3);
  color: var(--verm-deep);
  font-size: var(--text-sm);
}

.article-loading {
  display: grid;
  gap: var(--space-4);
  max-width: 780px;
  margin: 0 auto;
}

.article-loading__body {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-5);
}

.article-state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  max-width: 720px;
  margin: 0 auto;
  padding-block: clamp(32px, 8vw, 80px);
  color: var(--text-muted);
}

.article-state h1 {
  font-family: var(--serif);
  font-size: var(--text-3xl);
  font-weight: var(--weight-black);
}

.article-state--error {
  color: var(--verm-deep);
}

.article-state__link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 900px) {
  .article-layout {
    grid-template-columns: 1fr;
  }

  .article-aside {
    position: static;
    gap: var(--space-6);
  }
}

@media (max-width: 640px) {
  .article-head h1 {
    font-size: clamp(1.8rem, 9vw, 2.4rem);
  }

  .article-actions__stat {
    width: 100%;
    margin-left: 0;
  }

  .article-prose {
    font-size: var(--text-base);
  }
}
</style>
