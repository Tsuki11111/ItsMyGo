import { get } from './http'
import type { MerchantView } from './types'

/** 公开店铺信息（只返回 ACTIVE 店铺） */
export function fetchShop(id: number): Promise<MerchantView> {
  return get<MerchantView>(`/shops/${id}`)
}
