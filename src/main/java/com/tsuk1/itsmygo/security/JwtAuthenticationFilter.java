package com.tsuk1.itsmygo.security;

import io.jsonwebtoken.JwtException;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.service.RbacService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 Authorization: Bearer &lt;token&gt; 解析身份并放进 SecurityContext。
 * 解析失败不在这里抛错，交给 SecurityConfig 的 entry point 统一返回 401。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final AuthIdentityService authIdentityService;
    private final RbacService rbacService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthIdentityService authIdentityService,
                                   RbacService rbacService) {
        this.jwtService = jwtService;
        this.authIdentityService = authIdentityService;
        this.rbacService = rbacService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            try {
                // JWT 只负责证明「你是谁」；角色/店铺状态以数据库快照为准
                CurrentUser parsed = jwtService.parse(header.substring(PREFIX.length()));
                CurrentUser user = authIdentityService.load(parsed.id());
                if (user != null) {
                    // Spring Security 的 hasRole("ADMIN") 匹配的是 ROLE_ADMIN 这个 authority；
                    // 管理端再附加权限码（rbac 关闭时 RbacService 返回全部权限码，等价旧行为）
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role()));
                    if (Roles.ADMIN.equals(user.role())) {
                        rbacService.permissionCodes(user.id())
                                .forEach(code -> authorities.add(new SimpleGrantedAuthority(code)));
                    }
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    SecurityContextHolder.clearContext();
                }
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
