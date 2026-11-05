import { get, post, postForm, put } from './http'
import type {
  AfterSaleReasonView,
  AfterSaleStatsView,
  AfterSaleView,
  PageResponse,
} from './types'

/* ==================== 顾客 ==================== */

export function fetchAfterSaleReasons(): Promise<AfterSaleReasonView[]> {
  return get<AfterSaleReasonView[]>('/after-sales/reasons')
}

export interface AfterSaleApplyPayload {
  orderId: number
  type: 'REFUND_ONLY' | 'RETURN_REFUND'
  reasonCode: string
  reasonText?: string
  evidenceImages?: string[]
  items: { orderItemId: number; quantity: number }[]
}

export function applyAfterSale(payload: AfterSaleApplyPayload): Promise<AfterSaleView> {
  return post<AfterSaleView>('/after-sales', payload)
}

export interface AfterSaleQuery {
  page?: number
  size?: number
  status?: string
  /** 只看某个订单的售后（订单详情页用） */
  orderId?: number
}

export function fetchAfterSales(query: AfterSaleQuery = {}): Promise<PageResponse<AfterSaleView>> {
  return get<PageResponse<AfterSaleView>>('/after-sales', { ...query })
}

export function fetchAfterSale(id: number): Promise<AfterSaleView> {
  return get<AfterSaleView>(`/after-sales/${id}`)
}

export function cancelAfterSale(id: number): Promise<AfterSaleView> {
  return post<AfterSaleView>(`/after-sales/${id}/cancel`)
}

export function shipBackAfterSale(
  id: number,
  payload: { trackingNo: string; carrier?: string },
): Promise<AfterSaleView> {
  return post<AfterSaleView>(`/after-sales/${id}/ship-back`, payload)
}

/** 售后凭证图：multipart 上传，返回同源相对路径 */
export function uploadAfterSaleEvidence(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  return postForm<string>('/after-sales/evidence', form, 30000)
}

/* ==================== 商家 ==================== */

export function fetchMerchantAfterSales(
  query: AfterSaleQuery = {},
): Promise<PageResponse<AfterSaleView>> {
  return get<PageResponse<AfterSaleView>>('/merchant/after-sales', { ...query })
}

export function fetchMerchantAfterSale(id: number): Promise<AfterSaleView> {
  return get<AfterSaleView>(`/merchant/after-sales/${id}`)
}

export function approveMerchantAfterSale(id: number): Promise<AfterSaleView> {
  return post<AfterSaleView>(`/merchant/after-sales/${id}/approve`)
}

export function rejectMerchantAfterSale(id: number, reason: string): Promise<AfterSaleView> {
  return post<AfterSaleView>(`/merchant/after-sales/${id}/reject`, { reason })
}

export function confirmMerchantAfterSale(id: number): Promise<AfterSaleView> {
  return post<AfterSaleView>(`/merchant/after-sales/${id}/confirm-received`)
}

export function fetchMerchantAfterSaleStats(): Promise<AfterSaleStatsView> {
  return get<AfterSaleStatsView>('/merchant/after-sales/stats')
}

/* ==================== 管理端 ==================== */

export function fetchAdminAfterSales(
  query: AfterSaleQuery & { keyword?: string } = {},
): Promise<PageResponse<AfterSaleView>> {
  return get<PageResponse<AfterSaleView>>('/admin/after-sales', { ...query })
}

export function fetchAdminAfterSale(id: number): Promise<AfterSaleView> {
  return get<AfterSaleView>(`/admin/after-sales/${id}`)
}

export function approveAdminAfterSale(id: number): Promise<AfterSaleView> {
  return post<AfterSaleView>(`/admin/after-sales/${id}/approve`)
}

export function rejectAdminAfterSale(id: number, reason: string): Promise<AfterSaleView> {
  return post<AfterSaleView>(`/admin/after-sales/${id}/reject`, { reason })
}

export function confirmAdminAfterSale(id: number): Promise<AfterSaleView> {
  return post<AfterSaleView>(`/admin/after-sales/${id}/confirm-received`)
}

export function fetchAdminAfterSaleStats(): Promise<AfterSaleStatsView> {
  return get<AfterSaleStatsView>('/admin/after-sales/stats')
}

export function fetchAdminAfterSaleReasons(): Promise<AfterSaleReasonView[]> {
  return get<AfterSaleReasonView[]>('/admin/after-sale-reasons')
}

export interface AfterSaleReasonPayload {
  code: string
  label: string
  sort?: number
  status?: number
}

export function createAdminAfterSaleReason(
  payload: AfterSaleReasonPayload,
): Promise<AfterSaleReasonView> {
  return post<AfterSaleReasonView>('/admin/after-sale-reasons', payload)
}

export function updateAdminAfterSaleReason(
  id: number,
  payload: AfterSaleReasonPayload,
): Promise<AfterSaleReasonView> {
  return put<AfterSaleReasonView>(`/admin/after-sale-reasons/${id}`, payload)
}
