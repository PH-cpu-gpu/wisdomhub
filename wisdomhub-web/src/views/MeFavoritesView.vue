<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import 'element-plus/es/components/message/style/css'

import { listMyFavorites, uncollectArticle, type ArticleListItem } from '@/api/article'
import { ApiError } from '@/api/http'
import ArticleRow from '@/components/article/ArticleListItem.vue'
import { loadUserNames } from '@/composables/useUserNames'

/**
 * F1.8 我的收藏。
 *
 * 这页没法直接套 `ArticleList`：它的筛选只有 `tagId` / `authorId`，
 * 而收藏走的是另一条接口（`GET /article/collect`，按**收藏时间**倒序）。
 * 但列表项复用同一个 `ArticleRow` —— 排版与别处一致，只是多一个「取消收藏」的动作。
 *
 * ⚠️ 收藏列表是 join 出来的：已删/草稿/下架的文章不会出现，所以 `total` 与
 * 「我总共收藏过多少」不是一个数。
 */
const items = ref<ArticleListItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const removingId = ref<string | null>(null)

async function load(retryOnOutOfRange = true) {
  loading.value = true
  try {
    const result = await listMyFavorites(page.value, pageSize.value)
    items.value = result.items
    total.value = result.total
    void loadUserNames(result.items.map((item) => item.authorId))

    if (retryOnOutOfRange && result.items.length === 0 && result.pages > 0 && page.value > result.pages) {
      page.value = result.pages
      await load(false)
    }
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '收藏加载失败')
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 取消收藏：接口幂等，返回最新计数 —— 这里直接从本地列表移除即可（这一页就是靠它列出来的） */
async function onUncollect(article: ArticleListItem) {
  removingId.value = article.articleId
  try {
    await uncollectArticle(article.articleId)
    ElMessage.success('已取消收藏')
    await load()
  } catch (e) {
    ElMessage.error(e instanceof ApiError ? e.message : '取消失败')
  } finally {
    removingId.value = null
  }
}

watch(page, () => void load())
onMounted(() => void load())
</script>

<template>
  <div class="fav">
    <header class="fav__head">
      <h1 class="fav__title">我的收藏</h1>
      <span class="fav__sub">按收藏时间倒序；已删除的文章不会出现在这里</span>
    </header>

    <el-skeleton v-if="loading && items.length === 0" :rows="5" animated />

    <el-empty v-else-if="items.length === 0" description="还没有收藏任何文章">
      <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
    </el-empty>

    <template v-else>
      <div class="fav__list" :class="{ 'is-loading': loading }">
        <ArticleRow v-for="article in items" :key="article.articleId" :article="article">
          <template #actions>
            <button
              class="fav__op"
              type="button"
              :disabled="removingId === article.articleId"
              @click="onUncollect(article)"
            >
              取消收藏
            </button>
          </template>
        </ArticleRow>
      </div>

      <div class="fav__pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          layout="prev, pager, next, total"
          background
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.fav {
  padding-block: var(--wh-space-4);
}

/* 与 ArticleList 的工具栏同一套版式：标题与说明一行，底下一条分隔线 */
.fav__head {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;

  padding-bottom: var(--wh-space-3);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

.fav__title {
  font-size: var(--wh-text-xl);
}

.fav__sub {
  font-size: var(--wh-text-sm);
  color: var(--wh-text-3);
}

.fav__list.is-loading {
  opacity: 0.55;
  transition: opacity var(--wh-duration-base) var(--wh-ease);
}

.fav__op {
  padding: 0;

  font-family: inherit;
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
  background: none;
  border: none;
  cursor: pointer;
}

.fav__op:hover:not(:disabled) {
  color: var(--wh-danger);
  text-decoration: underline;
}

.fav__op:disabled {
  cursor: default;
  opacity: 0.6;
}

.fav__pager {
  display: flex;
  justify-content: center;
  padding-top: var(--wh-space-6);
}
</style>
