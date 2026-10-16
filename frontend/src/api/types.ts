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

/** 登录 / 注册成功后返回：JWT + 身份信息 */
export interface AuthResult {
  token: string
  userId: number
  username: string
  nickname: string
  /** CUSTOMER / ADMIN */
  role: string
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

/** 实时库存：数据来自 Redis 库存计数器，不走商品详情缓存 */
export interface StockView {
  productId: number
  stock: number
  /** 库存 > 0 */
  available: boolean
}

/**
 * 购物车行：行本身的数据 + 商品**当前**信息
 * （价格/库存/上架状态都可能变，下单时才固化成订单快照）。
 */
export interface CartItemView {
  id: number
  productId: number
  productName: string
  productImage: string
  price: number
  stock: number
  quantity: number
  subtotal: number
  selected: boolean
  onSale: boolean
  /** 在售且库存够本次数量 */
  available: boolean
}

export interface CartView {
  items: CartItemView[]
  /** 只算「已勾选 且 可购买」的项 */
  selectedTotal: number
}

export interface Address {
  id: number
  userId: number
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  /** 1 默认 / 0 普通 */
  isDefault: number
  createdAt: string
  updatedAt: string
}

export interface AddressPayload {
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: boolean
}

/** 订单明细 —— **下单时的快照**，商品之后改价改名都不影响它 */
export interface OrderItem {
  id: number
  orderId: number
  productId: number
  productName: string
  productImage: string
  unitPrice: number
  quantity: number
  subtotal: number
}

export interface Order {
  id: number
  orderNo: string
  userId: number
  totalAmount: number
  /** 见后端 OrderStatus 枚举名：PENDING_PAYMENT / PAID / SHIPPED / ... */
  status: string
  /** 以下三项是下单时的收货信息快照 */
  receiver: string
  phone: string
  address: string
  trackingNo: string
  paidAt: string | null
  shippedAt: string | null
  completedAt: string | null
  cancelledAt: string | null
  createdAt: string
  updatedAt: string
}

export interface OrderView {
  order: Order
  /** 状态中文名，由后端给 */
  statusLabel: string
  items: OrderItem[]
  itemCount: number
}
