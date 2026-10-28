<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import { isApiError } from '@/api/error'
import {
  cancel as cancelOrderRequest,
  detail as fetchOrder,
  pay as payOrderRequest,
  refund as refundOrderRequest,
} from '@/api/modules/order'
import WhButton from '@/components/base/WhButton.vue'
import WhModal from '@/components/base/WhModal.vue'
import WhSelect from '@/components/base/WhSelect.vue'
import WhSkeleton from '@/components/base/WhSkeleton.vue'
import { confirm } from '@/components/base/confirm'
import { toast } from '@/components/base/toast'
import PageContainer from '@/components/layout/PageContainer.vue'
import { useSeo } from '@/composables/useSeo'
import type { OrderVO, RefundSimulate } from '@/types/order'
import { formatDateTime, formatMoney, orderStateLabel } from '@/utils'

const route = useRoute()
const orderNo = computed(() => String(route.params.orderNo ?? ''))
const order = ref<OrderVO | null>(null)
const loading = ref(true)
const error = ref('')
const notFound = ref(false)
const actionError = ref('')
const paying = ref(false)
const canceling = ref(false)
const refundOpen = ref(false)
const refunding = ref(false)
const refundError = ref('')
const refundSimulate = ref<RefundSimulate>('success')

const REFUND_STATE_LABELS: Record<number, string> = {
  0: '无',
  1: '处理中',
  2: '成功',
  3: '失败',
}

const refundOptions = [
  { label: '成功（立即退款）', value: 'success' },
  { label: '处理中（稍后完成）', value: 'processing' },
  { label: '失败（保留权益）', value: 'failure' },
]

const now = ref(Date.now())
let clock: number | null = null
let orderRequest = 0

const stateLabel = computed(() => (order.value ? orderStateLabel(order.value.state) : ''))

const remainingSeconds = computed(() => {
  if (!order.value || order.value.state !== 0) return 0
  const expireAt = Date.parse(order.value.expireTime)
  if (Number.isNaN(expireAt)) return 0
  return Math.max(0, Math.ceil((expireAt - now.value) / 1000))
})

const expired = computed(() => order.value?.state === 0 && remainingSeconds.value <= 0)

const countdownText = computed(() => {
  const total = remainingSeconds.value
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
})

const columnLink = computed(() => {
  const item = order.value?.items[0]
  return item ? { name: 'column-detail', params: { columnId: item.itemId } } : null
})

const refundStateLabel = computed(() =>
  order.value?.payment ? (REFUND_STATE_LABELS[order.value.payment.refundState] ?? '—') : '',
)

const refundFailed = computed(
  () => order.value?.state === 1 && order.value.payment?.refundState === 3,
)

const canRefund = computed(() => order.value?.state === 1 || order.value?.state === 3)

function startClock(): void {
  stopClock()
  now.value = Date.now()
  clock = window.setInterval(() => {
    now.value = Date.now()
  }, 1000)
}

function stopClock(): void {
  if (clock !== null) {
    window.clearInterval(clock)
    clock = null
  }
}

async function loadOrder(silent = false): Promise<void> {
  const no = orderNo.value
  const request = (orderRequest += 1)

  if (!silent) {
    loading.value = true
    error.value = ''
    notFound.value = false
    order.value = null
  }

  if (!no) {
    loading.value = false
    notFound.value = true
    return
  }

  try {
    const data = await fetchOrder(no, { skipErrorToast: true })
    if (request !== orderRequest) return
    // 复制一份，避免 mock 复用同一对象时 computed 缓存无法失效
    order.value = { ...data }
    error.value = ''
    notFound.value = false
  } catch (err) {
    if (request !== orderRequest) return

    if (silent) {
      actionError.value = isApiError(err) ? err.message : '订单刷新失败，请稍后重试。'
      return
    }

    if (isApiError(err)) {
      error.value = err.message
      notFound.value = err.code === 404
    } else {
      error.value = '订单加载失败，请稍后重试。'
    }
  } finally {
    if (request === orderRequest) loading.value = false
  }
}

async function submitPayment(): Promise<void> {
  if (!order.value || paying.value || expired.value) return

  paying.value = true
  actionError.value = ''
  try {
    const updated = await payOrderRequest(orderNo.value, {}, { skipErrorToast: true })
    order.value = { ...updated }
    toast.success('支付成功，权益已开通')
    await loadOrder(true)
  } catch (err) {
    actionError.value = isApiError(err) ? err.message : '支付失败，请稍后重试。'
    toast.error(actionError.value)
    if (isApiError(err) && err.code === 409) await loadOrder(true)
  } finally {
    paying.value = false
  }
}

