<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import OrderCreateModal from '@/components/business/OrderCreateModal.vue'
import { useAuthStore } from '@/stores'
import type { ArticleColumnVO } from '@/types/content'
import { formatMoney } from '@/utils'

defineProps<{
  columns: ArticleColumnVO[]
}>()

const emit = defineEmits<{
  purchased: []
}>()

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const orderOpen = ref(false)
const orderColumn = ref<ArticleColumnVO | null>(null)

function priceLabel(column: ArticleColumnVO): string {
  return formatMoney(column.price, { freeLabel: '免费' })
}

function stateLabel(column: ArticleColumnVO): string {
  if (column.purchased) return '已购买'
  if (!column.purchasable) return '暂不可购买'
  return '付费专栏'
}

function actionLabel(column: ArticleColumnVO): string {
  if (column.purchased) return '已购买'
  if (!column.purchasable) return '暂不可购买'
  return auth.isLoggedIn() ? '购买专栏' : '登录后购买'
}

function handlePurchase(column: ArticleColumnVO): void {
  if (column.purchased || !column.purchasable) return

  if (!auth.isLoggedIn()) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }

  orderColumn.value = column
  orderOpen.value = true
}
</script>

<template>
  <section class="paywall">
    <header class="paywall__head">
      <div class="paywall__eyebrow-row">
        <p class="paywall__eyebrow">[ MEMBERS ONLY ]</p>
        <span class="paywall__badge">LOCKED</span>
      </div>
      <h2>付费内容</h2>
      <p class="paywall__desc">购买所属专栏后即可阅读全文；上方摘要可免费试读。</p>
    </header>

    <div v-if="columns.length" class="paywall__columns">
      <article v-for="(column, index) in columns" :key="column.columnId" class="paywall-column">
        <div class="paywall-column__top">
          <span class="paywall-column__index">
            COLUMN / {{ String(index + 1).padStart(2, '0') }}
          </span>
          <span class="paywall-column__price">{{ priceLabel(column) }}</span>
        </div>

        <h3 class="paywall-column__title">{{ column.title }}</h3>

        <div class="paywall-column__foot">
          <span
            class="paywall-column__state"
            :class="{
              'is-purchased': column.purchased,
              'is-disabled': !column.purchasable && !column.purchased,
            }"
          >
            {{ stateLabel(column) }}
          </span>
          <button
            class="paywall-column__button"
            :class="{ 'is-disabled': column.purchased || !column.purchasable }"
            type="button"
            :disabled="column.purchased || !column.purchasable"
            @click="handlePurchase(column)"
          >
            {{ actionLabel(column) }}
          </button>
        </div>
      </article>
    </div>

    <p v-else class="paywall__empty">该文章所属专栏信息暂不可用，请稍后再试。</p>

    <p class="paywall__note">
      点击购买会创建订单并进入支付页；未支付的订单 15 分钟后过期，已购专栏无需重复下单。
    </p>

    <OrderCreateModal
      v-if="orderColumn"
      v-model="orderOpen"
      :item-id="orderColumn.columnId"
      :title="orderColumn.title"
      :price="orderColumn.price"
      @already-purchased="emit('purchased')"
    />
  </section>
</template>

<style scoped>
.paywall {
  display: grid;
  gap: var(--space-5);
  margin-top: var(--space-6);
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--ink);
  background: var(--surface);
  box-shadow: var(--shadow-hard);
}

.paywall__head {
  display: grid;
  gap: var(--space-2);
}

.paywall__eyebrow-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.paywall__eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.paywall__badge {
  padding: 2px 7px;
  border: 1px solid var(--ink);
  background: var(--ink);
  color: var(--acid);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: 0.08em;
}

.paywall h2 {
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.paywall__desc {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.paywall__columns {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 1px;
  border: 1px solid var(--line);
  background: var(--line);
}

.paywall-column {
  display: grid;
  gap: var(--space-3);
  padding: var(--space-4);
  background: var(--bone);
}

.paywall-column__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}

.paywall-column__index {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.paywall-column__price {
  color: var(--verm-deep);
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  line-height: 1;
}

.paywall-column__title {
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
}

.paywall-column__foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding-top: var(--space-2);
  border-top: var(--border);
}

.paywall-column__state {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.paywall-column__state.is-purchased {
  color: var(--verm-deep);
}

.paywall-column__state.is-disabled {
  opacity: 0.65;
}

.paywall-column__button {
  min-height: 32px;
  padding-inline: 12px;
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
  font-family: var(--sans);
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  transition:
    background var(--dur) var(--ease),
    color var(--dur) var(--ease);
}

.paywall-column__button:hover:not(:disabled) {
  background: var(--ink);
  color: var(--acid);
}

.paywall-column__button.is-disabled,
.paywall-column__button:disabled {
  border-color: var(--line-strong);
  background: transparent;
  color: var(--text-muted);
  cursor: not-allowed;
}

.paywall__empty {
  padding: var(--space-5);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.paywall__note {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

@media (max-width: 640px) {
  .paywall {
    box-shadow: var(--shadow-hard-sm);
  }

  .paywall__columns {
    grid-template-columns: 1fr;
  }
}
</style>
