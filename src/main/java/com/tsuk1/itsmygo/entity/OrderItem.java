package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单项。商品名/图片/单价都是**下单时的快照**，
 * 商品事后改名改价，历史订单必须保持原样。
 */
@Data
@TableName("order_items")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    /** 仅供追溯，展示时**不要** join 商品表取当前值 */
    private Long productId;

    /** 买的是哪个规格，同样仅供追溯 */
    private Long skuId;

    private String productName;

    private String productImage;

    /**
     * 规格快照，如「颜色:黑色;版本:静音版」。
     * **必须是快照**：规格值会改名、会被删除，历史订单得原样显示，
     * 也不能展示时去 join 已经没了的 spec 表 —— 跟商品名/图/单价是一个道理。
     * 单规格时代的旧订单是空串。
     */
    private String specText;

    private BigDecimal unitPrice;

    private Integer quantity;

    private BigDecimal subtotal;

    private LocalDateTime createdAt;
}
