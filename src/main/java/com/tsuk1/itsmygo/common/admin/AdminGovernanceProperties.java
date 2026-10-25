package com.tsuk1.itsmygo.common.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理员治理：
 * - 首次部署用 bootstrap 账号引导（库中没有活跃管理员时才创建）；
 * - 邀请有效期按小时配置。
 */
@ConfigurationProperties(prefix = "itsmygo.admin")
public record AdminGovernanceProperties(
        String bootstrapUsername,
        String bootstrapPassword,
        int inviteExpiryHours) {

    public AdminGovernanceProperties {
        if (bootstrapUsername == null) {
            bootstrapUsername = "";
        }
        if (bootstrapPassword == null) {
            bootstrapPassword = "";
        }
        if (inviteExpiryHours <= 0) {
            inviteExpiryHours = 24;
        }
    }

    public boolean bootstrapConfigured() {
        return !bootstrapUsername.isBlank() && !bootstrapPassword.isBlank();
    }
}
