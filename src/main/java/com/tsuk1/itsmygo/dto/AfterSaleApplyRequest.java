package com.tsuk1.itsmygo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 申请售后：选订单、售后类型、要退的订单项、原因与凭证。 */
public record AfterSaleApplyRequest(
        @jakarta.validation.constraints.NotNull(message = "请选择订单")
        Long orderId,

        /** REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款 */
        @NotBlank(message = "请选择售后类型")
        String type,

        @NotBlank(message = "请选择售后原因")
        String reasonCode,

        @Size(max = 200, message = "补充说明最长 200 个字符")
        String reasonText,

        @Size(max = 3, message = "凭证最多 3 张")
        List<String> evidenceImages,

        @NotEmpty(message = "请选择要退的商品")
        List<@Valid AfterSaleItemRequest> items) {
}
