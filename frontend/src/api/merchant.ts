import { get, post, postForm, put } from './http'
import type {
  AdjustSkuStockPayload,
  AdminReviewView,
  Coupon,
  CouponPayload,
  MerchantPayload,
  MerchantView,
  Order,
  OrderView,
  PageResponse,
  Product,
  ProductPayload,
  ProductSku,
  ProductSkuView,
  ShipAddressPayload,
  SkuSavePayload,
} from './types'

/** 顾客提交/重新提交入驻申请 */
export function applyMerchant(payload: MerchantPayload): Promise<MerchantView> {
  return post<MerchantView>('/merchant-apply', payload)
}

/** 当前用户的申请状态；没有申请时 data 为 null */
export function fetchMerchantApplication(): Promise<MerchantView | null> {
  return get<MerchantView | null>('/merchant-apply')
}

/** 入驻申请：申请人还没有商家角色，走单独开放的 Logo 上传接口 */
export function uploadMerchantApplyLogo(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return postForm<string>('/merchant-apply/logo', form)
}

/** 商家后台：自己的店铺资料 */
export function fetchMerchantProfile(): Promise<MerchantView> {
  return get<MerchantView>('/merchant/profile')
}

export function updateMerchantProfile(payload: MerchantPayload): Promise<MerchantView> {
  return put<MerchantView>('/merchant/profile', payload)
}

/** 商家设置发货地址：虚拟物流从这里出发 */
export function updateMerchantShipAddress(payload: ShipAddressPayload): Promise<MerchantView> {
  return put<MerchantView>('/merchant/ship-address', payload)
}

// ── 商家商品 ──

export interface MerchantProductQuery {
  page?: number
  size?: number
  categoryId?: number
  keyword?: string
}

export function fetchMerchantProducts(
  query: MerchantProductQuery,
): Promise<PageResponse<Product>> {
  return get<PageResponse<Product>>('/merchant/products', { ...query })
}

export function fetchMerchantProduct(id: number): Promise<Product> {
  return get<Product>(`/merchant/products/${id}`)
}

export function createMerchantProduct(payload: ProductPayload): Promise<Product> {
  return post<Product>('/merchant/products', payload)
}

export function updateMerchantProduct(id: number, payload: ProductPayload): Promise<Product> {
  return put<Product>(`/merchant/products/${id}`, payload)
}

export function fetchMerchantProductSkus(productId: number): Promise<ProductSkuView> {
  return get<ProductSkuView>(`/merchant/products/${productId}/skus`)
}

export function saveMerchantProductSkus(
  productId: number,
  payload: SkuSavePayload,
): Promise<ProductSkuView> {
  return put<ProductSkuView>(`/merchant/products/${productId}/skus`, payload)
}

export function adjustMerchantSkuStock(
  productId: number,
  skuId: number,
  payload: AdjustSkuStockPayload,
): Promise<ProductSku> {
  return put<ProductSku>(`/merchant/products/${productId}/skus/${skuId}/stock`, payload)
}

export function uploadMerchantImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return postForm<string>('/merchant/files/upload', form)
}

// ── 商家订单 ──

export interface MerchantOrderQuery {
  page?: number
  size?: number
  status?: string
}

export function fetchMerchantOrders(
  query: MerchantOrderQuery,
): Promise<PageResponse<Order>> {
  return get<PageResponse<Order>>('/merchant/orders', { ...query })
}

export function fetchMerchantOrder(id: number): Promise<OrderView> {
  return get<OrderView>(`/merchant/orders/${id}`)
}

export function shipMerchantOrder(id: number, trackingNo: string): Promise<OrderView> {
  return post<OrderView>(`/merchant/orders/${id}/ship`, { trackingNo })
}

export function approveMerchantRefund(id: number): Promise<OrderView> {
  return post<OrderView>(`/merchant/orders/${id}/refund/approve`)
}

export function rejectMerchantRefund(id: number): Promise<OrderView> {
  return post<OrderView>(`/merchant/orders/${id}/refund/reject`)
}

// ── 商家店铺券 ──

export interface MerchantCouponQuery {
  page?: number
  size?: number
  status?: string
  keyword?: string
}

export function fetchMerchantCoupons(
  query: MerchantCouponQuery,
): Promise<PageResponse<Coupon>> {
  return get<PageResponse<Coupon>>('/merchant/coupons', { ...query })
}

export function createMerchantCoupon(payload: CouponPayload): Promise<Coupon> {
  return post<Coupon>('/merchant/coupons', payload)
}

export function updateMerchantCoupon(id: number, payload: CouponPayload): Promise<Coupon> {
  return put<Coupon>(`/merchant/coupons/${id}`, payload)
}

// ── 商家评价 ──

export interface MerchantReviewQuery {
  page?: number
  size?: number
  visible?: boolean
  keyword?: string
}

export function fetchMerchantReviews(
  query: MerchantReviewQuery,
): Promise<PageResponse<AdminReviewView>> {
  return get<PageResponse<AdminReviewView>>('/merchant/reviews', { ...query })
}

export function setMerchantReviewVisible(id: number, visible: boolean): Promise<void> {
  return put<void>(`/merchant/reviews/${id}/visible`, { visible })
}
