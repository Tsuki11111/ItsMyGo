-- 管理员邀请：管理员发起 → 另一位管理员批准 → 被邀请人接受后授予 ADMIN
CREATE TABLE admin_invitations (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    token_hash     CHAR(64)   NOT NULL,
    target_user_id BIGINT     NOT NULL,
    invited_by     BIGINT     NOT NULL,
    approved_by    BIGINT     NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    expires_at     DATETIME   NOT NULL,
    approved_at    DATETIME   NULL,
    accepted_at    DATETIME   NULL,
    created_at     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_admin_invitation_token (token_hash),
    KEY idx_admin_invitation_target (target_user_id, status),
    KEY idx_admin_invitation_status (status)
);
