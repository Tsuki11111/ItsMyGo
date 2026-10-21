package com.tsuk1.itsmygo.dto.agent;

import java.math.BigDecimal;

/** agent 商品搜索入参：结构化筛选 + 排序，模型只选参数，不写 SQL。 */
public record AgentProductSearchRequest(
        String keyword,
        Long categoryId,
        BigDecimal priceMin,
        BigDecimal priceMax,
        String sort,
        Integer limit) {
}
