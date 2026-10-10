<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { listArticles, type ArticleListItem, type ArticleSort } from '@/api/article'
import { ApiError } from '@/api/http'
import { loadUserNames } from '@/composables/useUserNames'

import ArticleRow from './ArticleListItem.vue'

/**
 * 文章列表。首页（F1.2）、标签详情（F1.5）、个人主页（F1.6）、我的收藏（F1.8）共用同一套
 * 分页 + 排序 + 骨架屏，靠 props 只换筛选条件 —— 不各写一份。
 *
 * 三条契约（B1.23）：
 *   - 只返回**已发布且未删除**的文章，`total` 就是可见文章数
 *   - `sort` 只认白名单枚举 `new | hot`，传别的后端给 400
 *   - `hot` 用 `view_count` **快照列**排序（B1.22 每 2 分钟从 Redis 落库）
 *     → 最热榜最多滞后 2 分钟，列表里的阅读量会略低于详情页的实时值。这是刻意的
 *
 * 排序与页码同步到 URL（`?sort=hot&page=2`）：刷新/回退/分享都能复现当前视图。
 * 只动这两个键，其余 query（比如个人主页的 tab）原样保留。
 */
const props = withDefaults(
  defineProps<{
    /** 按标签筛（标签详情页用）。名字 → id 的解析在页面里做 */
    tagId?: string
    /** 按作者筛（个人主页用） */
    authorId?: string
    /** 每条不显示作者：个人主页里所有文章都是同一个人 */
    hideAuthor?: boolean
    emptyText?: string
    pageSize?: number
  }>(),
  {
    tagId: undefined,
    authorId: undefined,
    hideAuthor: false,
    emptyText: '还没有已发布的文章',
    pageSize: 10,
  },
)

const route = useRoute()
const router = useRouter()

const DEFAULT_SORT: ArticleSort = 'new'

const sort = ref<ArticleSort>(parseSort(route.query.sort))
const page = ref(parsePage(route.query.page))
const pageSize = ref(props.pageSize)

const items = ref<ArticleListItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')

function parseSort(value: unknown): ArticleSort {
  return value === 'hot' ? 'hot' : DEFAULT_SORT
}

function parsePage(value: unknown): number {
  const n = Number.parseInt(String(value ?? ''), 10)
  return Number.isFinite(n) && n > 0 ? n : 1
}

async function load(retryOnOutOfRange = true) {
  loading.value = true
  error.value = ''
  try {
    const result = await listArticles({
      page: page.value,
      pageSize: pageSize.value,
      sort: sort.value,
      tagId: props.tagId,
      authorId: props.authorId,
    })
    items.value = result.items
    total.value = result.total
    // 列表只给 authorId：把本页作者去重后一次性把名字补回来（不阻塞渲染，名字到了会自动刷新）
    void loadUserNames(result.items.map((item) => item.authorId))

    // 页码越界（例如热度排序下总数变少了）：退回最后一页，只重试一次
    if (
      retryOnOutOfRange &&
      result.items.length === 0 &&
      result.pages > 0 &&
      page.value > result.pages
    ) {
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
  const query: Record<string, string | string[]> = {}
  // 先保留页面自己的 query（例如个人主页的 tab），再覆盖排序与页码
  for (const [key, value] of Object.entries(route.query)) {
    if (key !== 'sort' && key !== 'page' && value !== undefined) {
      query[key] = value as string | string[]
    }
  }
  if (sort.value !== DEFAULT_SORT) query.sort = sort.value
  if (page.value > 1) query.page = String(page.value)
  void router.replace({ query })
}

/** 换排序或每页条数都要回到第 1 页；页码已经是 1 时不会触发下面那个 watch，所以在这里补一次加载 */
watch([sort, pageSize], () => {
  if (page.value !== 1) {
    page.value = 1
    return
  }
  syncQuery()
  void load()
})

watch(page, () => {
  syncQuery()
  void load()
})

/** 筛选条件变了（比如在个人主页切 tab 后回到文章列表）就重新拉 */
watch(() => [props.tagId, props.authorId], () => {
  page.value = 1
  void load()
})

onMounted(() => void load())
</script>

<template>
  <div class="list">
    <!-- 标题与排序合成一行：页面通过 #heading 把标题塞进来，省掉单独的一整块页头 -->
    <div class="list__toolbar">
      <div class="list__heading">
        <slot name="heading" />
      </div>

      <div class="list__controls">
        <el-radio-group v-model="sort" size="small">
          <el-radio-button value="new">最新</el-radio-button>
          <el-radio-button value="hot">最热</el-radio-button>
        </el-radio-group>

        <span v-if="total > 0" class="list__total wh-mono">{{ total }} 篇</span>
      </div>
    </div>

    <!-- 首次加载：骨架屏。已有数据时重新加载不盖骨架，避免闪一下 -->
    <div v-if="loading && items.length === 0" class="list__skeleton">
      <el-skeleton v-for="n in 4" :key="n" :rows="3" animated />
    </div>

    <el-empty v-else-if="error" :description="error">
      <el-button type="primary" @click="load()">重试</el-button>
    </el-empty>

    <el-empty v-else-if="items.length === 0" :description="emptyText" />

    <template v-else>
      <div class="list__rows" :class="{ 'is-loading': loading }">
        <ArticleRow
          v-for="article in items"
          :key="article.articleId"
          :article="article"
          :hide-author="hideAuthor"
        />
      </div>

      <div class="list__pager">
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
.list__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-2) var(--wh-space-4);
  align-items: center;
  justify-content: space-between;

  padding-bottom: var(--wh-space-3);
  border-bottom: var(--wh-border-width) solid var(--wh-border-strong);
}

/* 页面注入的标题（可选）。空着时这一格宽度为 0，控件自然靠右 */
.list__heading {
  display: flex;
  flex-wrap: wrap;
  gap: var(--wh-space-3);
  align-items: center;
  min-width: 0;
}

.list__controls {
  display: flex;
  gap: var(--wh-space-3);
  align-items: center;
  margin-left: auto;
}

.list__total {
  font-size: var(--wh-text-xs);
  color: var(--wh-text-3);
}

.list__skeleton {
  display: flex;
  flex-direction: column;
  gap: var(--wh-space-6);
  padding-top: var(--wh-space-5);
}

/* 重新加载时压暗已有列表，不用骨架屏盖住 —— 内容还在，闪一下更烦 */
.list__rows.is-loading {
  opacity: 0.55;
  transition: opacity var(--wh-duration-base) var(--wh-ease);
}

.list__pager {
  display: flex;
  justify-content: center;
  padding-top: var(--wh-space-6);
}
</style>