async function submitCancel(): Promise<void> {
  if (!order.value || canceling.value) return

  const ok = await confirm({
    title: '取消订单',
    message: '取消后需要重新下单，确定取消这笔订单？',
    confirmText: '取消订单',
    danger: true,
  })
  if (!ok) return

  canceling.value = true
  actionError.value = ''
  try {
    await cancelOrderRequest(orderNo.value, { skipErrorToast: true })
    toast.success('订单已取消')
    await loadOrder(true)
  } catch (err) {
    actionError.value = isApiError(err) ? err.message : '取消失败，请稍后重试。'
    toast.error(actionError.value)
  } finally {
    canceling.value = false
  }
}

function openRefund(): void {
  refundError.value = ''
  refundSimulate.value = 'success'
  refundOpen.value = true
}

async function submitRefund(): Promise<void> {
  if (!order.value || refunding.value) return

  refunding.value = true
  refundError.value = ''
  try {
    const payload = order.value.state === 3 ? {} : { simulate: refundSimulate.value }
    const updated = await refundOrderRequest(orderNo.value, payload, { skipErrorToast: true })
    order.value = { ...updated }
    refundOpen.value = false

    if (updated.state === 4) toast.success('退款成功，权益已关闭')
    else if (updated.state === 3) toast.info('退款处理中，请稍后查看结果')
    else toast.error('退款失败，权益保持不变')

    await loadOrder(true)
  } catch (err) {
    refundError.value = isApiError(err) ? err.message : '退款失败，请稍后重试。'
    toast.error(refundError.value)
  } finally {
    refunding.value = false
  }
}

watch(
  orderNo,
  () => {
    actionError.value = ''
    void loadOrder()
  },
  { immediate: true },
)

startClock()
onBeforeUnmount(stopClock)

useSeo({
  title: '订单支付',
  description: '订单金额、15 分钟支付倒计时、模拟支付与取消订单。',
})
</script>

