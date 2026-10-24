package com.tsuk1.itsmygo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理端操作审计；只追加，不提供修改 / 删除。 */
@Data
@TableName("admin_audit_logs")
public class AdminAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long actorUserId;

    private String actorUsername;

    private String action;

    private String targetType;

    private Long targetId;

    private String summary;

    /** SUCCESS / FAILED */
    private String result;

    private String errorMessage;

    private String ip;

    private String userAgent;

    private String traceId;

    /** method + URI + query，不含请求体 */
    private String detail;

    private LocalDateTime createdAt;
}
