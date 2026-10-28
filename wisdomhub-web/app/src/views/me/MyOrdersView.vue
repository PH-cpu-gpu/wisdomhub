<script setup lang="ts">
import { ref, watch } from 'vue'
import { RouterLink } from 'vue-router'

import { isApiError } from '@/api/error'
import { listMine } from '@/api/modules/order'
import WhButton from '@/components/base/WhButton.vue'
import WhPagination from '@/components/base/WhPagination.vue'
import PageContainer from '@/components/layout/PageContainer.vue'
import { usePagination } from '@/composables/usePagination'
import { useSeo } from '@/composables/useSeo'
import type { OrderListItemVO } from '@/types/order'
import { formatDateTime, formatMoney, orderStateLabel } from '@/utils'

const orders = ref<OrderListItemVO[]>([])
const loading = ref(false)
const error = ref('')
const { page, pageSize, total, setTotal } = usePagination({ pageSize: 10 })

let requestId = 0

function orderLink(order: OrderListItemVO) {
  return { name: 'order-detail', params: { orderNo: order.orderNo } }
}

function itemsLabel(order: OrderListItemVO): string {
  if (!order.items.length) return '订单明细缺失'
  return order.items.map((item) => item.title).join('、')
}

async function loadOrders(): Promise<void> {
  const request = (requestId += 1)
  loading.value = true
  error.value = ''

  try {
    const result = await listMine(
      { page: page.value, pageSize: pageSize.value },
      { skipErrorToast: true },
    )
    if (request !== requestId) return

    orders.value = result.items
    setTotal(result.total)
  } catch (err) {
    if (request !== requestId) return
    orders.value = []
    setTotal(0)
    error.value = isApiError(err) ? err.message : '订单加载失败，请稍后重试。'
  } finally {
    if (request === requestId) loading.value = false
  }
}

watch(
  [page, pageSize],
  () => {
    void loadOrders()
  },
  { immediate: true },
)

useSeo({
  title: '我的订单',
  description: '订单分页、状态标签与订单详情入口。',
})
</script>

<template>
  <PageContainer tag="section">
    <div class="orders-page">
      <header class="orders-head">
        <div>
          <p class="orders-eyebrow">[ ORDERS / ME ]</p>
          <h1>我的订单</h1>
        </div>
        <p class="orders-count">共 {{ total }} 笔</p>
      </header>

      <p class="orders-lede">按创建时间倒序排列；待支付订单可继续支付，其余订单可查看详情。</p>

      <div v-if="loading" class="orders-state" aria-live="polite">正在加载订单…</div>

      <div v-else-if="error" class="orders-state orders-state--error" role="alert">
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadOrders">重试</WhButton>
      </div>

      <div v-else-if="!orders.length" class="orders-empty">
        <p>还没有订单。</p>
        <RouterLink class="orders-link" :to="{ name: 'article-list' }">去读文章</RouterLink>
      </div>

      <div v-else class="orders-list">
        <article v-for="(order, index) in orders" :key="order.orderNo" class="order-row">
          <span class="order-row__index">
            ORD / {{ String((page - 1) * pageSize + index + 1).padStart(2, '0') }}
          </span>

          <div class="order-row__body">
            <div class="order-row__top">
              <RouterLink class="order-row__no" :to="orderLink(order)">
                NO. {{ order.orderNo }}
              </RouterLink>
              <span class="order-chip" :class="`is-state-${order.state}`">
                {{ orderStateLabel(order.state) }}
              </span>
            </div>

            <h2 class="order-row__title">
              <RouterLink :to="orderLink(order)">{{ itemsLabel(order) }}</RouterLink>
            </h2>

            <div class="order-row__meta">
              <span class="order-row__amount">
                {{ formatMoney(order.amount, { freeLabel: '免费' }) }}
              </span>
              <time :datetime="order.createTime">
                下单 {{ formatDateTime(order.createTime) }}
              </time>
              <span v-if="order.state === 0 && order.expireTime">
                请在 {{ formatDateTime(order.expireTime) }} 前支付
              </span>
              <span v-else-if="order.paidTime"> 支付于 {{ formatDateTime(order.paidTime) }} </span>
            </div>
          </div>

          <RouterLink class="order-row__action" :to="orderLink(order)">
            {{
              order.state === 0 ? '继续支付 →' : order.state === 1 ? '详情 / 退款 →' : '查看详情 →'
            }}
          </RouterLink>
        </article>
      </div>

      <div v-if="total > pageSize" class="orders-pagination">
        <WhPagination v-model:page="page" :page-size="pageSize" :total="total" />
      </div>
    </div>
  </PageContainer>
</template>

<style scoped>
.orders-page {
  display: grid;
  gap: var(--space-3);
  max-width: 1080px;
  margin: 0 auto;
}

.orders-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-3);
}

.orders-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.orders-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 4vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.orders-count {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.orders-lede {
  max-width: 64ch;
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.orders-state {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-8) 0;
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.orders-state--error {
  color: var(--verm-deep);
}

.orders-empty {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-10) 0;
  border-top: 1px solid var(--ink);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.orders-link {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.orders-list {
  display: grid;
  border-top: 1px solid var(--ink);
}

.order-row {
  display: grid;
  grid-template-columns: 86px minmax(0, 1fr) auto;
  align-items: start;
  gap: var(--space-4);
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--line);
}

.order-row__index {
  padding-top: 4px;
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.order-row__body {
  display: grid;
  gap: var(--space-2);
  min-width: 0;
}

.order-row__top {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
}

.order-row__no {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.order-row__no:hover {
  color: var(--verm-deep);
}

.order-chip {
  padding: 2px 7px;
  border: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
  white-space: nowrap;
}

.order-chip.is-state-0 {
  border-color: var(--verm);
  background: var(--verm);
  color: var(--text-inverse);
}

.order-chip.is-state-1 {
  border-color: var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.order-chip.is-state-2 {
  border-color: var(--line-strong);
  color: var(--text-muted);
}

.order-chip.is-state-3,
.order-chip.is-state-4 {
  border-color: var(--violet);
  color: var(--violet);
}

.order-row__title {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
}

.order-row__title a:hover {
  color: var(--verm-deep);
}

.order-row__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.order-row__amount {
  color: var(--verm-deep);
  font-family: var(--serif);
  font-size: var(--text-md);
  font-weight: var(--weight-black);
}

.order-row__action {
  padding-top: 4px;
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  white-space: nowrap;
}

.order-row__action:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.orders-pagination {
  padding-top: var(--space-4);
  border-top: 1px solid var(--ink);
}

@media (max-width: 640px) {
  .order-row {
    grid-template-columns: 1fr auto;
  }

  .order-row__index {
    display: none;
  }

  .order-row__title {
    font-size: var(--text-lg);
  }
}
</style>
