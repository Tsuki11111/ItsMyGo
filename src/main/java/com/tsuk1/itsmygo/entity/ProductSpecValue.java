package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 规格值，如「云宝」。归属一个 {@link ProductSpec} */
@Data
@TableName("product_spec_values")
public class ProductSpecValue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long specId;

    private String value;

    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
