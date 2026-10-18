<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import {
  collectArticle,
  getArticleDetail,
  likeArticle,
  uncollectArticle,
  unlikeArticle,
  type ArticleDetail,
} from '@/api/article'
import { ApiError } from '@/api/http'
import { ApiCode } from '@/api/types'
import CommentSection from '@/components/comment/CommentSection.vue'
import UserAvatar from '@/components/UserAvatar.vue'
import { highlightIn } from '@/composables/useMarkdown'
import { loadUserNames, userAvatar, userName } from '@/composables/useUserNames'
import { useUserStore } from '@/stores/user'
import { formatCount, formatDateTime, formatRelativeTime } from '@/utils/format'

/**
 * F1.4 文章详情 + 评论。
 *
 * 正文用**后端渲染并消毒过的 `html`**（flexmark + jsoup 白名单），前端只做两件事：
 *   `v-html` 挂上去、然后 `highlightIn()` 给代码块上色。
 * **不要**在前端再渲染一遍 markdown —— 两套实现必然出现「预览和线上不一样」。
 *
 * 点赞/收藏（B1.26）：接口是「设置状态」，幂等，且**返回最新计数** ——
 * 所以计数直接用返回值，前端不自己加减（省掉一类对不上账的 bug）。
 */
const route = useRoute()
const router = useRouter()
const user = useUserStore()

const detail = ref<ArticleDetail | null>(null)
const loading = ref(true)
const notFound = ref(false)
const acting = ref(false)
const articleEl = ref<HTMLElement | null>(null)

/** 目录项，level 是标题层级 1~3 */
interface TocItem {
  id: string
  text: string
  level: number
}

const toc = ref<TocItem[]>([])
const activeId = ref('')
let tocObserver: IntersectionObserver | null = null

const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

/** 吸顶导航的高度，锚点跳转要留出来（与 CSS 的 scroll-margin-top 同源） */
const HEADING_OFFSET = 72

function scrollToHeading(id: string) {
  document.getElementById(id)?.scrollIntoView({
    behavior: prefersReducedMotion.matches ? 'auto' : 'smooth',
    block: 'start',
  })
}

/** 扫正文标题生成目录。必须在 v-html 进 DOM 后调（同 highlightIn），标题的 id 就地补上 */
function buildToc(root: HTMLElement | null) {
  tocObserver?.disconnect()
  tocObserver = null
  activeId.value = ''

  const headings = root ? Array.from(root.querySelectorAll<HTMLElement>('h1, h2, h3')) : []
  const items: TocItem[] = []
  headings.forEach((el, i) => {
    const text = (el.textContent ?? '').trim()
    if (!text) return
    const id = el.id || `wh-heading-${i + 1}`
    el.id = id
    items.push({ id, text, level: Number(el.tagName.slice(1)) })
  })
  toc.value = items

  if (items.length === 0) return

  // 只把落在视口上方那条带里的标题当作「当前位置」，下面的 70% 不算
  const visible = new Set<string>()
  tocObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) visible.add(entry.target.id)
        else visible.delete(entry.target.id)
      }
      const next = toc.value.find((item) => visible.has(item.id))
      if (next) activeId.value = next.id
    },
    { rootMargin: `-${HEADING_OFFSET}px 0px -70% 0px` },
  )
  for (const item of items) {
    const el = document.getElementById(item.id)
    if (el) tocObserver.observe(el)
  }
}

onBeforeUnmount(() => tocObserver?.disconnect())

const isOwner = computed(
  () =>
    detail.value !== null &&
    user.userId !== null &&
    String(detail.value.authorId) === user.userId,
)

async function load() {
  const articleId = String(route.params.id ?? '')
  loading.value = true
  notFound.value = false
  detail.value = null
  // 上一篇的标题马上就不在 DOM 里了，别让 observer 一直攥着它们
  tocObserver?.disconnect()
  toc.value = []

  try {
    detail.value = await getArticleDetail(articleId)
    void loadUserNames([detail.value.authorId])
  } catch (e) {
    if (e instanceof ApiError && e.code === ApiCode.NOT_FOUND) {
      notFound.value = true
    } else {
      ElMessage.error(e instanceof ApiError ? e.message : '加载失败')
    }
    return
  } finally {
    loading.value = false
  }

  // loading 必须先落回 false：正文挂在 v-else-if="detail" 上，loading 还是 true 时渲染的是
  // 骨架屏、articleEl 是 null —— 高亮和目录都会什么都扫不到（这里踩过一次）
  await nextTick()
  highlightIn(articleEl.value)
  buildToc(articleEl.value)
}

