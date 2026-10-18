package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户足迹：同一商品只留一行，重复浏览只更新 viewed_at。
 * 每个用户只保留最近 100 条（见 UserFootprintMapper.trimToLatest）。
 */
@Data
@TableName("user_footprints")
public class UserFootprint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private LocalDateTime viewedAt;

    private LocalDateTime createdAt;
}
