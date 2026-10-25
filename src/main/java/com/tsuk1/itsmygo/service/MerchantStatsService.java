package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.MerchantDashboardView;

public interface MerchantStatsService {

    /** 当前商户的经营看板；merchantId 来自登录态，不接受客户端传入 */
    MerchantDashboardView dashboard(Long merchantId);
}
