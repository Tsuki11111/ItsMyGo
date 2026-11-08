package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理端角色（v1 固定 4 个：SUPER_ADMIN / OPERATOR / CUSTOMER_SERVICE / FINANCE）。 */
@Data
@TableName("roles")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String description;

    private Integer builtin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
