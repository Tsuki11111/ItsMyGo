package com.tsuk1.itsmygo.common.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tsuk1.itsmygo.common.Roles;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.entity.UserStatus;
import com.tsuk1.itsmygo.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 首次部署引导管理员。
 *
 * 只有在「库中没有活跃 ADMIN」且配置了 BOOTSTRAP_ADMIN_USERNAME / PASSWORD 时才创建；
 * 已有管理员时绝不覆盖或提升现有账号，避免环境变量成为隐式提权入口。
 */
@Slf4j
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Pattern STRONG_PASSWORD =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");

    private final AdminGovernanceProperties properties;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(AdminGovernanceProperties properties, UserMapper userMapper,
                                PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.bootstrapUsername())
                && !StringUtils.hasText(properties.bootstrapPassword())) {
            log.info("未配置 BOOTSTRAP_ADMIN_*；如果库中没有管理员，请配置后重启或手工提权");
            return;
        }
        if (!properties.bootstrapConfigured()) {
            log.warn("BOOTSTRAP_ADMIN_USERNAME / PASSWORD 必须同时配置，已跳过初始化");
            return;
        }

        String username = properties.bootstrapUsername().trim();
        String password = properties.bootstrapPassword();
        if (username.length() < 3 || username.length() > 32) {
            log.warn("BOOTSTRAP_ADMIN_USERNAME 长度需在 3-32 个字符之间，已跳过");
            return;
        }
        if (!STRONG_PASSWORD.matcher(password).matches()) {
            log.warn("BOOTSTRAP_ADMIN_PASSWORD 至少 8 位且必须同时包含字母和数字，已跳过");
            return;
        }

        Long activeAdmins = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getRole, Roles.ADMIN)
                .eq(User::getStatus, UserStatus.ACTIVE));
        if (activeAdmins != null && activeAdmins > 0) {
            log.info("库中已有 {} 个活跃管理员，跳过 bootstrap", activeAdmins);
            return;
        }

        User existing = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
        if (existing != null) {
            log.warn("bootstrap 用户名已存在但不是 ADMIN，拒绝自动提升：{}", username);
            return;
        }

        User admin = new User();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setNickname(username);
        admin.setPhone("");
        admin.setAvatar("");
        admin.setStatus(UserStatus.ACTIVE);
        admin.setRole(Roles.ADMIN);
        try {
            userMapper.insert(admin);
            log.info("已创建 bootstrap 管理员：{}，请登录后立即修改初始密码", username);
        } catch (DuplicateKeyException e) {
            log.warn("bootstrap 管理员创建失败：用户名已被占用 {}", username);
        }
    }
}
