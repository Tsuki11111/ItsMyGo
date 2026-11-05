package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 售后单明细：一条售后包含哪些订单项、各退多少件。 */
@Data
@TableName("after_sale_items")
public class AfterSaleItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long afterSaleId;

    private Long orderItemId;

    private Long productId;

    private Long skuId;

    private String productName;

    private String productImage;

    private String specText;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal refundAmount;

    private LocalDateTime createdAt;
}
