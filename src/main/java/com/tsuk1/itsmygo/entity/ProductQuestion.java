package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品提问（问大家），与买家评分评价分开统计 */
@Data
@TableName("product_questions")
public class ProductQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Long userId;

    private String content;

    private Integer answerCount;

    /** 1 展示 / 0 管理端隐藏 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
