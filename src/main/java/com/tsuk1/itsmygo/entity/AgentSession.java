package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Java 侧的客服智能体会话归属，防止跨用户读历史。 */
@Data
@TableName("agent_sessions")
public class AgentSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String sessionId;

    private LocalDateTime createdAt;

    private LocalDateTime lastActiveAt;
}
