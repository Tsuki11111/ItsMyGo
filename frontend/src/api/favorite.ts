import { del, get, post } from './http'
import type { FavoriteView, PageResponse } from './types'

export function fetchFavorites(page = 1, size = 12): Promise<PageResponse<FavoriteView>> {
  return get<PageResponse<FavoriteView>>('/favorites', { page, size })
}

export function fetchFavoriteStatus(productId: number): Promise<boolean> {
  return get<boolean>(`/favorites/${productId}/status`)
}

/** 幂等收藏 */
export function addFavorite(productId: number): Promise<void> {
  return post<void>(`/favorites/${productId}`)
}

/** 幂等取消 */
export function removeFavorite(productId: number): Promise<void> {
  return del<void>(`/favorites/${productId}`)
}
