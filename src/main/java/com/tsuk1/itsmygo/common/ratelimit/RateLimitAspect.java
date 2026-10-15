package com.tsuk1.itsmygo.common.ratelimit;

import com.tsuk1.itsmygo.common.ResultCode;
import com.tsuk1.itsmygo.common.exception.BizException;
import com.tsuk1.itsmygo.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitAspect(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        if (!properties.isEnabled()) {
            return joinPoint.proceed();
        }

        String identity = resolveIdentity(rateLimit);
        if (identity == null) {
            // 拿不到身份（既没登录又拿不到 IP）就不限流，别把正常请求误杀
            return joinPoint.proceed();
        }

        String key = rateLimit.scene() + ":" + identity;
        if (!rateLimiter.tryAcquire(key, rateLimit.limit(), rateLimit.windowSeconds())) {
            log.warn("触发限流: {}", key);
            throw new BizException(ResultCode.TOO_MANY_REQUESTS);
        }
        return joinPoint.proceed();
    }

    private String resolveIdentity(RateLimit rateLimit) {
        if (rateLimit.byUser()) {
            CurrentUser user = currentUser();
            if (user != null) {
                return "u" + user.id();
            }
            if (!rateLimit.byIp()) {
                return null;
            }
        }
        if (rateLimit.byIp()) {
            String ip = clientIp();
            return ip == null ? null : "ip" + ip;
        }
        return null;
    }

    private CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUser user) {
            return user;
        }
        return null;
    }

    /**
     * 取来源 IP。
     * **刻意不读 `X-Forwarded-For`** —— 那个头客户端可以随便伪造，信它等于给了攻击者
     * 无限换 IP 绕过限流的机会。本项目是单体直连、前面没有可信代理，用 remoteAddr 就够。
     * 将来真上了 Nginx，要让限流按真实 IP 生效，得改成只信任来自已知代理的 XFF。
     */
    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return request.getRemoteAddr();
        }
        return null;
    }
}
