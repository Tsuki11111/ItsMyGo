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
  /** 头像 URL，空串表示没设置 */
  avatar: string
  /** CUSTOMER / ADMIN */
  role: string
}

/** 用户中心里的个人资料（不含密码与账号状态） */
export interface UserProfile {
  id: number
  username: string
  nickname: string
  phone: string
  avatar: string
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
  /** 多规格商品这里是各 SKU 的**汇总**，具体某一款的余量看 `skus` */
  stock: number
  sales: number
  /** 1 有多规格 / 0 单规格 —— 列表页据此显示「¥19.8 起」 */
  hasSpecs: number
  detail: string | null
  /** 1 上架 / 0 下架 */
  status: number
  createdAt: string
  updatedAt: string
  /** 规格定义。**只有详情接口会返回**（列表页里是 undefined） */
  specs?: ProductSpec[]
  /** SKU 列表（库存已是实时值）。同上，只有详情会返回 */
  skus?: ProductSku[]
}

export interface ProductSpecValue {
  id: number
  specId: number
  value: string
  sort: number
}

export interface ProductSpec {
  id: number
  productId: number
  name: string
  sort: number
  values: ProductSpecValue[]
}

export interface ProductSku {
  id: number
  productId: number
  /**
   * 升序逗号分隔的规格值 id，如 `"11,12"`。
   * 它是组合的规范键 —— 前端也靠它匹配「当前选中的组合对应哪一条 SKU」。
   * 无规格商品是空串。
   */
  specValueIds: string
  /** 规格描述，如「颜色:黑色;版本:静音版」 */
  specText: string
  price: number
  stock: number
  sales: number
  image: string
  status: number
}

export interface SkuStockView {
  skuId: number
  stock: number
  available: boolean
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
  /** 每个 SKU 的实时余量 —— 前端选规格时用它，不必为了一个数字重拉详情 */
  skus: SkuStockView[]
}

/**
 * 购物车行：行本身的数据 + 商品**当前**信息
 * （价格/库存/上架状态都可能变，下单时才固化成订单快照）。
 */
export interface CartItemView {
  id: number
  productId: number
  /** 具体买哪个规格 */
  skuId: number
  productName: string
  productImage: string
  /** 规格描述，如「颜色:黑色」；无规格商品是空串 */
  specText: string
  price: number
  stock: number
  quantity: number
  subtotal: number
  selected: boolean
  onSale: boolean
  /** 商品和规格都在售，且库存够本次数量 */
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
  /** 买的是哪个规格，仅供追溯 */
  skuId: number
  productName: string
  productImage: string
  /** 规格快照，如「颜色:黑色;版本:静音版」；单规格时代的旧订单是空串 */
  specText: string
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
