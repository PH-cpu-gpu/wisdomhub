<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { list as listArticles } from '@/api/modules/article'
import {
  addArticle,
  detail as fetchColumn,
  listArticles as listColumnArticles,
  remove as removeColumn,
  removeArticle,
} from '@/api/modules/column'
import WhButton from '@/components/base/WhButton.vue'
import WhModal from '@/components/base/WhModal.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import ArticleCard from '@/components/business/ArticleCard.vue'
import AuthorChip from '@/components/business/AuthorChip.vue'
import OrderCreateModal from '@/components/business/OrderCreateModal.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { useAuthorBrief } from '@/composables/useAuthorBrief'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { ID } from '@/types/common'
import type { ArticleListItemVO, ColumnVO } from '@/types/content'
import { formatDateTime, formatMoney } from '@/utils'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const columnId = computed(() => String(route.params.columnId ?? ''))
const column = ref<ColumnVO | null>(null)
const loading = ref(true)
const error = ref('')
const notFound = ref(false)

const articles = ref<ArticleListItemVO[]>([])
const articlesLoading = ref(false)
const articlesError = ref('')
const { page, pageSize, total, setTotal, reset } = usePagination({ pageSize: 10 })
const { authors, ensureAuthors } = useAuthorBrief()
useArticleAuthors(articles)

const deleting = ref(false)
const deleteError = ref('')
const orderOpen = ref(false)

const addOpen = ref(false)
const addArticles = ref<ArticleListItemVO[]>([])
const addLoading = ref(false)
const addError = ref('')
const {
  page: addPage,
  pageSize: addPageSize,
  total: addTotal,
  setTotal: setAddTotal,
  reset: resetAddPage,
} = usePagination({ pageSize: 5 })
useArticleAuthors(addArticles)
const memberIds = ref<Set<ID>>(new Set())
const adding = reactive<Record<ID, boolean>>({})
const removing = reactive<Record<ID, boolean>>({})

let columnRequest = 0
let articlesRequest = 0
let addRequest = 0

const isOwner = computed(() =>
  Boolean(column.value && auth.userId && column.value.userId === auth.userId),
)

const priceLabel = computed(() =>
  column.value ? formatMoney(column.value.price, { freeLabel: '免费' }) : '',
)

const purchaseState = computed(() => {
  if (!column.value) return ''
  if (column.value.purchased) return '已购买'
  if (column.value.price === 0) return '免费专栏'
  return ''
})

const purchaseAction = computed(() => {
  if (!auth.isLoggedIn()) return '登录后购买'
  return '购买专栏'
})

async function loadColumn(): Promise<void> {
  const id = columnId.value
  const request = (columnRequest += 1)
  loading.value = true
  error.value = ''
  notFound.value = false
  column.value = null
  deleteError.value = ''

  if (!id) {
    loading.value = false
    notFound.value = true
    return
  }

  try {
    const data = await fetchColumn(id, { skipErrorToast: true })
    if (request !== columnRequest) return

    column.value = data
    void ensureAuthors([data.userId])
  } catch (err) {
    if (request !== columnRequest) return

    if (isApiError(err)) {
      error.value = err.message
      notFound.value = err.code === 404
    } else {
      error.value = '专栏加载失败，请稍后重试。'
    }
  } finally {
    if (request === columnRequest) loading.value = false
  }
}

async function loadArticles(): Promise<void> {
  const id = columnId.value
  if (!id) return

  const request = (articlesRequest += 1)
  articlesLoading.value = true
  articlesError.value = ''

  try {
    const result = await listColumnArticles(
      id,
      { page: page.value, pageSize: pageSize.value },
      { skipErrorToast: true },
    )
    if (request !== articlesRequest) return

    articles.value = result.items
    setTotal(result.total)
  } catch (err) {
    if (request !== articlesRequest) return
    articles.value = []
    setTotal(0)
    articlesError.value = isApiError(err) ? err.message : '专栏文章加载失败，请稍后重试。'
  } finally {
    if (request === articlesRequest) articlesLoading.value = false
  }
}

async function loadMembership(): Promise<void> {
  const id = columnId.value
  if (!id) return

  try {
    const result = await listColumnArticles(
      id,
      { page: 1, pageSize: 100 },
      { skipErrorToast: true },
    )
    memberIds.value = new Set(result.items.map((item) => item.articleId))
  } catch {
    memberIds.value = new Set(articles.value.map((item) => item.articleId))
  }
}

