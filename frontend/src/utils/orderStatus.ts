/**
 * 订单状态的中文名与标签颜色。
 *
 * 管理端订单列表返回的是原始 `Order`（没有 `statusLabel`），所以前端要自己映射。
 * 顾客端列表走的是 `OrderView`，后端已经给了中文名，不需要用这里。
 */

export type OrderStatusTag = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const STATUS_MAP: Record<string, { label: string; tag: OrderStatusTag }> = {
  PENDING_PAYMENT: { label: '待支付', tag: 'warning' },
  PAID: { label: '待发货', tag: 'primary' },
  SHIPPED: { label: '待收货', tag: 'primary' },
  COMPLETED: { label: '已完成', tag: 'success' },
  CANCELLED: { label: '已取消', tag: 'info' },
  REFUNDING: { label: '退款中', tag: 'warning' },
  REFUNDED: { label: '已退款', tag: 'danger' },
}

export function orderStatusLabel(status: string): string {
  return STATUS_MAP[status]?.label ?? status
}

export function orderStatusTag(status: string): OrderStatusTag {
  return STATUS_MAP[status]?.tag ?? 'info'
}

/** 管理端筛选下拉用；管理端接口只接受单个状态名 */
export const ORDER_STATUS_OPTIONS = [
  { value: 'PENDING_PAYMENT', label: '待支付' },
  { value: 'PAID', label: '待发货' },
  { value: 'SHIPPED', label: '待收货' },
  { value: 'COMPLETED', label: '已完成' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'REFUNDING', label: '退款中' },
  { value: 'REFUNDED', label: '已退款' },
]
