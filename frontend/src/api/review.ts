import { get, post } from './http'
import type {
  MyReviewView,
  PageResponse,
  ReviewPayload,
  ReviewSummary,
  ReviewView,
} from './types'

export function createReview(payload: ReviewPayload): Promise<void> {
  return post<void>('/reviews', payload)
}

export function fetchMyReviews(page = 1, size = 10): Promise<PageResponse<MyReviewView>> {
  return get<PageResponse<MyReviewView>>('/reviews/mine', { page, size })
}

/** 商品公开评价（匿名可访问） */
export function fetchProductReviews(
  productId: number,
  page = 1,
  size = 5,
): Promise<PageResponse<ReviewView>> {
  return get<PageResponse<ReviewView>>(`/products/${productId}/reviews`, { page, size })
}

export function fetchReviewSummary(productId: number): Promise<ReviewSummary> {
  return get<ReviewSummary>(`/products/${productId}/reviews/summary`)
}
