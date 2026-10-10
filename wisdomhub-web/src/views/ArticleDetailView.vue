<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
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
import { highlightIn } from '@/composables/useMarkdown'
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

  try {
    detail.value = await getArticleDetail(articleId)
    // 等 v-html 真正进 DOM 再高亮，否则 querySelector 什么也找不到
    await nextTick()
    highlightIn(articleEl.value)
  } catch (e) {
    if (e instanceof ApiError && e.code === ApiCode.NOT_FOUND) {
      notFound.value = true
      return
    }
    ElMessage.error(e instanceof ApiError ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
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
    <el-skeleton v-if="loading" :rows="10" animated />

    <el-empty v-else-if="notFound" description="文章不存在或已删除">
      <el-button type="primary" @click="router.push('/')">回首页</el-button>
    </el-empty>

    <template v-else-if="detail">
      <header class="art__head">
        <h1 class="art__title">{{ detail.title }}</h1>

        <div class="art__meta wh-mono">
          <RouterLink class="art__author" :to="`/user/${detail.authorId}`">
            用户 {{ String(detail.authorId).slice(-4) }}
          </RouterLink>
          <span :title="formatDateTime(detail.publishTime)">
            {{ formatRelativeTime(detail.publishTime) }}
          </span>
          <span :title="`阅读 ${detail.viewCount}`">阅读 {{ formatCount(detail.viewCount) }}</span>

          <!-- 只有作者本人能看到编辑入口；已发布文章的改动走「覆盖发布」 -->
          <RouterLink v-if="isOwner" class="art__edit" :to="`/editor/${detail.articleId}`">
            编辑
          </RouterLink>
        </div>
      </header>

      <!-- 正文：后端渲染 + 消毒过的 HTML -->
      <article ref="articleEl" class="art__body wh-prose" v-html="detail.html" />

      <div class="art__actions">
        <button
          class="art__action"
          type="button"
          :class="{ 'is-on': detail.liked }"
          :disabled="acting"
          @click="onToggleLike"
        >
          <span class="art__action-icon">{{ detail.liked ? '♥' : '♡' }}</span>
          赞 <span class="wh-mono">{{ detail.likeCount }}</span>
        </button>

        <button
          class="art__action"
          type="button"
          :class="{ 'is-on': detail.collected }"
          :disabled="acting"
          @click="onToggleCollect"
        >
          <span class="art__action-icon">{{ detail.collected ? '★' : '☆' }}</span>
          收藏 <span class="wh-mono">{{ detail.favoriteCount }}</span>
        </button>

        <span class="art__action art__action--static">
          评论 <span class="wh-mono">{{ detail.commentCount }}</span>
        </span>
      </div>

      <CommentSection
        :target-id="detail.articleId"
        :comment-count="detail.commentCount"
        @changed="refreshCounts"
      />
    </template>
  </div>
</template>

<style scoped>
.art {
  max-width: var(--wh-reading);
  padding-block: var(--wh-space-6);
}

.art__head {
  padding-bottom: var(--wh-space-4);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.art__title {
  font-size: var(--wh-text-3xl);
  font-weight: var(--wh-weight-semibold);
  line-height: var(--wh-leading-tight);
  letter-spacing: var(--wh-tracking-tight);
}

.art__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;

  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.art__author,
.art__edit {
  color: var(--wh-text-2);
  text-decoration: none;
}

.art__author:hover,
.art__edit:hover {
  color: var(--wh-accent);
}

.art__edit {
  margin-left: auto;
  color: var(--wh-accent);
}

.art__body {
  margin-top: var(--wh-space-6);
}

.art__actions {
  display: flex;
  gap: var(--wh-space-3);
  justify-content: center;

  margin-top: var(--wh-space-7);
  padding-block: var(--wh-space-4);

  border-top: var(--wh-border-width) solid var(--wh-border);
  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

.art__action {
  display: inline-flex;
  gap: var(--wh-space-2);
  align-items: center;

  padding: var(--wh-space-2) var(--wh-space-4);

  font-size: var(--wh-text-sm);
  color: var(--wh-text-2);
  background-color: var(--wh-bg-surface);
  border: var(--wh-border-width) solid var(--wh-border);
  border-radius: var(--wh-radius-full);
  cursor: pointer;

  transition:
    color var(--wh-duration-fast) var(--wh-ease),
    border-color var(--wh-duration-fast) var(--wh-ease);
}

.art__action:hover:not(:disabled) {
  color: var(--wh-text-1);
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
  cursor: default;
  background: none;
  border-color: transparent;
}

.art__action-icon {
  font-size: var(--wh-text-base);
  line-height: 1;
}
</style>
