package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 平台活动。抽奖只是其中一种类型，后续活动类型共用这张主表。
 *
 * <p>活动自身的配置（名称 / 时间 / 颜色 / 封面）在这里；
 * 类型专属配置（抽奖奖项、抽奖记录）放在各自的子表里，用 activity_id 关联。</p>
 */
@Data
@TableName("activities")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 见 {@link ActivityType}，数据库存枚举名 */
    private String type;

    private String name;

    /** 活动卡片副标题 */
    private String description;

    /** 活动主色调，活动卡片和详情页的强调色 */
    private String primaryColor;

    /** 活动封面图 URL，空串表示无封面 */
    private String coverImage;

    /** 见 {@link ActivityStatus} */
    private Integer status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
