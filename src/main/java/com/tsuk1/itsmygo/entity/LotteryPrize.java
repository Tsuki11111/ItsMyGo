package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抽奖奖项。
 *
 * <p>{@code stock} 是**初始库存**；实时剩余量以 Redis 奖池 Set 的 {@code SCARD} 为准。
 * 重建奖池时用「初始库存 - 该奖项的 WIN 记录数」计算应补的奖券数量。</p>
 */
@Data
@TableName("lottery_prizes")
public class LotteryPrize {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String name;

    /** 奖项等级，仅用于展示（1 等奖 / 2 等奖…） */
    private Integer level;

    /** 初始库存，必须大于 0 */
    private Integer stock;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