<template>
  <PageContainer tag="section" size="narrow">
    <div class="order-page">
      <div v-if="loading" class="order-loading" aria-live="polite">
        <WhSkeleton width="22%" height="10px" />
        <WhSkeleton :lines="2" height="30px" />
        <WhSkeleton width="48%" height="11px" />
      </div>

      <div v-else-if="notFound" class="order-state">
        <p class="order-eyebrow">[ 404 / ORDER ]</p>
        <h1>订单不存在</h1>
        <p>订单号可能已失效，或者该订单不属于当前账号。</p>
        <RouterLink class="order-state__link" :to="{ name: 'home' }">返回首页</RouterLink>
      </div>

      <div v-else-if="error" class="order-state" role="alert">
        <p class="order-eyebrow">[ ERROR / ORDER ]</p>
        <h1>订单加载失败</h1>
        <p>{{ error }}</p>
        <WhButton size="sm" variant="ghost" @click="loadOrder()">重试</WhButton>
      </div>

      <template v-else-if="order">
        <header class="order-head">
          <div>
            <p class="order-eyebrow">[ ORDER / PAYMENT ]</p>
            <h1>订单支付</h1>
            <p class="order-no">NO. {{ order.orderNo }}</p>
          </div>
          <span
            class="order-chip"
            :class="expired ? 'is-state-expired' : `is-state-${order.state}`"
          >
            {{ expired ? '已过期' : stateLabel }}
          </span>
        </header>

        <section class="order-card">
          <ul class="order-items">
            <li v-for="item in order.items" :key="item.itemId" class="order-item">
              <span class="order-item__tag">专栏</span>
              <RouterLink v-if="columnLink" class="order-item__title" :to="columnLink">
                {{ item.title }}
              </RouterLink>
              <span v-else class="order-item__title">{{ item.title }}</span>
              <span class="order-item__price">
                {{ formatMoney(item.price, { freeLabel: '免费' }) }}
              </span>
            </li>
          </ul>

          <div class="order-total">
            <span>应付金额</span>
            <strong>{{ formatMoney(order.amount, { freeLabel: '免费' }) }}</strong>
          </div>

          <div
            v-if="order.state === 0"
            class="order-deadline"
            :class="{ 'is-expired': expired }"
            aria-live="polite"
          >
            <template v-if="expired">订单已过期，请返回专栏重新下单。</template>
            <template v-else
              >请在 <b>{{ countdownText }}</b> 内完成支付</template
            >
          </div>

          <div v-if="order.state === 1" class="order-success" role="status">
            <p class="order-success__eyebrow">[ PAID ]</p>
            <h2>支付成功，权益已开通</h2>
            <p>现在可以阅读该专栏的付费内容。</p>
          </div>

          <p v-if="order.state === 2" class="order-note">
            订单已取消，如需购买请返回专栏重新下单。
          </p>
          <p v-if="order.state === 3" class="order-note">退款处理中，完成后权益会关闭。</p>
          <p v-if="order.state === 4" class="order-note">订单已退款，权益已关闭。</p>
          <p v-if="refundFailed" class="order-note order-note--danger">
            上次退款失败，权益保持不变，可以重新申请。
          </p>

          <dl v-if="order.payment" class="order-payment">
            <div>
              <dt>支付单号</dt>
              <dd>{{ order.payment.paymentNo }}</dd>
            </div>
            <div>
              <dt>支付方式</dt>
              <dd>模拟支付</dd>
            </div>
            <div v-if="order.payment.paidTime">
              <dt>支付时间</dt>
              <dd>{{ formatDateTime(order.payment.paidTime) }}</dd>
            </div>
            <div v-if="order.payment.refundNo">
              <dt>退款单号</dt>
              <dd>{{ order.payment.refundNo }}</dd>
            </div>
            <div
              v-if="order.payment.refundAmount !== null && order.payment.refundAmount !== undefined"
            >
              <dt>退款金额</dt>
              <dd>{{ formatMoney(order.payment.refundAmount) }}</dd>
            </div>
            <div v-if="order.payment.refundState">
              <dt>退款状态</dt>
              <dd>{{ refundStateLabel }}</dd>
            </div>
            <div v-if="order.payment.refundTime">
              <dt>退款时间</dt>
              <dd>{{ formatDateTime(order.payment.refundTime) }}</dd>
            </div>
          </dl>

          <p v-if="actionError" class="order-error" role="alert">{{ actionError }}</p>

          <div class="order-actions">
            <template v-if="order.state === 0">
              <WhButton :loading="paying" :disabled="expired || canceling" @click="submitPayment">
                模拟支付
              </WhButton>
              <WhButton
                variant="ghost"
                :loading="canceling"
                :disabled="paying"
                @click="submitCancel"
              >
                取消订单
              </WhButton>
              <RouterLink v-if="expired && columnLink" class="order-link" :to="columnLink">
                重新下单
              </RouterLink>
            </template>
            <template v-else>
              <WhButton v-if="canRefund" variant="danger" @click="openRefund">
                {{ order.state === 3 ? '完成退款' : '申请退款' }}
              </WhButton>
              <RouterLink v-if="columnLink" class="order-link order-link--primary" :to="columnLink">
                查看专栏
              </RouterLink>
              <RouterLink class="order-link" :to="{ name: 'me-orders' }">我的订单</RouterLink>
            </template>
          </div>
        </section>

        <p class="order-footnote">模拟支付不会产生真实扣款；订单 15 分钟内有效。</p>
      </template>
    </div>

    <WhModal
      v-if="order"
      v-model="refundOpen"
      :title="order.state === 3 ? '完成退款' : '申请退款'"
      size="sm"
      :close-on-overlay="!refunding"
      :close-on-esc="!refunding"
      :show-close="!refunding"
    >
      <div class="order-refund">
        <p v-if="order.state === 3">退款正在处理中，点击下方按钮继续完成退款。</p>
        <p v-else>
          退款成功后，专栏阅读权益会立即关闭，退款金额为实付
          {{ formatMoney(order.amount) }}。
        </p>

        <div v-if="order.state !== 3" class="order-refund__field">
          <label for="refund-simulate">模拟退款结果</label>
          <WhSelect id="refund-simulate" v-model="refundSimulate" :options="refundOptions" />
        </div>

        <p v-if="refundError" class="order-error" role="alert">{{ refundError }}</p>
        <p class="order-refund__hint">这只是模拟渠道，不会产生真实退款。</p>
      </div>

      <template #footer>
        <WhButton variant="ghost" :disabled="refunding" @click="refundOpen = false">取消</WhButton>
        <WhButton variant="danger" :loading="refunding" @click="submitRefund">
          {{ order.state === 3 ? '完成退款' : '确认退款' }}
        </WhButton>
      </template>
    </WhModal>
  </PageContainer>
