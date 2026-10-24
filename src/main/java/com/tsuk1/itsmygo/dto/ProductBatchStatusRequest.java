package com.tsuk1.itsmygo.dto;

import java.util.List;

/** 管理端批量上下架商品。 */
public record ProductBatchStatusRequest(List<Long> ids, Boolean onSale) {
}
