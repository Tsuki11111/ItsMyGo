package com.tsuk1.itsmygo.common.logging;

import com.tsuk1.itsmygo.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 为每个 HTTP 请求生成 / 透传 traceId，并把 userId 一起放进 MDC。
 *
 * <p>安全过滤器链先执行（order -100），所以这里能读到 SecurityContext；
 * traceId 同时回写响应头，便于前端 / 网关把一次请求串起来。</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String TRACE_HEADER = "X-Trace-Id";

    private static final Pattern VALID_TRACE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || !VALID_TRACE.matcher(traceId).matches()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);
        putUserId();

        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("HTTP {} {} -> {} ({}ms)",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), System.currentTimeMillis() - start);
            MDC.remove(TRACE_ID);
            MDC.remove(USER_ID);
        }
    }

    private void putUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        if (authentication.getPrincipal() instanceof CurrentUser user && user.id() != null) {
            MDC.put(USER_ID, String.valueOf(user.id()));
        }
    }
}