async function loadMyArticles(): Promise<void> {
  const userId = auth.userId
  if (!userId) {
    addArticles.value = []
    setAddTotal(0)
    return
  }

  const request = (addRequest += 1)
  addLoading.value = true
  addError.value = ''

  try {
    const result = await listArticles(
      { authorId: userId, page: addPage.value, pageSize: addPageSize.value },
      { skipErrorToast: true },
    )
    if (request !== addRequest) return

    addArticles.value = result.items
    setAddTotal(result.total)
  } catch (err) {
    if (request !== addRequest) return
    addArticles.value = []
    setAddTotal(0)
    addError.value = isApiError(err) ? err.message : '文章加载失败，请稍后重试。'
  } finally {
    if (request === addRequest) addLoading.value = false
  }
}

function openAdd(): void {
  addOpen.value = true
  resetAddPage()
  void loadMyArticles()
}

async function handleAdd(article: ArticleListItemVO): Promise<void> {
  if (adding[article.articleId] || memberIds.value.has(article.articleId)) return

  adding[article.articleId] = true
  try {
    const count = await addArticle(columnId.value, article.articleId, { skipErrorToast: true })
    if (column.value) column.value = { ...column.value, articleCount: count }

    const next = new Set(memberIds.value)
    next.add(article.articleId)
    memberIds.value = next

    toast.success(`《${article.title}》已归入专栏`)
    await loadArticles()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '归入失败，请稍后重试。')
  } finally {
    adding[article.articleId] = false
  }
}

async function handleRemoveArticle(article: ArticleListItemVO): Promise<void> {
  if (removing[article.articleId]) return

  const ok = await confirm({
    title: '移出专栏',
    message: `移出后文章本身不会被删除，确定把《${article.title}》移出专栏？`,
    confirmText: '移出',
    danger: true,
  })
  if (!ok) return

  removing[article.articleId] = true
  try {
    const count = await removeArticle(columnId.value, article.articleId, {
      skipErrorToast: true,
    })
    if (column.value) column.value = { ...column.value, articleCount: count }

    const next = new Set(memberIds.value)
    next.delete(article.articleId)
    memberIds.value = next

    toast.success('已移出专栏')
    if (articles.value.length === 1 && page.value > 1) page.value -= 1
    else await loadArticles()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '移出失败，请稍后重试。')
  } finally {
    removing[article.articleId] = false
  }
}

