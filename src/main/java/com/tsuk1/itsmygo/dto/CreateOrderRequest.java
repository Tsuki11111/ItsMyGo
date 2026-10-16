package com.tsuk1.itsmygo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 下单：收货地址 + 提交令牌 + 商品来源。
 *
 * <p>商品有两种来源，**结算页对两者是同一套界面**（真实电商也是这么分的）：
 * <ul>
 *   <li><b>购物车结算</b> —— {@code items} 留空，商品从购物车勾选项取，下单后清掉这些行</li>
 *   <li><b>立即购买</b> —— {@code items} 直接给出要买的商品，**完全不碰购物车**</li>
 * </ul>
 */
public record CreateOrderRequest(
        @NotNull(message = "收货地址不能为空")
        Long addressId,

        /**
         * 提交令牌，先调 GET /api/orders/token 领取。
         * 一次性消费，重复提交会因令牌已被消费而被拒。
         */
        @NotBlank(message = "提交令牌不能为空，请刷新后重试")
        String token,

        /** 「立即购买」的商品行；为空表示走购物车结算 */
        @Valid
        List<BuyNowItem> items
) {
    public record BuyNowItem(
            @NotNull(message = "商品 ID 不能为空")
            Long productId,

            @NotNull(message = "数量不能为空")
            @Min(value = 1, message = "数量至少为 1")
            Integer quantity
    ) {
    }
}
