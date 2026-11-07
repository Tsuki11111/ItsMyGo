package com.tsuk1.itsmygo.dto.report;

import java.util.List;

/**
 * 个人消费报告视图（顾客页与 agent 工具共用）。
 *
 * <p>金额一律是**两位小数字符串**：聚合在数据库里用 DECIMAL 完成，
 * 出参再 setScale(2) 转成字符串，避免前端 JS 浮点误差。
 *
 * @param label          期间文案，如「2026年11月」「2026年」
 * @param orderCount     期间内已支付订单数
 * @param paidAmount     实付金额合计（order.total_amount）
 * @param refundedAmount 已退金额合计（order.refunded_amount）
 * @param netAmount      净支出 = 实付 - 已退
 * @param discountAmount 优惠金额合计
 * @param avgOrderAmount 单笔均价 = 实付 / 订单数
 * @param categories     品类 Top3（按金额降序）
 * @param merchants      店铺 Top3（按金额降序）
 * @param topProducts    商品 Top3（按金额降序）
 * @param monthly        按月消费（仅按年查询有值；只含有订单的月份）
 */
public record ConsumptionReportView(
        String label,
        long orderCount,
        String paidAmount,
        String refundedAmount,
        String netAmount,
        String discountAmount,
        String avgOrderAmount,
        List<RankItem> categories,
        List<ShopItem> merchants,
        List<RankItem> topProducts,
        List<MonthlyItem> monthly) {

    /** 品类 / 商品排行行。count 是件数（quantity 之和）。 */
    public record RankItem(String name, String amount, long count) {
    }

    /** 店铺排行行：只到店，不到商品件数。 */
    public record ShopItem(String name, String amount) {
    }

    /** 月份聚合行，month 形如 2026-11。 */
    public record MonthlyItem(String month, String amount) {
    }
}