/** 未登录时把用户送到登录页，并记下回跳地址（登录成功后回到这一页） */
function requireLogin(): boolean {
  if (user.isLoggedIn) return true
  ElMessage.info('登录后才能操作')
  void router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

async function onToggleLike() {
  if (!detail.value || !requireLogin()) return

  acting.value = true
  try {
    const { articleId, liked } = detail.value
    detail.value.likeCount = liked ? await unlikeArticle(articleId) : await likeArticle(articleId)
    detail.value.liked = !liked
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

async function onToggleCollect() {
  if (!detail.value || !requireLogin()) return

  acting.value = true
  try {
    const { articleId, collected } = detail.value
    detail.value.favoriteCount = collected
      ? await uncollectArticle(articleId)
      : await collectArticle(articleId)
    detail.value.collected = !collected
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '操作失败')
  } finally {
    acting.value = false
  }
}

/**
 * 评论增删后只刷新计数（不整篇重取）：
 * 重取详情会让 v-html 重渲染一次、正文闪一下，而且点赞/收藏的本地状态会被覆盖。
 */
async function refreshCounts() {
  if (!detail.value) return
  try {
    const fresh = await getArticleDetail(detail.value.articleId)
    detail.value.commentCount = fresh.commentCount
    // 刻意不覆盖 liked / collected：用户刚点过的状态以本地为准，避免闪回
  } catch {
    /* 只是刷新计数，失败不影响阅读 */
  }
}

watch(() => route.params.id, () => void load())
onMounted(() => void load())
</script>

<template>
  <div class="art">
    <div v-if="loading" class="art__state" role="status" aria-live="polite">
      <el-skeleton :rows="8" animated />
      <span class="wh-visually-hidden">正在加载文章</span>
    </div>

    <div v-else-if="notFound" class="art__state">
      <el-empty description="文章不存在或已删除" :image-size="96">
        <el-button type="primary" @click="router.push('/home')">回首页</el-button>
      </el-empty>
    </div>

    <template v-else-if="detail">
      <div class="art__layout" :class="{ 'is-single': toc.length === 0 }">
        <aside v-if="toc.length" class="art__toc" aria-label="文章目录">
          <h2 class="art__toc-title">目录</h2>
          <ul class="art__toc-list">
            <li v-for="item in toc" :key="item.id">
              <a
                class="art__toc-link"
                :class="[`is-lv${item.level}`, { 'is-active': item.id === activeId }]"
                :href="`#${item.id}`"
                @click.prevent="scrollToHeading(item.id)"
              >
                {{ item.text }}
              </a>
            </li>
          </ul>
        </aside>

        <div class="art__main">
          <header class="art__head">
            <h1 id="article-title" class="art__title">{{ detail.title }}</h1>
            <p v-if="detail.summary" class="art__summary">{{ detail.summary }}</p>

            <div class="art__meta">
              <div class="art__meta-main">
                <RouterLink class="art__author" :to="`/user/${detail.authorId}`">
                  <span aria-hidden="true">
                    <UserAvatar
                      :src="userAvatar(detail.authorId)"
                      :name="userName(detail.authorId)"
                      :size="20"
                    />
                  </span>
                  <span class="art__at" aria-hidden="true">@</span>{{ userName(detail.authorId) }}
                </RouterLink>
                <span :title="formatDateTime(detail.publishTime)">
                  {{ formatRelativeTime(detail.publishTime) }}
                </span>
                <span :title="`阅读 ${detail.viewCount}`">
                  阅读 <span class="wh-mono">{{ formatCount(detail.viewCount) }}</span>
                </span>
              </div>

              <!-- 只有作者本人能看到编辑入口；已发布文章的改动走「覆盖发布」 -->
              <RouterLink v-if="isOwner" class="art__edit" :to="`/editor/${detail.articleId}`">
                编辑
              </RouterLink>
            </div>
          </header>

          <!-- 正文：后端渲染 + 消毒过的 HTML -->
          <article
            ref="articleEl"
            class="art__body wh-prose"
            aria-labelledby="article-title"
            v-html="detail.html"
          ></article>

          <div class="art__actions" role="group" aria-label="文章操作">
            <button
              class="art__action"
              type="button"
              :class="{ 'is-on': detail.liked }"
              :disabled="acting"
              :aria-pressed="detail.liked"
              :aria-busy="acting"
              @click="onToggleLike"
            >
              <svg class="art__action-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  v-if="detail.liked"
                  d="M12 21s-7.5-4.7-9.3-9.2C1.3 8.5 3.2 5.5 6.3 5.5c1.9 0 3.5 1 4.7 2.6 1.2-1.6 2.8-2.6 4.7-2.6 3.1 0 5 3 3.6 6.3C19.5 16.3 12 21 12 21Z"
                  fill="currentColor"
                />
                <path
                  v-else
                  d="M12 21s-7.5-4.7-9.3-9.2C1.3 8.5 3.2 5.5 6.3 5.5c1.9 0 3.5 1 4.7 2.6 1.2-1.6 2.8-2.6 4.7-2.6 3.1 0 5 3 3.6 6.3C19.5 16.3 12 21 12 21Z"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linejoin="round"
                />
              </svg>
              赞 <span class="wh-mono">{{ detail.likeCount }}</span>
            </button>

            <button
              class="art__action"
              type="button"
              :class="{ 'is-on': detail.collected }"
              :disabled="acting"
              :aria-pressed="detail.collected"
              :aria-busy="acting"
              @click="onToggleCollect"
            >
              <svg class="art__action-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  v-if="detail.collected"
                  d="m12 3.4 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9Z"
                  fill="currentColor"
                />
                <path
                  v-else
                  d="m12 3.4 2.7 5.5 6.1.9-4.4 4.3 1 6.1-5.4-2.9-5.4 2.9 1-6.1-4.4-4.3 6.1-.9Z"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linejoin="round"
                />
              </svg>
              收藏 <span class="wh-mono">{{ detail.favoriteCount }}</span>
            </button>

            <span class="art__action art__action--static">
              <svg class="art__action-icon" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  d="M20 11.5a8.5 8.5 0 0 1-8.5 8.5H5l1.7-3.5A8.5 8.5 0 1 1 20 11.5Z"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linejoin="round"
                />
              </svg>
              评论 <span class="wh-mono">{{ detail.commentCount }}</span>
            </span>
          </div>

          <CommentSection
            :target-id="detail.articleId"
            :comment-count="detail.commentCount"
            @changed="refreshCounts"
          />
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.art {
  padding-block: var(--wh-space-7) var(--wh-space-9);
}

.art__state {
  width: 100%;
  max-width: var(--wh-reading);
  margin-inline: auto;
  padding-block: var(--wh-space-6);
}

/* 左侧目录有视觉重量，左列略宽于右列，正文中心向右补偿约 1.5% */
.art__layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, var(--wh-reading)) minmax(0, 0.95fr);
  gap: var(--wh-space-6);
  align-items: start;
}

