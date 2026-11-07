package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.report.ConsumptionReportView;

/** 个人消费报告：按用户 + 期间聚合已有订单数据，不写库、不调模型。 */
public interface ConsumptionReportService {

    /**
     * 生成当前用户的消费报告。
     *
     * @param userId 被查询用户（顾客接口取登录态，内部接口取票据里的 userId）
     * @param period month（默认当月）/ year（默认当年）
     * @param date   可选，YYYY-MM（按月）或 YYYY（按年）；不传取当前月/年
     * @throws com.tsuk1.itsmygo.common.exception.BizException 参数不合法时 400
     */
    ConsumptionReportView report(long userId, String period, String date);
}
