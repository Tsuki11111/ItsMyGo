import { get } from './http'
import type { CategoryNode, PageResponse, Product, StockView } from './types'

export interface ProductQuery {
  page?: number
  size?: number
  categoryId?: number
  keyword?: string
}

export function fetchProducts(query: ProductQuery): Promise<PageResponse<Product>> {
  return get<PageResponse<Product>>('/products', { ...query })
}

export function fetchProduct(id: number): Promise<Product> {
  return get<Product>(`/products/${id}`)
}

export function fetchCategoryTree(): Promise<CategoryNode[]> {
  return get<CategoryNode[]>('/categories')
}

/**
 * 实时库存 —— 走**独立接口**，不要用商品详情里的 stock 字段。
 * 详情接口虽然也会把 stock 替换成实时值，但这个接口语义更清楚，
 * 而且不需要为了一个数字去拉整个详情。
 */
export function fetchStock(id: number): Promise<StockView> {
  return get<StockView>(`/products/${id}/stock`)
}
