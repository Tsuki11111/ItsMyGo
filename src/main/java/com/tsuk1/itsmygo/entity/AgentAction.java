package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 客服智能体的待确认动作。确认前只落快照，确认后才调业务 Service 执行。 */
@Data
@TableName("agent_actions")
public class AgentAction {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外动作号（UUID），浏览器/agent 只认它 */
    private String actionNo;

    private Long userId;

    private String sessionId;

    /** 关联业务对象：退款动作存订单 id；下单动作为空 */
    private Long targetId;

    /** 见 {@link AgentActionType} */
    private String type;

    /** 见 {@link AgentActionStatus} */
    private String status;

    /** 服务端执行快照 */
    private String payloadJson;

    /** 确认卡片展示快照 */
    private String summaryJson;

    private String resultJson;

    private String errorMessage;

    private LocalDateTime expiresAt;

    private LocalDateTime confirmedAt;

    private LocalDateTime executedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
