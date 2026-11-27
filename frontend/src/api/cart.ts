import { del, get, post, put } from './http'
import type { CartCountView, CartView } from './types'

/**
 * 加购。后端靠 `ON DUPLICATE KEY UPDATE` 累加，重复加同一规格是合法的。
 *
 * `skuId` 是必须的 —— 无规格商品传它的**默认 SKU**（详情里 `skus` 只有一条、`specValueIds` 为空）。
 */
export function addToCart(productId: number, skuId: number, quantity: number): Promise<CartView> {
  return post<CartView>('/cart/items', { productId, skuId, quantity })
}

export function fetchCart(): Promise<CartView> {
  return get<CartView>('/cart')
}

/**
 * 顶栏角标专用。只返回数量，后端走 Redis 缓存，**最多旧 5 分钟**；
 * 需要实时明细（购物车页、结算页）仍然用 {@link fetchCart}。
 */
export function fetchCartCount(): Promise<CartCountView> {
  return get<CartCountView>('/cart/count')
}

/** 改数量或勾选状态。**两个参数都要传**（后端是整体覆盖那一行） */
export function updateCartItem(itemId: number, quantity: number, selected: boolean): Promise<CartView> {
  return put<CartView>(`/cart/items/${itemId}`, { quantity, selected })
}

export function removeCartItem(itemId: number): Promise<CartView> {
  return del<CartView>(`/cart/items/${itemId}`)
}
