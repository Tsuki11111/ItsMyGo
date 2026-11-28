package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每次抽奖一条记录，包括「谢谢参与」。
 *
 * <p>{@code uk_activity_user_day (activity_id, user_id, draw_date)} 是「每人每天一次」
 * 的最终兜底：Redis 的资格 Set 只是快速闸门，丢了也能靠这个唯一键拒绝当天重复抽奖。</p>
 */
@Data
@TableName("lottery_draw_records")
public class LotteryDrawRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private Long userId;

    /** 抽奖日期；一天一行，跨天可再抽 */
    private LocalDate drawDate;

    /** 中奖项；谢谢参与时为 null */
    private Long prizeId;

    /** 奖券码；谢谢参与时为 null（唯一键允许多个 NULL） */
    private String prizeCode;

    /** 见 {@link LotteryDrawResult} */
    private String result;

    private LocalDateTime createdAt;
}
