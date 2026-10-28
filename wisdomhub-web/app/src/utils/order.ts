import type { OrderState } from '@/types/order'

export const ORDER_STATE_LABELS: Record<OrderState, string> = {
  0: '待支付',
  1: '已支付',
  2: '已取消',
  3: '退款中',
  4: '已退款',
}

export function orderStateLabel(state?: number | null): string {
  if (state === null || state === undefined) return '—'
  return ORDER_STATE_LABELS[state as OrderState] ?? '未知状态'
}