/* 文章没有标题时，目录列不占，正文仍然居中 */
.art__layout.is-single {
  grid-template-columns: minmax(0, 1fr) minmax(0, var(--wh-reading)) minmax(0, 1fr);
}

/* 同 DefaultLayout：允许收缩，否则宽表格/长代码块会把这一列撑破 */
.art__main {
  grid-column: 2;
  width: 100%;
  min-width: 0;
}

.art__toc {
  position: sticky;
  top: calc(var(--wh-nav-height) + var(--wh-space-5));
  grid-column: 1;
  justify-self: end;

  width: 100%;
  max-width: 13rem;
  max-height: calc(100vh - var(--wh-nav-height) - var(--wh-space-7));
  padding-right: var(--wh-space-2);
  overflow-y: auto;
  scrollbar-width: thin;
}

.art__toc-title {
  padding-bottom: var(--wh-space-2);

  font-size: var(--wh-text-xs);
  font-weight: var(--wh-weight-medium);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-text-3);

  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

.art__toc-list {
  margin-top: var(--wh-space-2);
  list-style: none;
}

.art__toc-link {
  display: block;
  padding: 5px 0 5px var(--wh-space-2);

  font-size: var(--wh-text-sm);
  line-height: var(--wh-leading-snug);
  color: var(--wh-text-3);
  text-decoration: none;

  border-left: var(--wh-border-width) solid transparent;
  transition:
    color var(--wh-duration-fast) var(--wh-ease),
    border-color var(--wh-duration-fast) var(--wh-ease);
}

