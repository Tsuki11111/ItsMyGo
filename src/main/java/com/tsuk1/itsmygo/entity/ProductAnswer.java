package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品回答：只有买过该商品的用户可以回答 */
@Data
@TableName("product_answers")
public class ProductAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long userId;

    private String content;

    /** 1 展示 / 0 管理端隐藏 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
