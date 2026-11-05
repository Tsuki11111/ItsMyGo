package com.tsuk1.itsmygo.service;

import com.tsuk1.itsmygo.dto.AfterSaleApplyRequest;
import com.tsuk1.itsmygo.dto.AfterSaleReasonRequest;
import com.tsuk1.itsmygo.dto.AfterSaleReasonView;
import com.tsuk1.itsmygo.dto.AfterSaleRejectRequest;
import com.tsuk1.itsmygo.dto.AfterSaleShipRequest;
import com.tsuk1.itsmygo.dto.AfterSaleStatsView;
import com.tsuk1.itsmygo.dto.AfterSaleView;
import com.tsuk1.itsmygo.dto.PageResponse;

import java.util.List;

/**
 * 售后单（M30）：申请 / 审核 / 退货 / 退款 / 撤销 / 超时处理。
 *
 * <p>金额口径与状态机见 docs/requirements.md §2.5：
 * 项金额 = 单价 × 退款数量，优惠按订单等比分摊，尾差归最后一次退款；
 * 券只在订单全部退完时退回。</p>
 */
public interface AfterSaleService {

    /* ---------- 顾客 ---------- */

    List<AfterSaleReasonView> reasons();

    AfterSaleView apply(Long userId, AfterSaleApplyRequest request);

    PageResponse<AfterSaleView> pageForCustomer(Long userId, int page, int size, String status, Long orderId);

    AfterSaleView detailForCustomer(Long userId, Long id);

    AfterSaleView cancel(Long userId, Long id);

    AfterSaleView shipBack(Long userId, Long id, AfterSaleShipRequest request);

    /* ---------- 商家 ---------- */

    PageResponse<AfterSaleView> pageForMerchant(Long merchantId, int page, int size, String status);

    AfterSaleView detailForMerchant(Long merchantId, Long id);

    AfterSaleView approveForMerchant(Long merchantId, Long id);

    AfterSaleView rejectForMerchant(Long merchantId, Long id, AfterSaleRejectRequest request);

    AfterSaleView confirmReceivedForMerchant(Long merchantId, Long id);

    AfterSaleStatsView statsForMerchant(Long merchantId);

    /* ---------- 管理端 ---------- */

    PageResponse<AfterSaleView> pageForAdmin(int page, int size, String status, String keyword);

    AfterSaleView detailForAdmin(Long id);

    AfterSaleView approveForAdmin(Long id);

    AfterSaleView rejectForAdmin(Long id, AfterSaleRejectRequest request);

    AfterSaleView confirmReceivedForAdmin(Long id);

    AfterSaleStatsView statsForAdmin();

    List<AfterSaleReasonView> adminReasons();

    AfterSaleReasonView createReason(AfterSaleReasonRequest request);

    AfterSaleReasonView updateReason(Long id, AfterSaleReasonRequest request);

    /* ---------- 超时扫描（系统） ---------- */

    int autoApproveExpired(int reviewHours);

    int autoConfirmReturnExpired(int returnConfirmDays);
}
