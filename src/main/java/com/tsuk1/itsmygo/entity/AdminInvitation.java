package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理员邀请：发起 → 批准 → 接受，三步都由独立字段留痕。 */
@Data
@TableName("admin_invitations")
public class AdminInvitation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 只存 SHA-256，明文 token 只在创建响应里出现一次 */
    private String tokenHash;

    private Long targetUserId;

    private Long invitedBy;

    private Long approvedBy;

    /** 见 {@link AdminInvitationStatus} */
    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime approvedAt;

    private LocalDateTime acceptedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