/* 层级只靠缩进表达，不额外上色 */
.art__toc-link.is-lv2 {
  padding-left: calc(var(--wh-space-2) + var(--wh-space-3));
}

.art__toc-link.is-lv3 {
  padding-left: calc(var(--wh-space-2) + var(--wh-space-5));
}

.art__toc-link:hover {
  color: var(--wh-text-1);
}

.art__toc-link.is-active {
  color: var(--wh-accent);
  font-weight: var(--wh-weight-medium);
  border-left-color: var(--wh-accent);
}

/* 吸顶导航会盖住锚点标题 */
.art__body :deep(:is(h1, h2, h3)) {
  scroll-margin-top: calc(var(--wh-nav-height) + var(--wh-space-4));
}

/* 窄屏放不下目录，退回单列居中 */
@media (max-width: 1200px) {
  .art__layout,
  .art__layout.is-single {
    grid-template-columns: minmax(0, var(--wh-reading));
    justify-content: center;
  }

  .art__main {
    grid-column: 1;
  }

  .art__toc {
    display: none;
  }
}

.art__head {
  padding-bottom: var(--wh-space-5);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.art__title {
  font-size: var(--wh-text-3xl);
  font-weight: var(--wh-weight-semibold);
  line-height: var(--wh-leading-tight);
  letter-spacing: var(--wh-tracking-tight);
}

.art__summary {
  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-lg);
  line-height: var(--wh-leading-normal);
  color: var(--wh-text-2);
}

.art__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;
  justify-content: space-between;

  margin-top: var(--wh-space-4);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.art__meta-main {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2);
  align-items: center;
}

.art__meta-main > * + *::before {
  content: '·';
  margin-right: var(--wh-space-2);
  color: var(--wh-text-3);
}

.art__author {
  display: inline-flex;
  gap: var(--wh-space-2);
  align-items: center;

  color: var(--wh-text-1);
  font-weight: var(--wh-weight-medium);
  text-decoration: none;
}

.art__at {
  margin-right: 2px;
  color: var(--wh-text-3);
}

.art__author:hover {
  color: var(--wh-accent);
}

.art__edit {
  flex: none;

  padding: 2px var(--wh-space-2);

  font-size: var(--wh-text-xs);
  color: var(--wh-text-2);
  text-decoration: none;

  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-sm);
}

.art__edit:hover {
  color: var(--wh-accent);
  border-color: var(--wh-accent);
  text-decoration: none;
}

.art__body {
  margin-top: var(--wh-space-6);
}

.art__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2);
  align-items: center;

  margin-top: var(--wh-space-7);
  padding-top: var(--wh-space-4);

  border-top: var(--wh-border-width) solid var(--wh-border);
}

.art__action {
  display: inline-flex;
  gap: var(--wh-space-2);
  align-items: center;

  min-height: 36px;
  padding: var(--wh-space-2) var(--wh-space-3);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-2);
  background: none;
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-md);
  cursor: pointer;

  transition:
    color var(--wh-duration-fast) var(--wh-ease),
    background-color var(--wh-duration-fast) var(--wh-ease),
    border-color var(--wh-duration-fast) var(--wh-ease);
}

.art__action:hover:not(:disabled) {
  color: var(--wh-text-1);
  background-color: var(--wh-bg-raised);
  border-color: var(--wh-border-strong);
}

.art__action:disabled {
  cursor: default;
  opacity: 0.7;
}

/* 已点赞/已收藏：主色描边 + 主色字，不填充 —— 填充会和主按钮抢注意力 */
.art__action.is-on {
  color: var(--wh-accent);
  border-color: var(--wh-accent);
}

.art__action--static {
  color: var(--wh-text-3);
  cursor: default;
  background: none;
  border-color: transparent;
}

.art__action-icon {
  flex: none;
  width: 16px;
  height: 16px;
}

@media (max-width: 600px) {
  .art {
    padding-block: var(--wh-space-5) var(--wh-space-8);
  }

  .art__title {
    font-size: var(--wh-text-2xl);
  }

  .art__head {
    padding-bottom: var(--wh-space-4);
  }
}
</style>
