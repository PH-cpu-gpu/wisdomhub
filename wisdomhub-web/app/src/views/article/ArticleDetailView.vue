<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { detail as fetchArticleDetail } from '@/api/modules/article'
import {
  collect as collectArticle,
  getState,
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
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { useArticleHistory } from '@/composables/useArticleHistory'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { useInteractionCounts } from '@/composables/useInteractionCounts'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { ArticleDetailVO } from '@/types/content'
import { CONTENT_TARGET_TYPE } from '@/types/interaction'
import type { InteractionStateVO } from '@/types/interaction'
import type { RecommendationItemVO } from '@/types/recommendation'
import { formatDateTime } from '@/utils'
import { AI_TEXT_MAX_LENGTH, AI_TEXT_SELECTION_KEY } from '@/utils/ai-entry'
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

const proseRef = ref<HTMLElement | null>(null)
const activeTocId = ref('')
const readingProgress = ref(0)
const showReadingRing = ref(false)
const readingPercent = computed(() => Math.round(readingProgress.value * 100))
const READING_RING_LENGTH = 2 * Math.PI * 20
const readingRingOffset = computed(
  () => READING_RING_LENGTH * (1 - readingProgress.value),
)

const selectionText = ref('')
const selectionTop = ref(0)
const selectionLeft = ref(0)

let scrollFrame = 0
let selectionFrame = 0
const copyTimers = new Set<number>()

const related = ref<RecommendationItemVO[]>([])
const relatedLoading = ref(false)
const relatedError = ref('')
const commentTotal = ref<number | null>(null)
/** 互动状态与计数单独持有：正文 VO 不再返回这些字段 */
const interaction = ref<InteractionStateVO | null>(null)

const likePending = ref(false)
const collectPending = ref(false)

const { authors } = useArticleAuthors(related)
const { ensureAuthors } = useAuthorBrief()
const relatedTargets = computed(() =>
  related.value.map((item) => ({
    targetType: CONTENT_TARGET_TYPE.ARTICLE,
    targetId: item.articleId,
  })),
)
const { countsFor, patchCount } = useInteractionCounts(relatedTargets)
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
  void nextTick(enhanceCodeBlocks)
}

/** 为正文代码块补一个复制按钮；v-html 重渲染后按钮会随之重建 */
function enhanceCodeBlocks(): void {
  const root = proseRef.value
  if (!root) return

  root.querySelectorAll<HTMLPreElement>('pre').forEach((pre) => {
    if (pre.parentElement?.classList.contains('article-code')) return

    const wrapper = document.createElement('div')
    wrapper.className = 'article-code'
    pre.parentNode?.insertBefore(wrapper, pre)
    wrapper.appendChild(pre)

    const button = document.createElement('button')
    button.type = 'button'
    button.className = 'article-code__copy'
    button.textContent = '复制'
    button.setAttribute('aria-label', '复制代码')
    wrapper.appendChild(button)
  })
}

function onProseClick(event: MouseEvent): void {
  const target = event.target as HTMLElement | null
  const button = target?.closest<HTMLButtonElement>('.article-code__copy')
  if (!button) return

  const wrapper = button.closest('.article-code')
  const code = wrapper?.querySelector('pre code')?.textContent ?? ''
  if (!code) return

  void copyCode(button, code)
}

async function copyCode(button: HTMLButtonElement, code: string): Promise<void> {
  let copied = false
  try {
    await navigator.clipboard.writeText(code)
    copied = true
  } catch {
    // 非安全上下文（http）没有 clipboard API，退回到 execCommand
    const area = document.createElement('textarea')
    area.value = code
    area.setAttribute('readonly', '')
    area.style.position = 'fixed'
    area.style.opacity = '0'
    document.body.appendChild(area)
    area.select()
    copied = document.execCommand('copy')
    area.remove()
  }

  button.textContent = copied ? '已复制' : '复制失败'
  button.classList.toggle('is-copied', copied)
  const timer = window.setTimeout(() => {
    button.textContent = '复制'
    button.classList.remove('is-copied')
    copyTimers.delete(timer)
  }, 1600)
  copyTimers.add(timer)
}

