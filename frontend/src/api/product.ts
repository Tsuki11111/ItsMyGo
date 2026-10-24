import { get } from './http'
import type { BrandView, CategoryNode, PageResponse, Product, StockView } from './types'

export interface ProductQuery {
  page?: number
  size?: number
  categoryId?: number
  keyword?: string
  /** 品牌专区用：按品牌精确筛选 */
  brand?: string
  /** 店铺主页用：只看该商户的商品 */
  merchantId?: number
}

export function fetchProducts(query: ProductQuery): Promise<PageResponse<Product>> {
  return get<PageResponse<Product>>('/products', { ...query })
}

/** 品牌专区：在售商品的品牌与数量 */
export function fetchBrands(): Promise<BrandView[]> {
  return get<BrandView[]>('/products/brands')
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
