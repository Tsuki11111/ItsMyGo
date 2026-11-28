package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抽奖实物奖品领取单：一次中奖一条，一奖一单。
 *
 * <p>收货信息是填写时的快照，用户之后改地址不影响已发货的单。</p>
 */
@Data
@TableName("lottery_prize_claims")
public class LotteryPrizeClaim {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long drawRecordId;

    private Long activityId;

    private Long prizeId;

    private Long userId;

    /** 见 {@link LotteryClaimStatus} */
    private String status;

    private String receiverName;

    private String receiverPhone;

    private String receiverAddress;

    private String trackingNo;

    private LocalDateTime shippedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
