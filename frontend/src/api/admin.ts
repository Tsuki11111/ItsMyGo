import { del, download, get, post, postForm, put } from './http'
import type {
  AdjustSkuStockPayload,
  AdminDashboardView,
  AdminMerchantView,
  AdminReviewView,
  CategoryNode,
  CategoryPayload,
  Coupon,
  CouponPayload,
  MerchantView,
  Order,
  OrderView,
  PageResponse,
  Product,
  ProductPayload,
  ProductSku,
  ProductSkuView,
  ShipAddressPayload,
  ShipPayload,
  SkuSavePayload,
} from './types'

// ── 数据概览 ──

/** 管理端数据概览：指标 + 待办 + 近 7 天成交 */
export function fetchAdminDashboard(): Promise<AdminDashboardView> {
  return get<AdminDashboardView>('/admin/dashboard')
}

// ── 分类 ──

/** 管理端分类树：含停用分类，children 嵌套 */
export function fetchAdminCategories(): Promise<CategoryNode[]> {
  return get<CategoryNode[]>('/admin/categories')
}

export function createCategory(payload: CategoryPayload): Promise<CategoryNode> {
  return post<CategoryNode>('/admin/categories', payload)
}

export function updateCategory(id: number, payload: CategoryPayload): Promise<CategoryNode> {
  return put<CategoryNode>(`/admin/categories/${id}`, payload)
}

export function deleteCategory(id: number): Promise<void> {
  return del<void>(`/admin/categories/${id}`)
}

// ── 商品 ──

export interface AdminProductQuery {
  page?: number
  size?: number
  categoryId?: number
  keyword?: string
  merchantId?: number
}

/** 管理端商品列表：含下架商品 */
export function fetchAdminProducts(query: AdminProductQuery): Promise<PageResponse<Product>> {
  return get<PageResponse<Product>>('/admin/products', { ...query })
}

export function createProduct(payload: ProductPayload): Promise<Product> {
  return post<Product>('/admin/products', payload)
}

export function updateProduct(id: number, payload: ProductPayload): Promise<Product> {
  return put<Product>(`/admin/products/${id}`, payload)
}

/** 手工重建布隆过滤器（手工插库/导入数据后补救用） */
export function rebuildBloomFilter(): Promise<number> {
  return post<number>('/admin/products/rebuild-bloom')
}

// ── 规格与 SKU ──

export function fetchProductSkus(productId: number): Promise<ProductSkuView> {
  return get<ProductSkuView>(`/admin/products/${productId}/skus`)
}

/** 整组覆盖保存；已有 SKU 的库存不会被改动 */
export function saveProductSkus(productId: number, payload: SkuSavePayload): Promise<ProductSkuView> {
  return put<ProductSkuView>(`/admin/products/${productId}/skus`, payload)
}

/** 调整已有 SKU 库存：expectedStock 是表单看到的值，被并发改过会被拒 */
export function adjustSkuStock(
  productId: number,
  skuId: number,
  payload: AdjustSkuStockPayload,
): Promise<ProductSku> {
  return put<ProductSku>(`/admin/products/${productId}/skus/${skuId}/stock`, payload)
}

// ── 文件上传 ──

/** 上传单张图片，返回可公开访问的 URL */
export function uploadImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return postForm<string>('/admin/files/upload', form)
}

// ── 订单 ──

export interface AdminOrderQuery {
  page?: number
  size?: number
  /** 单个状态枚举名；管理端接口不支持逗号分隔 */
  status?: string
  merchantId?: number
  /** 订单号 / 收货人 / 电话 / 运单号 */
  keyword?: string
  /** 下单日期，YYYY-MM-DD */
  dateFrom?: string
  dateTo?: string
}

export function fetchAdminOrders(query: AdminOrderQuery): Promise<PageResponse<Order>> {
  return get<PageResponse<Order>>('/admin/orders', { ...query })
}

/** 导出当前筛选条件下的订单 CSV */
export function exportAdminOrders(query: AdminOrderQuery): Promise<void> {
  return download('/admin/orders/export', { ...query }, 'orders.csv')
}

export function fetchAdminOrder(id: number): Promise<OrderView> {
  return get<OrderView>(`/admin/orders/${id}`)
}

export function shipOrder(id: number, payload: ShipPayload): Promise<OrderView> {
  return post<OrderView>(`/admin/orders/${id}/ship`, payload)
}

export function approveRefund(id: number): Promise<OrderView> {
  return post<OrderView>(`/admin/orders/${id}/refund/approve`)
}

export function rejectRefund(id: number): Promise<OrderView> {
  return post<OrderView>(`/admin/orders/${id}/refund/reject`)
}

/** 取消超时未支付订单，返回取消条数 */
export function cancelExpiredOrders(minutes: number): Promise<number> {
  return post<number>(`/admin/orders/cancel-expired?minutes=${minutes}`)
}

// ── 优惠券模板 ──

export interface AdminCouponQuery {
  page?: number
  size?: number
  enabled?: boolean
  keyword?: string
  merchantId?: number
}

export function fetchAdminCoupons(query: AdminCouponQuery): Promise<PageResponse<Coupon>> {
  return get<PageResponse<Coupon>>('/admin/coupons', { ...query })
}

export function createAdminCoupon(payload: CouponPayload): Promise<Coupon> {
  return post<Coupon>('/admin/coupons', payload)
}

export function updateAdminCoupon(id: number, payload: CouponPayload): Promise<Coupon> {
  return put<Coupon>(`/admin/coupons/${id}`, payload)
}

// ── 评价管理 ──

export interface AdminReviewQuery {
  page?: number
  size?: number
  visible?: boolean
  keyword?: string
  merchantId?: number
}

export function fetchAdminReviews(query: AdminReviewQuery): Promise<PageResponse<AdminReviewView>> {
  return get<PageResponse<AdminReviewView>>('/admin/reviews', { ...query })
}

export function setReviewVisible(id: number, visible: boolean): Promise<void> {
  return put<void>(`/admin/reviews/${id}/visible`, { visible })
}

// ── 商户审核 ──

export interface AdminMerchantQuery {
  page?: number
  size?: number
  /** PENDING / ACTIVE / REJECTED / DISABLED */
  status?: string
  keyword?: string
}

export function fetchAdminMerchants(
  query: AdminMerchantQuery,
): Promise<PageResponse<AdminMerchantView>> {
  return get<PageResponse<AdminMerchantView>>('/admin/merchants', { ...query })
}

export function approveMerchant(id: number): Promise<void> {
  return post<void>(`/admin/merchants/${id}/approve`)
}

export function rejectMerchant(id: number, reason: string): Promise<void> {
  return post<void>(`/admin/merchants/${id}/reject`, { reason })
}

export function disableMerchant(id: number): Promise<void> {
  return post<void>(`/admin/merchants/${id}/disable`)
}

export function enableMerchant(id: number): Promise<void> {
  return post<void>(`/admin/merchants/${id}/enable`)
}

/** 平台代设发货地址；平台自营（id=1）也走这里 */
export function updateAdminMerchantShipAddress(
  id: number,
  payload: ShipAddressPayload,
): Promise<MerchantView> {
  return put<MerchantView>(`/admin/merchants/${id}/ship-address`, payload)
}
