package com.tsuk1.itsmygo.common.audit;

import com.tsuk1.itsmygo.common.Result;
import com.tsuk1.itsmygo.common.logging.RequestIdFilter;
import com.tsuk1.itsmygo.entity.AdminAuditLog;
import com.tsuk1.itsmygo.security.CurrentUser;
import com.tsuk1.itsmygo.service.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 管理端写操作审计。
 *
 * <p>在 Controller 外层记录，业务事务已结束：成功、业务失败、抛异常都能落一条；
 * 审计写入失败只打运行日志，不影响业务结果。GET 不审计。</p>
 */
@Slf4j
@Aspect
@Component
@Order(0)
public class AdminAuditAspect {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("/(\\d+)(?=/|$)");

    private final AdminAuditService auditService;

    public AdminAuditAspect(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @Around("execution(public * com.tsuk1.itsmygo.controller.Admin*Controller.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();
        String httpMethod = request.getMethod();
        if (READ_METHODS.contains(httpMethod)) {
            return joinPoint.proceed();
        }

        AdminAudit audit = resolveAudit(joinPoint);
        String uri = request.getRequestURI();
        String action = audit == null
                ? httpMethod + " " + normalizeUri(uri)
                : audit.action();
        String summary = audit == null
                ? joinPoint.getSignature().getName()
                : audit.label();
        String targetType = audit == null
                ? fallbackTargetType(uri)
                : audit.targetType();

        long start = System.currentTimeMillis();
        Object result = null;
        Throwable failure = null;
        try {
            result = joinPoint.proceed();
            if (result instanceof Result<?> response && response.getCode() != 200) {
                failure = new IllegalStateException(response.getMessage());
            }
            return result;
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            record(joinPoint, request, action, summary, targetType, result, failure,
                    System.currentTimeMillis() - start);
        }
    }

    private void record(ProceedingJoinPoint joinPoint, HttpServletRequest request,
                        String action, String summary, String targetType, Object result,
                        Throwable failure, long elapsedMs) {
        try {
            AdminAuditLog audit = new AdminAuditLog();
            CurrentUser actor = currentUser();
            audit.setActorUserId(actor == null ? 0L : actor.id());
            audit.setActorUsername(actor == null ? "" : actor.username());
            audit.setAction(truncate(action, 64));
            audit.setTargetType(truncate(targetType, 32));
            audit.setTargetId(targetId(request));
            audit.setSummary(truncate(summary, 255));
            audit.setResult(failure == null ? "SUCCESS" : "FAILED");
            audit.setErrorMessage(failure == null ? "" : truncate(message(failure), 512));
            audit.setIp(truncate(request.getRemoteAddr(), 64));
            audit.setUserAgent(truncate(request.getHeader("User-Agent"), 512));
            audit.setTraceId(truncate(MDC.get(RequestIdFilter.TRACE_ID), 64));
            String query = request.getQueryString();
            String detail = joinPoint.getSignature().toShortString() + " | " + request.getMethod()
                    + " " + request.getRequestURI() + (query == null ? "" : "?" + query)
                    + " | " + elapsedMs + "ms";
            audit.setDetail(truncate(detail, 2000));
            audit.setCreatedAt(LocalDateTime.now());
            auditService.record(audit);
        } catch (Exception e) {
            log.error("写入管理端审计失败: action={} uri={}",
                    action, request.getRequestURI(), e);
        }
    }

    private AdminAudit resolveAudit(ProceedingJoinPoint joinPoint) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Method specific = AopUtils.getMostSpecificMethod(method, joinPoint.getTarget().getClass());
        AdminAudit audit = specific.getAnnotation(AdminAudit.class);
        return audit == null ? method.getAnnotation(AdminAudit.class) : audit;
    }

    private CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof CurrentUser user ? user : null;
    }

    private Long targetId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        var matcher = NUMERIC_SEGMENT.matcher(uri);
        Long id = null;
        while (matcher.find()) {
            try {
                id = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                // 超长数字不是合法目标 ID，忽略
            }
        }
        return id;
    }

    private String normalizeUri(String uri) {
        return NUMERIC_SEGMENT.matcher(uri).replaceAll("/{id}");
    }

    private String fallbackTargetType(String uri) {
        String prefix = "/api/admin/";
        if (!uri.startsWith(prefix)) {
            return "";
        }
        String rest = uri.substring(prefix.length());
        int slash = rest.indexOf('/');
        String segment = slash < 0 ? rest : rest.substring(0, slash);
        return segment.isEmpty() ? "" : segment.toUpperCase().replace('-', '_');
    }

    private String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
