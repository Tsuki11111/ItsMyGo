package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 顾客的客服设置：智能推广 / 联网搜索 / 流式输出。
 * 没有行表示没改过设置，读取时走默认值（见 AgentPreferenceService），不落库。
 */
@Data
@TableName("user_agent_preferences")
public class AgentPreference {

    /** 用户 ID 直接当主键：一个用户最多一行 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private Boolean promotionEnabled;

    private Boolean webSearchEnabled;

    private Boolean streamEnabled;

    /** 给后续「智能推广」用：上次推荐日期 */
    private LocalDate lastPromotionDate;

    private LocalDateTime updatedAt;
}
