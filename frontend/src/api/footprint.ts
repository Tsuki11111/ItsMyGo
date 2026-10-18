import { del, get, post } from './http'
import type { FootprintView, PageResponse } from './types'

/** 商品详情加载成功后静默调用；同一商品只更新时间 */
export function recordFootprint(productId: number): Promise<void> {
  return post<void>(`/footprints/${productId}`)
}

export function fetchFootprints(page = 1, size = 12): Promise<PageResponse<FootprintView>> {
  return get<PageResponse<FootprintView>>('/footprints', { page, size })
}

export function clearFootprints(): Promise<void> {
  return del<void>('/footprints')
}
