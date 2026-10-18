import { get, post } from './http'
import type { CouponView, PageResponse, UserCouponView } from './types'

/** 领券中心：匿名可看；登录时 claimed 标记有效 */
export function fetchCouponCenter(): Promise<CouponView[]> {
  return get<CouponView[]>('/coupons')
}

export function claimCoupon(couponId: number): Promise<void> {
  return post<void>(`/coupons/${couponId}/claim`)
}

export function fetchMyCoupons(
  page = 1,
  size = 10,
  status?: string,
): Promise<PageResponse<UserCouponView>> {
  return get<PageResponse<UserCouponView>>('/coupons/mine', { page, size, status })
}

/** 结算页预校验：按商品总额筛可用券 */
export function fetchAvailableCoupons(amount: number): Promise<UserCouponView[]> {
  return get<UserCouponView[]>('/coupons/mine/available', { amount })
}
