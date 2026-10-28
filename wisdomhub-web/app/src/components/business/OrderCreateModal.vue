<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { isApiError } from '@/api/error'
import { create as createOrder } from '@/api/modules/order'
import WhButton from '@/components/base/WhButton.vue'
import WhModal from '@/components/base/WhModal.vue'
import { toast } from '@/components/base/toast'
import type { ID } from '@/types/common'
import type { OrderVO } from '@/types/order'
import { formatMoney } from '@/utils'

const props = defineProps<{
  modelValue: boolean
  itemId: ID
  title: string
  price: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [order: OrderVO]
  'already-purchased': []
}>()

const router = useRouter()
const submitting = ref(false)
const error = ref('')

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const priceLabel = computed(() => formatMoney(props.price, { freeLabel: '免费' }))

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) error.value = ''
  },
)

async function submit(): Promise<void> {
  if (submitting.value) return

  if (!props.itemId) {
    error.value = '专栏信息缺失，请刷新后重试。'
    return
  }

  submitting.value = true
  error.value = ''

  try {
    const order = await createOrder({ itemType: 1, itemId: props.itemId }, { skipErrorToast: true })
    emit('created', order)
    open.value = false
    toast.success('订单已创建，请在 15 分钟内完成支付')
    await router.push({ name: 'order-detail', params: { orderNo: order.orderNo } })
  } catch (err) {
    // 已购买：订单模块无法继续，回源刷新购买状态
    if (isApiError(err) && err.code === 409) {
      toast.error(err.message)
      open.value = false
      emit('already-purchased')
      return
    }
    error.value = isApiError(err) ? err.message : '创建订单失败，请稍后重试。'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <WhModal
    v-model="open"
    title="确认购买"
    size="sm"
    :close-on-overlay="!submitting"
    :close-on-esc="!submitting"
    :show-close="!submitting"
  >
    <div class="order-confirm">
      <div class="order-confirm__item">
        <span class="order-confirm__tag">专栏</span>
        <h3>{{ title }}</h3>
        <p class="order-confirm__price">{{ priceLabel }}</p>
      </div>

      <p class="order-confirm__note">
        创建订单后请在 15 分钟内完成支付；已有未过期的待支付订单会直接复用，不会重复下单。
      </p>

      <p v-if="error" class="order-confirm__error" role="alert">{{ error }}</p>
    </div>

    <template #footer>
      <WhButton variant="ghost" :disabled="submitting" @click="open = false">取消</WhButton>
      <WhButton :loading="submitting" @click="submit">
        {{ submitting ? '创建中…' : '创建订单' }}
      </WhButton>
    </template>
  </WhModal>
</template>

<style scoped>
.order-confirm {
  display: grid;
  gap: var(--space-4);
}

.order-confirm__item {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid var(--ink);
  background: var(--bone);
}

.order-confirm__tag {
  color: var(--verm-deep);
  font-family: var(--mono);
  font-size: var(--text-2xs);
  letter-spacing: var(--tracking-wider);
}

.order-confirm__item h3 {
  font-family: var(--serif);
  font-size: var(--text-xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-snug);
  letter-spacing: var(--tracking-tight);
}

.order-confirm__price {
  color: var(--verm-deep);
  font-family: var(--serif);
  font-size: var(--text-2xl);
  font-weight: var(--weight-black);
  line-height: var(--leading-none);
}

.order-confirm__note {
  color: var(--text-muted);
  font-size: var(--text-sm);
  line-height: var(--leading-relaxed);
}

.order-confirm__error {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--verm);
  background: rgb(255 59 31 / 8%);
  color: var(--verm-deep);
  font-size: var(--text-sm);
}
</style>
