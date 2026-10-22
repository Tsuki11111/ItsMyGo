import { get, post } from './http'
import type { CheckoutView, LogisticsView, OrderView, PageResponse } from './types'

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
  /** 每个店铺选用的用户券；平台券只能分配给一个店铺 */
  coupons?: { merchantId: number; userCouponId: number }[]
}

export function createOrder(payload: CreateOrderPayload): Promise<CheckoutView> {
  return post<CheckoutView>('/orders', payload)
}

export function fetchOrder(id: number): Promise<OrderView> {
  return get<OrderView>(`/orders/${id}`)
}

/** 虚拟物流；未发货时返回 null */
export function fetchOrderLogistics(id: number): Promise<LogisticsView | null> {
  return get<LogisticsView | null>(`/orders/${id}/logistics`)
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

/** 取消订单：待支付 / 待发货可用；会回滚库存，用券时未过期券会退回 */
export function cancelOrder(id: number): Promise<OrderView> {
  return post<OrderView>(`/orders/${id}/cancel`)
}

/** 确认收货：仅待收货可用 */
export function confirmOrder(id: number): Promise<OrderView> {
  return post<OrderView>(`/orders/${id}/confirm`)
}

/** 申请退款：待发货 / 待收货 / 已完成可用，之后进入「退款中」等管理端审核 */
export function applyRefund(id: number, reason: string): Promise<OrderView> {
  return post<OrderView>(`/orders/${id}/refund`, { reason })
}
