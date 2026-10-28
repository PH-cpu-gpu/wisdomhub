import type { ID } from './common'

/** 0 待支付 1 已支付 2 已取消 3 退款中 4 已退款 */
export type OrderState = 0 | 1 | 2 | 3 | 4

/** 0 无 1 处理中 2 成功 3 失败 */
export type RefundState = 0 | 1 | 2 | 3

export type RefundSimulate = 'success' | 'failure' | 'processing'

export interface OrderItemVO {
  itemType: number
  itemId: ID
  authorId: ID
  title: string
  /** 成交单价，单位：分 */
  price: number
}

export interface PaymentVO {
  paymentNo: string
  payType: number
  state: number
  paidTime: string | null
  refundNo: string | null
  /** 退款金额，单位：分 */
  refundAmount: number | null
  refundState: RefundState
  refundTime: string | null
}

export interface OrderVO {
  orderNo: string
  /** 订单金额，单位：分 */
  amount: number
  state: OrderState
  expireTime: string
  paidTime: string | null
  refundTime: string | null
  createTime: string
  items: OrderItemVO[]
  /** 最近一笔成功支付，未支付时为 null */
  payment: PaymentVO | null
}

export interface OrderListItemVO {
  orderNo: string
  amount: number
  state: OrderState
  expireTime: string
  paidTime: string | null
  createTime: string
  items: OrderItemVO[]
}

export interface OrderCreatePayload {
  /** 1 专栏 */
  itemType: 1
  itemId: ID
}

export interface OrderPayPayload {
  /** 1 模拟支付；2/3 尚未接入 */
  payType?: 1
}

export interface RefundPayload {
  simulate?: RefundSimulate
}
