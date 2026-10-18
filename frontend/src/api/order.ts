import { get, post } from './http'
import type { OrderView, PageResponse } from './types'

/**
 * 一次性提交令牌。
 *
 * ⚠️ **进结算页时领一次，提交时复用同一个** —— 提交时才领的话，
 * 连点两次会各领一个新令牌，防重复提交就形同虚设了（见 implementation §3.7）。
 */
export function fetchOrderToken(): Promise<string> {
  return get<string>('/orders/token')
}

export interface CreateOrderPayload {
  addressId: number
  token: string
  /** 「立即购买」：直接指定商品与规格；不传则从购物车勾选项下单 */
  items?: { productId: number; skuId: number; quantity: number }[]
  /** 使用的用户券 id；不传表示不用券 */
  couponId?: number
}

export function createOrder(payload: CreateOrderPayload): Promise<OrderView> {
  return post<OrderView>('/orders', payload)
}

export function fetchOrder(id: number): Promise<OrderView> {
  return get<OrderView>(`/orders/${id}`)
}

export interface OrderQuery {
  page?: number
  size?: number
  /** 每个订单给几条明细；0 表示全给 */
  previewLimit?: number
  /** 逗号分隔的订单状态名，如 `PAID,SHIPPED`；不传表示全部 */
  status?: string
}

export function fetchOrders(query: OrderQuery = {}): Promise<PageResponse<OrderView>> {
  return get<PageResponse<OrderView>>('/orders', { ...query })
}

export function payOrder(id: number): Promise<OrderView> {
  return post<OrderView>(`/orders/${id}/pay`)
}
