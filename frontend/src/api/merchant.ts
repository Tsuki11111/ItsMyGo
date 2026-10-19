import { get, post, postForm, put } from './http'
import type {
  AdjustSkuStockPayload,
  MerchantPayload,
  MerchantView,
  PageResponse,
  Product,
  ProductPayload,
  ProductSku,
  ProductSkuView,
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

/** 商家后台：自己的店铺资料 */
export function fetchMerchantProfile(): Promise<MerchantView> {
  return get<MerchantView>('/merchant/profile')
}

export function updateMerchantProfile(payload: MerchantPayload): Promise<MerchantView> {
  return put<MerchantView>('/merchant/profile', payload)
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
