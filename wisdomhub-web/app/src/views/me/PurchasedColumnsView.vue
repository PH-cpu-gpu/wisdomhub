<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { listPurchased } from '@/api/modules/column'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import type { PurchasedColumnVO } from '@/types/content'
import { formatDateTime, formatMoney } from '@/utils'

const columns = ref<PurchasedColumnVO[]>([])
const loading = ref(false)
const error = ref('')
const { page, pageSize, total, setTotal } = usePagination({ pageSize: 5 })

let requestId = 0

function priceLabel(price: number): string {
  return formatMoney(price, { freeLabel: '免费' })
}

function columnLink(column: PurchasedColumnVO) {
  return { name: 'column-detail', params: { columnId: column.columnId } }
}

async function loadPurchased(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listPurchased(
      { page: page.value, pageSize: pageSize.value },
      { skipErrorToast: true },
    )
    if (request !== requestId) return

    columns.value = result.items
    setTotal(result.total)
  } catch (err) {
    if (request !== requestId) return
    columns.value = []
    setTotal(0)
    error.value = isApiError(err) ? err.message : '已购专栏加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

watch(
  [page, pageSize],
  () => {
    void loadPurchased()
  },
  { immediate: true },
)

useSeo({
  title: '已购专栏',
  description: '已购买专栏的实付价、购买时间与下架状态。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="purchased-page">
      <header class="purchased-head">
        <div>
          <p class="purchased-eyebrow">[ LIBRARY / PURCHASED ]</p>
          <h1>已购专栏</h1>
        </div>
        <p class="purchased-count">{{ total }} 个专栏</p>
      </header>

      <p class="purchased-lede">
        这里按购买时间倒序展示已购专栏；下架专栏仍可继续阅读，但不会再出现在购买入口。
      </p>

      <div v-if="loading" class="purchased-state" aria-live="polite">正在加载已购专栏…</div>

      <div v-else-if="error" class="purchased-state purchased-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadPurchased">重试</WhButton>
      </div>

      <div v-else-if="!columns.length" class="purchased-empty">
        <p>还没有购买过专栏。</p>
        <RouterLink class="purchased-link" :to="{ name: 'article-list' }">去读文章</RouterLink>
      </div>

      <div v-else class="purchased-list">
        <article
          v-for="(column, index) in columns"
          :key="column.columnId"
          class="purchased-item"
          :class="{ 'is-offline': column.state === 2 }"
        >
          <span class="purchased-item__index">
            PUR / {{ String((page - 1) * pageSize + index + 1).padStart(2, '0') }}
          </span>

          <div class="purchased-item__body">
            <div class="purchased-item__title-row">
              <h2 class="purchased-item__title">
                <RouterLink :to="columnLink(column)">{{ column.title }}</RouterLink>
              </h2>
              <span v-if="column.state === 2" class="purchased-item__badge">已下架</span>
            </div>

            <p class="purchased-item__description">{{ column.description || '暂无简介。' }}</p>

            <div class="purchased-item__meta">
              <span class="purchased-item__price">{{ priceLabel(column.price) }}</span>
              <span>{{ column.articleCount }} 篇文章</span>
              <time :datetime="column.purchaseTime">
                购于 {{ formatDateTime(column.purchaseTime, 'YYYY-MM-DD') }}
              </time>
            </div>
          </div>

          <RouterLink class="purchased-item__action" :to="columnLink(column)">
            继续阅读 →
          </RouterLink>
        </article>
      </div>

      <div v-if="total > pageSize" class="purchased-pagination">
        <WhPagination v-model:page="page" :page-size="pageSize" :total="total" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.purchased-page {
  display: grid;
  gap: var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
}

.purchased-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.purchased-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.purchased-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.purchased-count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.purchased-lede {
  max-width: 64ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.purchased-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.purchased-state--error {
  color: var(--verm-deep);
}

.purchased-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-10) 0;
  border-top: 1px solid var(--ink);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.purchased-link {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.purchased-list {
  display: grid;
  border-top: 1px solid var(--ink);
}

.purchased-item {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-4);
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--line);
}

.purchased-item__index {
  padding-top: 4px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.purchased-item__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.purchased-item__title-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.purchased-item__title {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
}

.purchased-item__title a:hover {
  color: var(--verm-deep);
}

.purchased-item__badge {
  padding: 2px 7px;
  border: 1px solid var(--line-strong);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.purchased-item.is-offline .purchased-item__title {
  color: var(--text-body);
}

.purchased-item__description {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.purchased-item__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.purchased-item__price {
  color: var(--verm-deep);
  font-family: var(--serif);
  font-size: var(--text-md);
  font-weight: var(--weight-black);
}

.purchased-item__action {
  padding-top: 4px;
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  white-space: nowrap;
}

.purchased-item__action:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.purchased-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .purchased-item {
    grid-template-columns: 1fr auto;
  }

  .purchased-item__index {
    display: none;
  }

  .purchased-item__title {
    font-size: var(--text-lg);
  }
}
</style>
