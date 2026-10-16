package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 商品规格定义，如「款式」。规格值见 {@link ProductSpecValue} */
@Data
@TableName("product_specs")
public class ProductSpec {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private String name;

    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 该规格下的可选值。不是数据库列，组装详情时填进去 */
    @TableField(exist = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ProductSpecValue> values;
}
