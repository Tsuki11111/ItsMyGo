package com.tsuk1.itsmygo.security;

import com.tsuk1.itsmygo.common.RedisCacheHelper;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.entity.Merchant;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.MerchantMapper;
import com.tsuk1.itsmygo.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 每次请求的**授权快照**：以数据库为准，不信任 JWT 里的 role/merchantId。
 *
 * 这样商家被禁用、用户被禁用、审核通过后，旧 token 不会继续保留权限。
 * 快照缓存 60 秒（带抖动），角色/店铺状态变更时主动 evict。
 */
@Service
public class AuthIdentityService {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final RedisCacheHelper cacheHelper;

    public AuthIdentityService(UserMapper userMapper, MerchantMapper merchantMapper,
                               RedisCacheHelper cacheHelper) {
        this.userMapper = userMapper;
        this.merchantMapper = merchantMapper;
        this.cacheHelper = cacheHelper;
    }

    /** 返回有效身份；用户不存在/被禁用时返回 null */
    public CurrentUser load(Long userId) {
        AuthSnapshot snapshot = cacheHelper.getOrLoad(key(userId), TTL, AuthSnapshot.class,
                () -> loadSnapshot(userId));
        if (snapshot == null || !Integer.valueOf(1).equals(snapshot.status())) {
            return null;
        }

        String role = snapshot.role();
        Long merchantId = snapshot.merchantId();
        if (Roles.MERCHANT.equals(role)) {
            // 店铺非 ACTIVE 时，商家端权限立即降为普通顾客
            if (merchantId == null || !Merchant.ACTIVE.equals(snapshot.merchantStatus())) {
                role = Roles.CUSTOMER;
                merchantId = null;
            }
        } else {
            merchantId = null;
        }
        return new CurrentUser(userId, snapshot.username(), role, merchantId);
    }

    /** 审核/禁用/启用后调用，让下一次请求立刻读到新状态 */
    public void evict(Long userId) {
        if (userId != null) {
            cacheHelper.evict(key(userId));
        }
    }

    private AuthSnapshot loadSnapshot(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        Merchant merchant = user.getMerchantId() == null ? null
                : merchantMapper.selectById(user.getMerchantId());
        return new AuthSnapshot(user.getId(), user.getUsername(), user.getStatus(), user.getRole(),
                user.getMerchantId(),
                merchant == null ? "" : merchant.getName(),
                merchant == null ? "" : merchant.getStatus());
    }

    private String key(Long userId) {
        return RedisCacheHelper.key("auth:user:" + userId);
    }

    /** 缓存快照；字段名与 Jackson 序列化一一对应 */
    public record AuthSnapshot(Long userId, String username, Integer status, String role,
                               Long merchantId, String merchantName, String merchantStatus) {
    }
}
