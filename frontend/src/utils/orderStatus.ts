/**
 * 订单状态的标签颜色。
 *
 * 状态中文名由后端 `Order.statusLabel` 统一提供，前端不再维护第二份文案。
 * 这里只保留 tag 颜色这种纯 UI 映射。
 */

export type OrderStatusTag = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const STATUS_TAGS: Record<string, OrderStatusTag> = {
  CREATING: 'info',
  CREATE_FAILED: 'danger',
  PENDING_PAYMENT: 'warning',
  PAID: 'primary',
  SHIPPED: 'primary',
  COMPLETED: 'success',
  CANCELLED: 'info',
  REFUNDING: 'warning',
  REFUNDED: 'danger',
}

export function orderStatusTag(status: string): OrderStatusTag {
  return STATUS_TAGS[status] ?? 'info'
}

/** 管理端筛选下拉用；管理端接口只接受单个状态名 */
export const ORDER_STATUS_OPTIONS = [
  { value: 'CREATING', label: '创建中' },
  { value: 'CREATE_FAILED', label: '创建失败' },
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDING', label: '退款中' },
  { value: 'REFUNDED', label: '已退款' },
]
