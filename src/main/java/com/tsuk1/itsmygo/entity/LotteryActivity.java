package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 抽奖活动。Redis 只缓存运行时状态，活动与奖项配置以 MySQL 为准 */
@Data
@TableName("lottery_activities")
public class LotteryActivity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 见 {@link LotteryActivityStatus} */
    private Integer status;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
