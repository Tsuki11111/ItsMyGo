package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.agent.AgentProperties;
import com.tsuk1.itsmygo.common.ratelimit.RateLimitProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.tsuk1.itsmygo.mapper")
@EnableScheduling
@EnableConfigurationProperties({RateLimitProperties.class, AgentProperties.class})
public class ItsMyGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItsMyGoApplication.class, args);
    }

}
