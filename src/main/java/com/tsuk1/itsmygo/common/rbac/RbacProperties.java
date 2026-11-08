package com.tsuk1.itsmygo.common.rbac;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 权限开关：关闭时管理员视为拥有全部权限码（兼容现状）。 */
@ConfigurationProperties(prefix = "itsmygo.rbac")
public class RbacProperties {

    /** itsmygo.rbac.enabled，默认 false（灰度开关） */
    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
