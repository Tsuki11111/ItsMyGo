import { post } from './http'
import type { CartView } from './types'

/** 加购。后端靠 `ON DUPLICATE KEY UPDATE` 累加，重复加同一商品是合法的 */
export function addToCart(productId: number, quantity: number): Promise<CartView> {
  return post<CartView>('/cart/items', { productId, quantity })
}
