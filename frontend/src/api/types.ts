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
  /** CUSTOMER / MERCHANT / ADMIN */
  role: string
  /** 商家账号才有 */
  merchantId?: number | null
  merchantName?: string
  /** PENDING / ACTIVE / REJECTED / DISABLED */
  merchantStatus?: string
}

/** 用户中心里的个人资料（不含密码与账号状态） */
export interface UserProfile {
  id: number
  username: string
  nickname: string
  phone: string
  avatar: string
  role: string
  merchantId?: number | null
  merchantName?: string
  merchantStatus?: string
}

export interface Product {
  id: number
  /** 所属商户；商品详情/店铺页用 */
  merchantId: number
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
  /** 当前订单项是否已评价；后端只在已完成订单里返回 */
  reviewed?: boolean
  createdAt?: string
}

export interface Order {
  id: number
  orderNo: string
  userId: number
  /** 实付金额 = originalAmount - discountAmount */
  totalAmount: number
  /** 优惠前商品总额 */
  originalAmount: number
  /** 优惠金额；未用券为 0 */
  discountAmount: number
  userCouponId: number | null
  /** 券名称快照；未用券为空串 */
  couponName: string
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

// ── 管理端请求 / 回显 ──────────────────────────────────────────────

/** 管理端：某个商品的规格与 SKU 回显 */
export interface ProductSkuView {
  specs: ProductSpec[]
  skus: ProductSku[]
}

/** 管理端：分类新增/编辑。parentId 传 0 表示顶层 */
export interface CategoryPayload {
  parentId: number | null
  name: string
  sort: number
  enabled: boolean
}

/** 管理端：商品新增/编辑 */
export interface ProductPayload {
  categoryId: number
  name: string
  subtitle: string
  mainImage: string
  price: number
  stock: number
  detail: string
  onSale: boolean
}

/** 管理端：规格值。已有值传真实正数 id，新建值传负数临时 id */
export interface SpecValuePayload {
  id: number
  value: string
}

/** 管理端：规格。已有规格传真实 id，新建传 null */
export interface SpecPayload {
  id: number | null
  name: string
  values: SpecValuePayload[]
}

/** 管理端：单个 SKU 组合 */
export interface SkuPayload {
  /** 每组规格各选一个值；无规格商品传空数组 */
  specValueIds: number[]
  price: number
  /** 仅新建 SKU 生效；已有 SKU 的库存由后端保留 */
  stock: number
  image: string
}

/** 管理端：规格与 SKU 整组覆盖保存 */
export interface SkuSavePayload {
  specs: SpecPayload[]
  skus: SkuPayload[]
}

/** 管理端：调整已有 SKU 的库存（expectedStock 用于条件更新） */
export interface AdjustSkuStockPayload {
  stock: number
  expectedStock: number
}

/** 管理端：发货 */
export interface ShipPayload {
  trackingNo: string
}

// ── 用户中心后续功能 ──────────────────────────────────────────────

/** 收藏列表项 */
export interface FavoriteView {
  id: number
  productId: number
  name: string
  subtitle: string
  mainImage: string
  price: number
  /** 1 上架 / 0 下架；商品已删除时也为 0 */
  status: number
  createdAt: string
}

/** 足迹列表项 */
export interface FootprintView {
  id: number
  productId: number
  name: string
  subtitle: string
  mainImage: string
  price: number
  status: number
  viewedAt: string
}

/** 商品详情页公开评价 */
export interface ReviewView {
  id: number
  rating: number
  content: string
  userNickname: string
  userAvatar: string
  createdAt: string
}

export interface ReviewSummary {
  averageRating: number
  count: number
}

/** 我的评价 */
export interface MyReviewView {
  id: number
  orderItemId: number
  productId: number
  productName: string
  productImage: string
  rating: number
  content: string
  /** 1 展示 / 0 已被管理端隐藏 */
  status: number
  createdAt: string
}

/** 提交评价 */
export interface ReviewPayload {
  orderItemId: number
  rating: number
  content: string
}

/** 管理端评价列表项 */
export interface AdminReviewView {
  id: number
  userId: number
  userNickname: string
  productId: number
  productName: string
  rating: number
  content: string
  status: number
  createdAt: string
}

/** 优惠券模板（管理端） */
export interface Coupon {
  id: number
  name: string
  thresholdAmount: number
  discountAmount: number
  totalCount: number
  claimedCount: number
  validFrom: string
  validTo: string
  /** 1 可领 / 0 停用 */
  status: number
  createdAt: string
  updatedAt: string
}

/** 领券中心列表项 */
export interface CouponView {
  id: number
  name: string
  thresholdAmount: number
  discountAmount: number
  totalCount: number
  claimedCount: number
  remainingCount: number
  validFrom: string
  validTo: string
  claimed: boolean
}

/** 我的券 */
export interface UserCouponView {
  id: number
  couponId: number
  couponName: string
  thresholdAmount: number
  discountAmount: number
  validFrom: string
  validTo: string
  /** UNUSED / USED / EXPIRED（EXPIRED 可能由后端按数据库时间计算） */
  status: string
  usedOrderId: number | null
  createdAt: string
}

/** 管理端新建/编辑优惠券模板 */
export interface CouponPayload {
  name: string
  thresholdAmount: number
  discountAmount: number
  totalCount: number
  validFrom: string
  validTo: string
  enabled: boolean
}

// ── 多商户 ──────────────────────────────────────────────────────

/** 店铺信息：公开店铺页 / 商家资料 / 入驻申请状态共用 */
export interface MerchantView {
  id: number
  name: string
  logo: string
  description: string
  contactName: string
  contactPhone: string
  /** PENDING / ACTIVE / REJECTED / DISABLED */
  status: string
  rejectReason: string
  ownerUserId: number | null
  createdAt: string
}

/** 平台后台商户列表项 */
export interface AdminMerchantView extends MerchantView {
  ownerUsername: string
}

/** 入驻申请 / 店铺资料修改 */
export interface MerchantPayload {
  name: string
  logo: string
  description: string
  contactName: string
  contactPhone: string
}
