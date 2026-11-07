import { get } from './http'

/** 品类 / 商品排行行 */
export interface ConsumptionRankItem {
  name: string
  /** 两位小数字符串 */
  amount: string
  /** 件数 */
  count: number
}

/** 店铺排行行 */
export interface ConsumptionShopItem {
  name: string
  amount: string
}

/** 月度消费行，month 形如 2026-11 */
export interface ConsumptionMonthlyItem {
  month: string
  amount: string
}

/** 个人消费报告（后端金额统一为两位小数字符串） */
export interface ConsumptionReport {
  label: string
  orderCount: number
  paidAmount: string
  refundedAmount: string
  netAmount: string
  discountAmount: string
  avgOrderAmount: string
  categories: ConsumptionRankItem[]
  merchants: ConsumptionShopItem[]
  topProducts: ConsumptionRankItem[]
  monthly: ConsumptionMonthlyItem[]
}

export interface ConsumptionReportQuery {
  period: 'month' | 'year'
  /** 月份 YYYY-MM 或年份 YYYY；不传由后端取当前月/年 */
  date?: string
}

export function fetchConsumptionReport(query: ConsumptionReportQuery): Promise<ConsumptionReport> {
  return get<ConsumptionReport>('/reports/consumption', { ...query })
}
