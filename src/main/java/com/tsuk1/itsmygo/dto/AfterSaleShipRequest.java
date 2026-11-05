package com.tsuk1.itsmygo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 买家填写退货物流（演示环境：手填单号，不对接真实快递）。 */
public record AfterSaleShipRequest(
        @NotBlank(message = "请填写退货单号")
        @Size(max = 64, message = "单号最长 64 个字符")
        String trackingNo,

        @Size(max = 32, message = "快递公司最长 32 个字符")
        String carrier) {
}