async function handleDeleteColumn(): Promise<void> {
  if (!column.value || deleting.value) return

  const ok = await confirm({
    title: '删除专栏',
    message: '专栏内的文章不会被删除，但专栏本身不可恢复。确定删除？',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  deleting.value = true
  deleteError.value = ''
  try {
    await removeColumn(columnId.value, { skipErrorToast: true })
    toast.success('专栏已删除')
    await router.push({ name: 'me-columns' })
  } catch (err) {
    if (isApiError(err) && err.code === 409) deleteError.value = err.message
    else toast.error(isApiError(err) ? err.message : '删除失败，请稍后重试。')
  } finally {
    deleting.value = false
  }
}

function handlePurchase(): void {
  if (!column.value || column.value.purchased || column.value.price === 0) return

  if (!auth.isLoggedIn()) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }

  orderOpen.value = true
}

watch(
  columnId,
  () => {
    reset()
    void loadColumn()
    void loadArticles()
    addOpen.value = false
  },
  { immediate: true },
)

watch([page, pageSize], () => {
  void loadArticles()
})

watch(addOpen, (open) => {
  if (open) void loadMembership()
})

watch([addPage, addPageSize], () => {
  if (addOpen.value) void loadMyArticles()
})

useSeo({
  title: computed(() => column.value?.title ?? '专栏详情'),
  description: computed(
    () => column.value?.description || 'WisdomHub 专栏：按连载顺序组织起来的文章。',
  ),
})
</script>

<template>
  <PageContainer tag="div">
    <div class="column-page">
      <div v-if="loading" class="column-loading" aria-live="polite">
        <WhSkeleton width="20%" height="10px" />
        <WhSkeleton :lines="2" height="32px" />
        <WhSkeleton width="46%" height="11px" />
      </div>

      <div v-else-if="notFound" class="column-state">
        <p class="column-eyebrow">[ 404 / COLUMN ]</p>
        <h1>专栏不存在或已删除</h1>
        <p>链接可能已失效，或者专栏已被作者删除。</p>
        <RouterLink class="column-state__link" :to="{ name: 'me-columns' }"
          >返回我的专栏</RouterLink
        >
      </div>

      <div v-else-if="error" class="column-state" role="alert">
        <p class="column-eyebrow">[ ERROR / COLUMN ]</p>
        <h1>专栏加载失败</h1>
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadColumn">重试</WhButton>
      </div>

      <template v-else-if="column">
        <header class="column-hero">
          <div v-if="column.cover" class="column-hero__cover">
            <img :src="column.cover" :alt="`${column.title} 封面`" loading="lazy" />
          </div>

          <div class="column-hero__main">
            <div class="column-hero__top">
              <p class="column-eyebrow">[ COLUMN / SERIAL ]</p>
              <span v-if="isOwner" class="column-owner-badge">你的专栏</span>
            </div>

            <h1>{{ column.title }}</h1>
            <p class="column-hero__desc">{{ column.description || '这个专栏还没有简介。' }}</p>

            <div class="column-hero__meta">
              <AuthorChip :author="authors[column.userId]" size="sm" />
              <span>{{ column.articleCount }} 篇文章</span>
              <span class="column-hero__price">{{ priceLabel }}</span>
              <span v-if="purchaseState" class="column-hero__state">{{ purchaseState }}</span>
              <time :datetime="column.createTime">
                {{ formatDateTime(column.createTime, 'YYYY-MM-DD') }}
              </time>
            </div>

            <div class="column-hero__actions">
              <template v-if="isOwner">
                <WhButton size="sm" @click="openAdd">归入文章</WhButton>
                <WhButton
                  size="sm"
                  variant="danger"
                  :loading="deleting"
                  @click="handleDeleteColumn"
                >
                  删除专栏
                </WhButton>
              </template>
              <WhButton
                v-else-if="!column.purchased && column.price > 0"
                size="sm"
                @click="handlePurchase"
              >
                {{ purchaseAction }}
              </WhButton>
            </div>

            <p v-if="deleteError" class="column-alert" role="alert">{{ deleteError }}</p>

            <p v-if="isOwner" class="column-hint">
              归入顺序就是阅读顺序；移出文章不会删除文章本身。
            </p>
            <p v-else-if="!column.purchased && column.price > 0" class="column-hint">
              购买后可阅读专栏内的付费文章；点击购买会创建订单并进入支付页。
            </p>
          </div>
        </header>

        <section class="column-articles">
          <header class="column-articles__head">
            <div>
              <p class="column-eyebrow">[ CONTENTS ]</p>
              <h2>文章目录</h2>
            </div>
            <p class="column-articles__note">按归入时间升序 · 共 {{ column.articleCount }} 篇</p>
          </header>

          <div v-if="articlesLoading" class="column-articles__state" aria-live="polite">
            <WhSkeleton :lines="4" height="14px" />
          </div>

          <div v-else-if="articlesError" class="column-articles__state" role="alert">
            <p>{{ articlesError }}</p>
            <WhButton size="sm" variant="ghost" @click="loadArticles">重试</WhButton>
          </div>

          <div
            v-else-if="!articles.length"
            class="column-articles__state column-articles__state--empty"
          >
            <p>{{ isOwner ? '专栏里还没有文章。' : '这个专栏还没有公开文章。' }}</p>
            <WhButton v-if="isOwner" size="sm" @click="openAdd">归入第一篇文章</WhButton>
          </div>

          <div v-else class="column-entries">
            <article
              v-for="(article, index) in articles"
              :key="article.articleId"
              class="column-entry"
            >
              <span class="column-entry__index">
                {{ String((page - 1) * pageSize + index + 1).padStart(2, '0') }}
              </span>
              <div class="column-entry__body">
                <ArticleCard :article="article" :author="authors[article.authorId]" compact />
              </div>
              <button
                v-if="isOwner"
                class="column-entry__remove"
                type="button"
                :disabled="removing[article.articleId]"
                @click="handleRemoveArticle(article)"
              >
                {{ removing[article.articleId] ? '移出中…' : '移出' }}
              </button>
            </article>
          </div>

          <div v-if="total > pageSize" class="column-articles__pagination">
            <WhPagination v-model:page="page" :page-size="pageSize" :total="total" />
          </div>
        </section>
      </template>
    </div>

    <WhModal v-model="addOpen" title="归入文章" size="lg">
      <div class="add-panel">
        <p class="add-panel__lede">从你的已发布文章中选择；归入顺序即阅读顺序。</p>

        <div v-if="addLoading" class="add-panel__state" aria-live="polite">正在加载文章…</div>

        <div v-else-if="addError" class="add-panel__state add-panel__state--error" role="alert">
          <p>{{ addError }}</p>
          <WhButton size="sm" variant="ghost" @click="loadMyArticles">重试</WhButton>
        </div>

        <div v-else-if="!addArticles.length" class="add-panel__state">
          <p>还没有已发布文章。</p>
          <RouterLink class="add-panel__link" :to="{ name: 'editor-new' }" @click="addOpen = false">
            去写一篇
          </RouterLink>
        </div>

        <ul v-else class="add-list">
          <li v-for="article in addArticles" :key="article.articleId" class="add-item">
            <div class="add-item__body">
              <h3>{{ article.title }}</h3>
              <p>
                {{ formatDateTime(article.publishTime, 'YYYY-MM-DD') }} · 阅读
                {{ article.viewCount }}
              </p>
            </div>
            <WhButton v-if="memberIds.has(article.articleId)" size="sm" variant="ghost" disabled>
              已归入
            </WhButton>
            <WhButton
              v-else
              size="sm"
              :loading="Boolean(adding[article.articleId])"
              @click="handleAdd(article)"
            >
              归入
            </WhButton>
          </li>
        </ul>

        <div v-if="addTotal > addPageSize" class="add-panel__pagination">
          <WhPagination v-model:page="addPage" :page-size="addPageSize" :total="addTotal" />
        </div>
      </div>
    </WhModal>

    <OrderCreateModal
      v-if="column"
      v-model="orderOpen"
      :item-id="column.columnId"
      :title="column.title"
      :price="column.price"
      @already-purchased="loadColumn"
    />
  </PageContainer>
</template>

<style scoped>
.column-page {
  display: grid;
  gap: clamp(32px, 5vw, 64px);
  max-width: 1080px;
  margin: 0 auto;
}

.column-loading {
  display: grid;
  gap: var(--space-4);
}

.column-state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-16) 0;
}

