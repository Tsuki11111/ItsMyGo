package com.tsuk1.itsmygo.common.security;

/**
 * 令牌签发端口（shared kernel）。
 *
 * <p>service 层只依赖本接口，不反向依赖 security 包的具体实现。</p>
 */
public interface TokenIssuer {

    String issue(Long userId, String username, String role);
}
