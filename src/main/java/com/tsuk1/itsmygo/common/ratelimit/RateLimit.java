package com.tsuk1.itsmygo.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流。标在 Controller 方法上，由 {@link RateLimitAspect} 拦截。
 *
 * <pre>
 * byUser = true  → 按登录用户限流（下单、上传这类要登录的接口）
 * byIp   = true  → 按来源 IP 限流（注册、登录这类匿名的接口）
 * 两者都开 → 按「用户 + IP」组合限流
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 场景名，用于拼 Redis key；同一场景共享配额 */
    String scene();

    /** 窗口内允许的最大请求数 */
    int limit();

    /** 窗口长度（秒） */
    int windowSeconds();

    boolean byUser() default false;

    boolean byIp() default false;
}
