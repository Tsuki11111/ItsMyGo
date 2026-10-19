package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品评价：一个订单项只能评一次（order_item_id 唯一） */
@Data
@TableName("reviews")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long orderId;

    private Long orderItemId;

    private Long productId;

    /** 所属商户（下单时快照，商品删除后仍可按店查询） */
    private Long merchantId;

    private Long skuId;

    /** 1-5 星 */
    private Integer rating;

    private String content;

    /** 1 展示 / 0 管理端隐藏 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
