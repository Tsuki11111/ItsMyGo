import { get, post, put } from './http'
import type { MerchantPayload, MerchantView } from './types'

/** 顾客提交/重新提交入驻申请 */
export function applyMerchant(payload: MerchantPayload): Promise<MerchantView> {
  return post<MerchantView>('/merchant-apply', payload)
}

/** 当前用户的申请状态；没有申请时 data 为 null */
export function fetchMerchantApplication(): Promise<MerchantView | null> {
  return get<MerchantView | null>('/merchant-apply')
}

/** 商家后台：自己的店铺资料 */
export function fetchMerchantProfile(): Promise<MerchantView> {
  return get<MerchantView>('/merchant/profile')
}

export function updateMerchantProfile(payload: MerchantPayload): Promise<MerchantView> {
  return put<MerchantView>('/merchant/profile', payload)
}
