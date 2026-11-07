package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品价格历史：智能推广用它判断最近 30 天是否降价。 */
@Data
@TableName("product_price_history")
public class ProductPriceHistory {

    public static final String REASON_BASELINE = "baseline";
    public static final String REASON_UPDATE = "update";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    /** 商品级变更为 NULL；规格级变更记具体 SKU */
    private Long skuId;

    private BigDecimal price;

    /** baseline / update */
    private String reason;

    private LocalDateTime changedAt;
}
