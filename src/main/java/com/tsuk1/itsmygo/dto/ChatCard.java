package com.tsuk1.itsmygo.dto;

import java.math.BigDecimal;

/** 商品/订单卡片的冻结快照，存 messages.card_data。 */
public final class ChatCard {

    private ChatCard() {
    }

    public record Product(
            Long productId,
            String name,
            String image,
            BigDecimal price,
            Long merchantId,
            Integer status) {
    }

    public record Order(
            Long orderId,
            String orderNo,
            String status,
            String statusLabel,
            BigDecimal totalAmount,
            String firstItemName,
            String firstItemImage) {
    }
}
