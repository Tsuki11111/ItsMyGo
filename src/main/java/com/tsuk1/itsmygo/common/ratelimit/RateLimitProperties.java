package com.tsuk1.itsmygo.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流总开关。
 *
 * 测试环境默认关掉（见 src/test/resources/application.yml）——
 * 限流计数是**全局共享状态**，测试用例之间会互相干扰：
 * 所有测试都来自同一个 IP，注册配额 5 次/分钟的话，跑几个用例就撞墙了。
 * 只有 `RateLimitTest` 用 @TestPropertySource 把它单独打开来验证限流本身。
 */
@ConfigurationProperties(prefix = "itsmygo.ratelimit")
public class RateLimitProperties {

    /** 关掉后切面直接放行，不做任何检查 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
