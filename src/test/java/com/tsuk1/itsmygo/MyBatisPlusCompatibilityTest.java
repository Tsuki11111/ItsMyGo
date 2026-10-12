package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.entity.User;
import com.tsuk1.itsmygo.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0 关键验证：MyBatis-Plus 3.5.17 在 Spring Boot 4.1.1 上能否正常工作。
 * 这条链路同时覆盖 Spring 上下文、Flyway 建表与一次完整 CRUD。
 */
@SpringBootTest
class MyBatisPlusCompatibilityTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    void myBatisPlusCrudWorksOnSpringBoot4() {
        User user = new User();
        user.setUsername("smoke_" + System.currentTimeMillis());
        user.setPassword("x");
        user.setNickname("冒烟测试");
        user.setStatus(1);

        assertThat(userMapper.insert(user)).isEqualTo(1);
        assertThat(user.getId()).isNotNull();

        User loaded = userMapper.selectById(user.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getUsername()).isEqualTo(user.getUsername());
        assertThat(loaded.getNickname()).isEqualTo("冒烟测试");

        assertThat(userMapper.deleteById(user.getId())).isEqualTo(1);
    }
}
