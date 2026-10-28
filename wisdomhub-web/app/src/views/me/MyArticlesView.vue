<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { list as listArticles, remove as deleteArticle } from '@/api/modules/article'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import ArticleCard from '@/components/business/ArticleCard.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useArticleAuthors } from '@/composables/useArticleAuthors'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import { useAuthStore } from '@/stores'
import type { ArticleListItemVO } from '@/types/content'

const auth = useAuthStore()

const articles = ref<ArticleListItemVO[]>([])
const loading = ref(false)
const error = ref('')

const { page, pageSize, total, setTotal } = usePagination({ pageSize: 5 })
const { authors } = useArticleAuthors(() => articles.value)

let requestId = 0

async function loadArticles(): Promise<void> {
  const authorId = auth.userId
  if (!authorId) {
    articles.value = []
    setTotal(0)
    return
  }

  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listArticles(
      { authorId, page: page.value, pageSize: pageSize.value },
      { skipErrorToast: true },
    )
    if (request !== requestId) return

    articles.value = result.items
    setTotal(result.total)
  } catch (err) {
    if (request !== requestId) return
    articles.value = []
    setTotal(0)
    error.value = isApiError(err) ? err.message : '文章加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

async function removeArticle(article: ArticleListItemVO): Promise<void> {
  const ok = await confirm({
    title: '删除文章',
    message: `删除后不可恢复，确定删除《${article.title}》？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return

  try {
    await deleteArticle(article.articleId, { skipErrorToast: true })
    toast.success('文章已删除')
    if (articles.value.length === 1 && page.value > 1) page.value -= 1
    else await loadArticles()
  } catch (err) {
    toast.error(isApiError(err) ? err.message : '删除失败，请稍后重试。')
  }
}

watch(
  [page, pageSize],
  () => {
    void loadArticles()
  },
  { immediate: true },
)

useSeo({
  title: '我的文章',
  description: '已发布文章的分页列表、编辑、查看与删除。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="my-articles-page">
      <header class="my-articles-head">
        <div>
          <p class="my-articles-eyebrow">[ ARTICLES / ME ]</p>
          <h1>我的文章</h1>
        </div>
        <p class="my-articles-count">{{ total }} 篇已发布</p>
      </header>
      <p class="my-articles-lede">已发布的文章可以继续编辑、查看或删除。</p>

      <div v-if="loading" class="my-articles-state" aria-live="polite">正在加载文章…</div>

      <div v-else-if="error" class="my-articles-state my-articles-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadArticles">重试</WhButton>
      </div>

      <div v-else-if="!articles.length" class="my-articles-empty">
        <p>还没有发布文章。</p>
        <RouterLink class="my-articles-link" :to="{ name: 'editor-new' }">
          写第一篇文章
        </RouterLink>
      </div>

      <div v-else class="my-articles-list">
        <article v-for="article in articles" :key="article.articleId" class="my-article">
          <ArticleCard :article="article" :author="authors[article.authorId]" compact />
          <div class="my-article__actions">
            <RouterLink
              class="my-article__action"
              :to="{ name: 'editor-edit', params: { articleId: article.articleId } }"
            >
              编辑
            </RouterLink>
            <RouterLink
              class="my-article__action"
              :to="{ name: 'article-detail', params: { articleId: article.articleId } }"
            >
              查看
            </RouterLink>
            <button
              class="my-article__action my-article__action--danger"
              type="button"
              @click="removeArticle(article)"
            >
              删除
            </button>
          </div>
        </article>
      </div>

      <div v-if="total > pageSize" class="my-articles-pagination">
        <WhPagination v-model:page="page" :page-size="pageSize" :total="total" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.my-articles-page {
  display: grid;
  gap: var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
}

.my-articles-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.my-articles-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.my-articles-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.my-articles-count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.my-articles-lede {
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--ink);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.my-articles-list {
  display: grid;
}

.my-article {
  display: grid;
  gap: var(--space-2);
  padding-bottom: var(--space-4);
  border-bottom: var(--border);
}

.my-article:first-child {
  border-top: var(--border);
}

.my-article :deep(.article-card) {
  border-top: 0;
}

.my-article__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--space-4);
}

.my-article__action {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
  transition: color var(--dur) var(--ease);
}

.my-article__action:hover {
  color: var(--ink);
}

.my-article__action--danger:hover {
  color: var(--verm-deep);
}

.my-articles-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.my-articles-state--error {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.my-articles-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.my-articles-link {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.my-articles-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .my-article__actions {
    justify-content: flex-start;
  }
}
</style>
