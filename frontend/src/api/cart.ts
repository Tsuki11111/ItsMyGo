import { post } from './http'
import type { CartView } from './types'

/**
 * 加购。后端靠 `ON DUPLICATE KEY UPDATE` 累加，重复加同一规格是合法的。
 *
 * `skuId` 是必须的 —— 无规格商品传它的**默认 SKU**（详情里 `skus` 只有一条、`specValueIds` 为空）。
 */
export function addToCart(productId: number, skuId: number, quantity: number): Promise<CartView> {
  return post<CartView>('/cart/items', { productId, skuId, quantity })
}