.column-state h1 {
  font-family: var(--serif);
  font-size: var(--text-3xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.column-state p {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.column-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.column-state__link {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.column-hero {
  display: grid;
  gap: clamp(20px, 3vw, 32px);
  border-top: 1px solid var(--ink);
  padding-top: var(--space-6);
}

.column-hero__cover {
  overflow: hidden;
  max-height: 320px;
  border: 1px solid var(--ink);
}

.column-hero__cover img {
  width: 100%;
  height: 100%;
  max-height: 320px;
  object-fit: cover;
}

.column-hero__main {
  display: grid;
  gap: var(--space-4);
}

.column-hero__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.column-owner-badge {
  padding: 2px 8px;
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
}

.column-hero h1 {
  max-width: 24ch;
  font-family: var(--serif);
  font-size: clamp(2.2rem, 5vw, 3.6rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.column-hero__desc {
  max-width: 66ch;
  color: var(--text-muted);
  font-size: var(--text-base);
  line-height: var(--leading-relaxed);
}

.column-hero__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2) var(--space-4);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.column-hero__price {
  color: var(--verm-deep);
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-black);
}

.column-hero__state {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--ink);
}

.column-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.column-alert {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
  font-size: var(--text-sm);
}

.column-hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.column-articles {
  display: grid;
  gap: var(--space-4);
}

.column-articles__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.column-articles__head h2 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.column-articles__note {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.column-articles__state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.column-articles__state--empty {
  border-bottom: 1px solid var(--line);
}

.column-entries {
  display: grid;
}

.column-entry {
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-4);
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--line);
}

.column-entry__index {
  padding-top: 4px;
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-sm);
  font-variant-numeric: tabular-nums;
  letter-spacing: var(--tracking-wide);
}

.column-entry__body {
  min-width: 0;
}

.column-entry__remove {
  padding: 4px 0 2px;
  border-bottom: 1px solid transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  white-space: nowrap;
}

.column-entry__remove:hover:not(:disabled) {
  border-color: var(--verm-deep);
  color: var(--verm-deep);
}

.column-entry__remove:disabled {
  opacity: 0.6;
  cursor: wait;
}

.column-articles__pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

.add-panel {
  display: grid;
  gap: var(--space-4);
}

.add-panel__lede {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.add-panel__state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-6) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.add-panel__state--error {
  color: var(--verm-deep);
}

.add-panel__link {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.add-list {
  display: grid;
  border-top: 1px solid var(--line);
}

.add-item {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--line);
}

.add-item__body {
  display: grid;
  gap: var(--space-1);
  min-width: 0;
}

.add-item__body h3 {
  overflow: hidden;
  font-family: var(--serif);
  font-size: var(--text-md);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.add-item__body p {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.add-panel__pagination {
  padding-top: var(--space-2);
}

@media (max-width: 640px) {
  .column-hero h1 {
    font-size: clamp(1.9rem, 8vw, 2.4rem);
  }

  .column-entry {
    grid-template-columns: 34px minmax(0, 1fr);
  }

  .column-entry__remove {
    grid-column: 2;
    justify-self: start;
  }

  .add-item {
    align-items: flex-start;
  }

  .add-item__body h3 {
    white-space: normal;
  }
}
</style>
