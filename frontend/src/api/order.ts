import { get, post } from './http'
import type { OrderView } from './types'

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
  /** 「立即购买」：直接指定商品；不传则从购物车勾选项下单 */
  items?: { productId: number; quantity: number }[]
}

export function createOrder(payload: CreateOrderPayload): Promise<OrderView> {
  return post<OrderView>('/orders', payload)
}

export function fetchOrder(id: number): Promise<OrderView> {
  return get<OrderView>(`/orders/${id}`)
}

export function payOrder(id: number): Promise<OrderView> {
  return post<OrderView>(`/orders/${id}/pay`)
}
