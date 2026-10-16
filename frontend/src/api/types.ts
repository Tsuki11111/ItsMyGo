/**
 * 与后端 DTO 一一对应的类型。
 *
 * 手写而非从 OpenAPI 生成 —— 后端接口就十来个，手写更直接。
 * 后端改了字段记得同步这里（这是手写类型唯一的成本）。
 */

/** 后端统一响应体：`{ code, message, data }` */
export interface Result<T> {
  code: number
  message: string
  data: T
}

export interface PageResponse<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export interface Product {
  id: number
  categoryId: number
  name: string
  subtitle: string
  mainImage: string
  /** 后端是 DECIMAL / BigDecimal，序列化成 JSON 数字。
   *  本项目金额量级远小于 2^53，用 number 安全；真做金融系统应该让它序列化成字符串。 */
  price: number
  stock: number
  sales: number
  detail: string | null
  /** 1 上架 / 0 下架 */
  status: number
  createdAt: string
  updatedAt: string
}

export interface CategoryNode {
  id: number
  parentId: number
  name: string
  sort: number
  status: number
  children: CategoryNode[]
}
