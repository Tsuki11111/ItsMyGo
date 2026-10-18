package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("users")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String phone;

    /** 头像 URL，空串表示没设置 */
    private String avatar;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    /** CUSTOMER 顾客 / ADMIN 管理员 */
    private String role;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
