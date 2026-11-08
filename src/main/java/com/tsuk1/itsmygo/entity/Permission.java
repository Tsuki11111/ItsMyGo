package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 权限点（admin:order:ship 等）。 */
@Data
@TableName("permissions")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String module;

    /** API / MENU / BUTTON */
    private String type;

    private String description;

    private LocalDateTime createdAt;
}
