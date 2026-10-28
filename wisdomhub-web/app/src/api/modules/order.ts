import { get, post, type RequestOptions } from '@/api/http'
import type { PageQuery, PageResponse } from '@/types/common'
import type {
  OrderCreatePayload,
  OrderListItemVO,
  OrderPayPayload,
  OrderVO,
  RefundPayload,
} from '@/types/order'

export function create(
  payload: OrderCreatePayload,
  options: RequestOptions = {},
): Promise<OrderVO> {
  return post<OrderVO>('/api/order', payload, options)
}

export function pay(
  orderNo: string,
  payload: OrderPayPayload = {},
  options: RequestOptions = {},
): Promise<OrderVO> {
  return post<OrderVO>(`/api/order/${orderNo}/pay`, payload, options)
}

export function cancel(orderNo: string, options: RequestOptions = {}): Promise<void> {
  return post<void>(`/api/order/${orderNo}/cancel`, undefined, options)
}

export function refund(
  orderNo: string,
  payload: RefundPayload = {},
  options: RequestOptions = {},
): Promise<OrderVO> {
  return post<OrderVO>(`/api/order/${orderNo}/refund`, payload, options)
}

export function listMine(
  params: PageQuery = {},
  options: RequestOptions = {},
): Promise<PageResponse<OrderListItemVO>> {
  return get<PageResponse<OrderListItemVO>>('/api/order/mine', params, options)
}

export function detail(orderNo: string, options: RequestOptions = {}): Promise<OrderVO> {
  return get<OrderVO>(`/api/order/${orderNo}`, undefined, options)
}