function scrollToTop(): void {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function hideSelectionEntry(): void {
  selectionText.value = ''
}

function isInsideProse(node: Node | null): boolean {
  return Boolean(node && proseRef.value?.contains(node))
}

/** 仅在正文内的有效选区上显示入口：入口坐标跟随选区最后一行。 */
function updateSelectionEntry(): void {
  selectionFrame = 0

  const selection = window.getSelection()
  if (
    !selection ||
    selection.isCollapsed ||
    selection.rangeCount === 0 ||
    !isInsideProse(selection.anchorNode) ||
    !isInsideProse(selection.focusNode)
  ) {
    hideSelectionEntry()
    return
  }

  const text = selection.toString().trim()
  if (!text) {
    hideSelectionEntry()
    return
  }

  const range = selection.getRangeAt(selection.rangeCount - 1)
  const rects = range.getClientRects()
  const rect = rects.length > 0 ? rects[rects.length - 1] : range.getBoundingClientRect()
  if (rect.width === 0 && rect.height === 0) {
    hideSelectionEntry()
    return
  }

  const center = rect.left + rect.width / 2
  const clamp = (value: number, min: number, max: number): number =>
    Math.min(Math.max(value, min), max)

  selectionLeft.value = clamp(center, 64, window.innerWidth - 64)
  selectionTop.value =
    rect.bottom + 44 > window.innerHeight ? Math.max(12, rect.top - 44) : rect.bottom + 10
  selectionText.value = text.slice(0, AI_TEXT_MAX_LENGTH)
}

function onSelectionChange(): void {
  if (selectionFrame) return
  selectionFrame = window.requestAnimationFrame(updateSelectionEntry)
}

function askAiWithSelection(): void {
  const text = selectionText.value.trim()
  if (!text) {
    hideSelectionEntry()
    return
  }

  try {
    window.sessionStorage.setItem(AI_TEXT_SELECTION_KEY, text.slice(0, AI_TEXT_MAX_LENGTH))
  } catch {
    toast.error('无法携带选中文本，请重试。')
    return
  }

  hideSelectionEntry()
  void router.push({ name: 'ai', query: { workflow: 3 } })
}

function updateReadingState(): void {
  scrollFrame = 0

  const prose = proseRef.value
  if (!prose) {
    showReadingRing.value = false
    activeTocId.value = ''
    return
  }

  const top = prose.getBoundingClientRect().top + window.scrollY
  const height = prose.offsetHeight
  const anchor = window.scrollY + window.innerHeight * 0.5
  const ratio = height > 0 ? (anchor - top) / height : 0
  readingProgress.value = Math.min(1, Math.max(0, ratio))
  showReadingRing.value = window.scrollY > 160

  const headings = prose.querySelectorAll<HTMLElement>('h2[id], h3[id]')
  let current = ''
  for (const heading of headings) {
    if (heading.getBoundingClientRect().top <= 160) current = heading.id
    else break
  }
  activeTocId.value = current
}

function onScroll(): void {
  hideSelectionEntry()
  if (scrollFrame) return
  scrollFrame = window.requestAnimationFrame(updateReadingState)
}

async function loadState(id: string): Promise<void> {
  try {
    const state = await getState(CONTENT_TARGET_TYPE.ARTICLE, id, { skipErrorToast: true })
    if (id === articleId.value) interaction.value = state
  } catch {
    // 计数是可选增强：拿不到就按未点赞/未收藏渲染，不让详情页报错
    if (id === articleId.value) {
      interaction.value = {
        liked: false,
        collected: false,
        likeCount: 0,
        commentCount: 0,
        favoriteCount: 0,
      }
    }
  }
}

/** 把详情最新的三项计数整份回写列表缓存，避免只回写一个字段把其余写成 0 */
function syncInteractionCache(): void {
  const id = detail.value?.articleId
  const state = interaction.value
  if (!id || !state) return
  patchCount(CONTENT_TARGET_TYPE.ARTICLE, id, {
    likeCount: state.likeCount,
    commentCount: state.commentCount,
    favoriteCount: state.favoriteCount,
  })
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
  interaction.value = null
  activeTocId.value = ''
  readingProgress.value = 0
  showReadingRing.value = false

  if (!id) {
    detailLoading.value = false
    notFound.value = true
    return
  }

  // 详情与状态各一次请求，并行发出；状态失败不阻塞正文
  void loadState(id)

  try {
    const data = await fetchArticleDetail(id, { skipErrorToast: true })
    if (request !== detailRequest) return

    detail.value = data
    prepareContent(data.html)
    void ensureAuthors([data.authorId])
    void report(data.articleId)
    void nextTick(updateReadingState)
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
  const current = interaction.value
  if (!detail.value || !current || likePending.value) return
  if (!requireLogin()) return

  const id = detail.value.articleId
  const previous = { liked: current.liked, count: current.likeCount }
  const nextLiked = !previous.liked
  interaction.value = {
    ...current,
    liked: nextLiked,
    likeCount: Math.max(0, previous.count + (nextLiked ? 1 : -1)),
  }
  likePending.value = true

  try {
    const count = nextLiked
      ? await likeArticle(CONTENT_TARGET_TYPE.ARTICLE, id, { skipErrorToast: true })
      : await unlikeArticle(CONTENT_TARGET_TYPE.ARTICLE, id, { skipErrorToast: true })
    if (detail.value?.articleId === id && interaction.value) {
      const latest = typeof count === 'number' ? count : interaction.value.likeCount
      interaction.value = { ...interaction.value, liked: nextLiked, likeCount: latest }
      syncInteractionCache()
    }
  } catch (error) {
    if (detail.value?.articleId === id && interaction.value) {
      interaction.value = { ...interaction.value, liked: previous.liked, likeCount: previous.count }
    }
    toast.error(isApiError(error) ? error.message : '点赞失败，请稍后重试。')
  } finally {
    likePending.value = false
  }
}

async function toggleCollect(): Promise<void> {
  const current = interaction.value
  if (!detail.value || !current || collectPending.value) return
  if (!requireLogin()) return

  const id = detail.value.articleId
  const previous = { collected: current.collected, count: current.favoriteCount }
  const nextCollected = !previous.collected
  interaction.value = {
    ...current,
    collected: nextCollected,
    favoriteCount: Math.max(0, previous.count + (nextCollected ? 1 : -1)),
  }
  collectPending.value = true

  try {
    const count = nextCollected
      ? await collectArticle(CONTENT_TARGET_TYPE.ARTICLE, id, { skipErrorToast: true })
      : await uncollectArticle(CONTENT_TARGET_TYPE.ARTICLE, id, { skipErrorToast: true })
    if (detail.value?.articleId === id && interaction.value) {
      const latest = typeof count === 'number' ? count : interaction.value.favoriteCount
      interaction.value = { ...interaction.value, collected: nextCollected, favoriteCount: latest }
      syncInteractionCache()
    }
  } catch (error) {
    if (detail.value?.articleId === id && interaction.value) {
      interaction.value = {
        ...interaction.value,
        collected: previous.collected,
        favoriteCount: previous.count,
      }
    }
    toast.error(isApiError(error) ? error.message : '收藏失败，请稍后重试。')
  } finally {
    collectPending.value = false
  }
}

function handleCommentTotal(total: number): void {
  commentTotal.value = total
  if (!interaction.value) return
  interaction.value = { ...interaction.value, commentCount: total }
  syncInteractionCache()
}

function scrollToSection(id: string): void {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(
  articleId,
  () => {
    hideSelectionEntry()
    void loadDetail()
    void loadRelated()
    void nextTick(() => {
      window.scrollTo({ top: 0, behavior: 'smooth' })
    })
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('selectionchange', onSelectionChange)
  window.addEventListener('resize', onSelectionChange)
  updateReadingState()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('selectionchange', onSelectionChange)
  window.removeEventListener('resize', onSelectionChange)
  if (scrollFrame) window.cancelAnimationFrame(scrollFrame)
  if (selectionFrame) window.cancelAnimationFrame(selectionFrame)
  for (const timer of copyTimers) window.clearTimeout(timer)
  copyTimers.clear()
})

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
                <span>{{ commentTotal ?? interaction?.commentCount ?? '—' }} 评论</span>
              </div>
            </header>

            <ArticlePaywall
              v-if="detail.locked"
              :columns="detail.columns"
              @purchased="loadDetail"
            />
            <div
              v-else
              ref="proseRef"
              class="article-prose"
              v-html="contentHtml"
              @click="onProseClick"
            ></div>

            <footer class="article-actions">
              <button
                class="article-action"
                :class="{ 'is-active': interaction?.liked }"
                type="button"
                :aria-pressed="interaction?.liked ?? false"
                :disabled="likePending"
                @click="toggleLike"
              >
                <span aria-hidden="true">♥</span>
                <span>{{ interaction?.liked ? '已赞' : '点赞' }} {{ interaction?.likeCount ?? '—' }}</span>
              </button>
              <button
                class="article-action"
                :class="{ 'is-active': interaction?.collected }"
                type="button"
                :aria-pressed="interaction?.collected ?? false"
                :disabled="collectPending"
                @click="toggleCollect"
              >
                <span aria-hidden="true">☆</span>
                <span
                  >{{ interaction?.collected ? '已收藏' : '收藏' }}
                  {{ interaction?.favoriteCount ?? '—' }}</span
                >
              </button>
              <span class="article-actions__stat">{{ detail.viewCount }} 阅读</span>
            </footer>

            <AnalysisCard :article-id="detail.articleId" />

            <CommentSection
              :target-id="detail.articleId"
              :target-type="1"
              @total-change="handleCommentTotal"
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
                  :class="[
                    `article-toc__item--${item.level}`,
                    { 'is-active': activeTocId === item.id },
                  ]"
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
                  :counts="countsFor(CONTENT_TARGET_TYPE.ARTICLE, item.articleId)"
                  :tags="item.tags"
                  compact
                />
              </div>
            </section>
          </aside>
        </div>
      </template>

      <Transition name="reading-ring">
        <button
          v-if="showReadingRing && !detailLoading"
          class="reading-ring"
          type="button"
          :aria-label="`回到顶部，已阅读 ${readingPercent}%`"
          @click="scrollToTop"
        >
          <svg viewBox="0 0 48 48" aria-hidden="true">
            <circle class="reading-ring__track" cx="24" cy="24" r="20" />
            <circle
              class="reading-ring__value"
              cx="24"
              cy="24"
              r="20"
              :style="{ strokeDashoffset: readingRingOffset }"
            />
          </svg>
          <span class="reading-ring__label">{{ readingPercent }}%</span>
        </button>
      </Transition>

      <button
        v-if="selectionText"
        class="article-ai-entry"
        type="button"
        :style="{ top: `${selectionTop}px`, left: `${selectionLeft}px` }"
        aria-label="用 AI 解读选中文本"
        @mousedown.prevent
        @click="askAiWithSelection"
      >
        问 AI
      </button>
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

.article-prose :deep(.article-code) {
  position: relative;
  margin: 1.6em 0;
}

.article-prose :deep(.article-code pre) {
  margin: 0;
}

.article-prose :deep(.article-code__copy) {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 3px 8px;
  border: 1px solid rgb(255 255 255 / 32%);
  background: rgb(255 255 255 / 8%);
  color: var(--bone);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  cursor: pointer;
  transition:
    background var(--dur) var(--ease),
    border-color var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.article-prose :deep(.article-code__copy:hover),
.article-prose :deep(.article-code__copy.is-copied) {
  border-color: var(--acid);
  background: var(--acid);
  color: var(--ink);
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
  display: inline-block;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-snug);
  text-decoration: none;
  transition:
    color var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.article-toc__item a:hover {
  color: var(--verm-deep);
}

.article-toc__item.is-active a {
  color: var(--ink);
  font-weight: var(--weight-semibold);
  transform: translateX(4px);
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

.article-ai-entry {
  position: fixed;
  z-index: var(--z-sticky);
  display: inline-flex;
  align-items: center;
  padding: 7px 14px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--bone);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  white-space: nowrap;
  box-shadow: var(--shadow-hard-sm);
  cursor: pointer;
  transform: translateX(-50%);
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.article-ai-entry:hover {
  background: var(--acid);
  color: var(--ink);
}

.reading-ring {
  position: fixed;
  right: clamp(16px, 3vw, 32px);
  bottom: clamp(16px, 3vw, 32px);
  z-index: var(--z-sticky);
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  padding: 0;
  border: 1px solid var(--ink);
  border-radius: 50%;
  background: color-mix(in srgb, var(--bone) 92%, transparent);
  color: var(--ink);
  cursor: pointer;
  backdrop-filter: blur(10px);
  transition:
    transform var(--dur) var(--ease),
    box-shadow var(--dur) var(--ease);
}

.reading-ring:hover {
  transform: translateY(-2px);
  box-shadow: 3px 3px 0 var(--ink);
}

.reading-ring svg {
  position: absolute;
  inset: 4px;
  width: calc(100% - 8px);
  height: calc(100% - 8px);
  transform: rotate(-90deg);
}

.reading-ring__track {
  fill: none;
  stroke: var(--line-strong);
  stroke-width: 2;
}

.reading-ring__value {
  fill: none;
  stroke: var(--verm);
  stroke-width: 2;
  stroke-dasharray: 125.66;
  stroke-linecap: round;
  transition: stroke-dashoffset 120ms linear;
}

.reading-ring__label {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  font-weight: var(--weight-semibold);
}

.reading-ring-enter-active,
.reading-ring-leave-active {
  transition:
    opacity var(--dur) var(--ease),
    transform var(--dur) var(--ease);
}

.reading-ring-enter-from,
.reading-ring-leave-to {
  opacity: 0;
  transform: translateY(8px) scale(0.9);
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

@media (prefers-reduced-motion: reduce) {
  .reading-ring,
  .reading-ring__value,
  .article-toc__item a {
    transition: none;
  }

  .reading-ring-enter-active,
  .reading-ring-leave-active {
    transition: none;
  }

  .reading-ring-enter-from,
  .reading-ring-leave-to {
    transform: none;
  }

  .article-toc__item.is-active a {
    transform: none;
  }
}
</style>