</template>

<style scoped>
.order-page {
  display: grid;
  gap: clamp(20px, 3vw, 32px);
  max-width: 720px;
  margin: 0 auto;
}

.order-loading {
  display: grid;
  gap: var(--space-4);
}

.order-state {
  display: grid;
  justify-items: start;
  gap: var(--space-3);
  padding: var(--space-16) 0;
}

.order-state h1 {
  font-family: var(--serif);
  font-size: var(--text-3xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.order-state p {
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.order-state__link {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.order-eyebrow {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.order-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--space-4);
  padding-bottom: var(--space-4);
  border-bottom: 1px solid var(--ink);
}

.order-head h1 {
  margin-top: var(--space-1);
  font-family: var(--serif);
  font-size: clamp(2rem, 5vw, 3rem);
  font-weight: var(--weight-black);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
}

.order-no {
  margin-top: var(--space-2);
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.order-chip {
  padding: 4px 10px;
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

.order-chip.is-state-expired {
  border-color: var(--verm);
  color: var(--verm-deep);
}

.order-chip.is-state-3,
.order-chip.is-state-4 {
  border-color: var(--violet);
  color: var(--violet);
}

.order-card {
  display: grid;
  gap: var(--space-5);
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--ink);
  background: var(--surface);
  box-shadow: var(--shadow-hard);
}

.order-items {
  display: grid;
  border-top: 1px solid var(--line);
}

.order-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--line);
}

.order-item__tag {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.order-item__title {
  overflow: hidden;
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-semibold);
  line-height: var(--leading-snug);
  text-overflow: ellipsis;
  white-space: nowrap;
}

a.order-item__title:hover {
  color: var(--verm-deep);
}

.order-item__price {
  color: var(--verm-deep);
  font-family: var(--serif);
  font-size: var(--text-lg);
  font-weight: var(--weight-black);
}

.order-total {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px solid var(--ink);
}

.order-total span {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.order-total strong {
  color: var(--verm-deep);
  font-family: var(--serif);
  font-size: var(--text-3xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-none);
}

.order-deadline {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--line-strong);
  color: var(--text-body);
  font-size: var(--text-sm);
}

.order-deadline b {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-md);
  font-variant-numeric: tabular-nums;
}

.order-deadline.is-expired {
  border-color: var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
}

.order-success {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-5);
  border: 1px solid var(--ink);
  background: var(--acid);
  color: var(--ink);
}

.order-success__eyebrow {
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.order-success h2 {
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  letter-spacing: var(--tracking-tight);
}

.order-success p {
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.order-note {
  padding: var(--space-3) var(--space-4);
  border: 1px dashed var(--line-strong);
  color: var(--text-muted);
  font-size: var(--text-sm);
}

.order-note--danger {
  border-color: var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
}

.order-refund {
  display: grid;
  gap: var(--space-4);
}

.order-refund p {
  color: var(--text-body);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.order-refund .order-error {
  color: var(--verm-deep);
}

.order-refund__field {
  display: grid;
  gap: var(--space-2);
}

.order-refund__field label {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
  text-transform: uppercase;
}

.order-refund__hint {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

.order-payment {
  display: grid;
  gap: var(--space-2);
  padding-top: var(--space-3);
  border-top: 1px solid var(--line);
}

.order-payment div {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: var(--space-3);
}

.order-payment dt {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.order-payment dd {
  margin: 0;
  font-family: var(--mono);
  font-size: var(--text-xs);
}

.order-error {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
  font-size: var(--text-sm);
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-3);
  padding-top: var(--space-2);
}

.order-link {
  padding-bottom: 2px;
  border-bottom: 1px solid var(--ink);
  color: var(--ink);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wide);
}

.order-link:hover {
  color: var(--verm-deep);
  border-color: var(--verm-deep);
}

.order-link--primary {
  border-color: var(--verm-deep);
  color: var(--verm-deep);
}

.order-footnote {
  color: var(--text-muted);
  font-family: var(--mono);
  font-size: var(--text-2xs);
}

@media (max-width: 640px) {
  .order-card {
    box-shadow: var(--shadow-hard-sm);
  }

  .order-item {
    grid-template-columns: auto minmax(0, 1fr);
  }

  .order-item__price {
    grid-column: 2;
  }

  .order-item__title {
    white-space: normal;
  }
}
</style>
