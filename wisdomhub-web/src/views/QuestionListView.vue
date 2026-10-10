<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/http'
import { listQuestions, type QuestionListItem, type QuestionSort } from '@/api/question'
import { formatCount, formatDateTime, formatRelativeTime } from '@/utils/format'

/**
 * F1.7 问答列表。
 *
 * ⚠️ 列表**不返回问题正文**（`content` 是 TEXT，跟着列表走一页几十 KB），只有标题 ——
 * 所以要摘要的话得点进详情，这是刻意的。
 * `sort=hot` 按**回答数**倒序（问题表没有 view_count，热度只能看回答）。
 */
const route = useRoute()
const router = useRouter()

const DEFAULT_SORT: QuestionSort = 'new'

const sort = ref<QuestionSort>(route.query.sort === 'hot' ? 'hot' : DEFAULT_SORT)
const page = ref(parsePage(route.query.page))
const pageSize = ref(10)

const items = ref<QuestionListItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')

function parsePage(value: unknown): number {
  const n = Number.parseInt(String(value ?? ''), 10)
  return Number.isFinite(n) && n > 0 ? n : 1
}

async function load(retryOnOutOfRange = true) {
  loading.value = true
  error.value = ''
  try {
    const result = await listQuestions({ page: page.value, pageSize: pageSize.value, sort: sort.value })
    items.value = result.items
    total.value = result.total

    if (retryOnOutOfRange && result.items.length === 0 && result.pages > 0 && page.value > result.pages) {
      page.value = result.pages
      await load(false)
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '加载失败，请稍后重试'
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function syncQuery() {
  const query: Record<string, string> = {}
  if (sort.value !== DEFAULT_SORT) query.sort = sort.value
  if (page.value > 1) query.page = String(page.value)
  void router.replace({ query })
}

watch([sort, page], () => {
  syncQuery()
  void load()
})

onMounted(() => void load())
</script>

<template>
  <div class="qa">
    <!-- 标题、提问按钮、排序合成一行，省掉一整块页头 -->
    <div class="qa__toolbar">
      <div class="qa__heading">
        <h1 class="qa__title">问答</h1>
        <span class="qa__eyebrow wh-mono">技术问答</span>
      </div>

      <div class="qa__controls">
        <el-button type="primary" size="small" @click="router.push('/question/ask')">
          我要提问
        </el-button>

        <el-radio-group v-model="sort" size="small">
          <el-radio-button value="new">最新</el-radio-button>
          <el-radio-button value="hot">回答最多</el-radio-button>
        </el-radio-group>

        <span v-if="total > 0" class="qa__total wh-mono">{{ total }} 个问题</span>
      </div>
    </div>

    <el-skeleton v-if="loading && items.length === 0" :rows="5" animated />

    <el-empty v-else-if="error" :description="error">
      <el-button type="primary" @click="load()">重试</el-button>
    </el-empty>

    <el-empty v-else-if="items.length === 0" description="还没有人提问" />

    <template v-else>
      <ul class="qa__list" :class="{ 'is-loading': loading }">
        <li v-for="question in items" :key="question.questionId" class="qa__item">
          <h2 class="qa__item-title">
            <RouterLink :to="`/question/${question.questionId}`">{{ question.title }}</RouterLink>
            <span v-if="question.solved" class="qa__solved wh-mono">已解决</span>
          </h2>

          <div class="qa__item-meta wh-mono">
            <RouterLink class="qa__author" :to="`/user/${question.userId}`">
              用户 {{ String(question.userId).slice(-4) }}
            </RouterLink>
            <span :title="formatDateTime(question.createTime)">
              {{ formatRelativeTime(question.createTime) }}
            </span>
            <span class="qa__stats">
              <span>回答 {{ formatCount(question.answerCount) }}</span>
              <span>收藏 {{ formatCount(question.favoriteCount) }}</span>
            </span>
          </div>
        </li>
      </ul>

      <div class="qa__pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="prev, pager, next, sizes, total"
          background
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.qa {
  padding-block: var(--wh-space-4);
}

/* 标题、提问按钮与排序同处一行 */
.qa__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2) var(--wh-space-4);
  align-items: center;
  justify-content: space-between;

  padding-bottom: var(--wh-space-3);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.qa__heading {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;
  min-width: 0;
}

.qa__eyebrow {
  font-size: var(--wh-text-xs);
  letter-spacing: var(--wh-tracking-wide);
  color: var(--wh-accent);
}

.qa__title {
  font-size: var(--wh-text-xl);
}

.qa__controls {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;
  margin-left: auto;
}

.qa__total {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.qa__list {
  list-style: none;
}

.qa__list.is-loading {
  opacity: 0.55;
  transition: opacity var(--wh-duration-base) var(--wh-ease);
}

.qa__item {
  padding: var(--wh-space-5) 0;
  border-bottom: var(--wh-border-width) solid var(--wh-border);
}

.qa__item-title {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2);
  align-items: center;

  font-size: var(--wh-text-lg);
  font-weight: var(--wh-weight-semibold);
  line-height: var(--wh-leading-snug);
}

.qa__item-title a {
  color: var(--wh-text-1);
  text-decoration: none;
}

.qa__item-title a:hover {
  color: var(--wh-accent);
}

.qa__solved {
  flex: none;
  padding: 0 var(--wh-space-2);

  font-size: var(--wh-text-xs);
  color: var(--wh-accent);
  background-color: var(--wh-accent-soft);
  border-radius: var(--wh-radius-full);
}

.qa__item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;

  margin-top: var(--wh-space-3);

  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.qa__author {
  color: var(--wh-text-2);
  text-decoration: none;
}

.qa__author:hover {
  color: var(--wh-accent);
}

.qa__stats {
  display: flex;
  gap: var(--wh-space-3);
  margin-left: auto;
}

.qa__pager {
  display: flex;
  justify-content: center;
  padding-top: var(--wh-space-6);
}
</style>
