package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cart_items")
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    /** 具体买哪个规格。同一个人可以把同一商品的不同规格各放一行 */
    private Long skuId;

    private Integer quantity;

    /** 1 勾选 / 0 未勾选 */
    private Integer selected;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
