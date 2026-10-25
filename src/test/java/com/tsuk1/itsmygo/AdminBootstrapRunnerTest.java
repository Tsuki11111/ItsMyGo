package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.admin.AdminBootstrapRunner;
import com.tsuk1.itsmygo.common.admin.AdminGovernanceProperties;
import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** bootstrap 只负责首次引导；已有管理员或用户名被占用时绝不越权创建。 */
class AdminBootstrapRunnerTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void createsAdminOnlyWhenNoActiveAdminExists() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("Admin12345")).thenReturn("encoded");

        new AdminBootstrapRunner(
                new AdminGovernanceProperties("rootadmin", "Admin12345", 24),
                userMapper, passwordEncoder).run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User created = captor.getValue();
        assertThat(created.getUsername()).isEqualTo("rootadmin");
        assertThat(created.getRole()).isEqualTo("ADMIN");
        assertThat(created.getStatus()).isEqualTo(1);
        assertThat(created.getPassword()).isEqualTo("encoded");
    }

    @Test
    void skipsWhenActiveAdminAlreadyExists() {
        when(userMapper.selectCount(any())).thenReturn(1L);

        new AdminBootstrapRunner(
                new AdminGovernanceProperties("rootadmin", "Admin12345", 24),
                userMapper, passwordEncoder).run(null);

        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void neverPromotesAnExistingNonAdminAccount() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        User existing = new User();
        existing.setId(9L);
        existing.setUsername("rootadmin");
        existing.setRole("CUSTOMER");
        when(userMapper.selectOne(any())).thenReturn(existing);

        new AdminBootstrapRunner(
                new AdminGovernanceProperties("rootadmin", "Admin12345", 24),
                userMapper, passwordEncoder).run(null);

        verify(userMapper, never()).insert(any(User.class));
        assertThat(existing.getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void rejectsWeakBootstrapPassword() {
        new AdminBootstrapRunner(
                new AdminGovernanceProperties("rootadmin", "12345678", 24),
                userMapper, passwordEncoder).run(null);

        verify(userMapper, never()).insert(any(User.class));
    }
}
