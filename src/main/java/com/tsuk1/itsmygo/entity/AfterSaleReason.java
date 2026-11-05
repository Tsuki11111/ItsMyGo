package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 售后原因字典（平台维护，申请时必选）。 */
@Data
@TableName("after_sale_reasons")
public class AfterSaleReason {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String label;

    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
