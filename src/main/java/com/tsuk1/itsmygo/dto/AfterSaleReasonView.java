package com.tsuk1.itsmygo.dto;

/** 售后原因（申请页下拉 / 管理端列表）。 */
public record AfterSaleReasonView(Long id, String code, String label, Integer sort, Integer status) {
}
