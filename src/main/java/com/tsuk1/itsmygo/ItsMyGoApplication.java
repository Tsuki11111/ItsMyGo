package com.tsuk1.itsmygo;

import com.tsuk1.itsmygo.common.agent.AgentActionProperties;
import com.tsuk1.itsmygo.common.agent.AgentProperties;
import com.tsuk1.itsmygo.common.agent.KnowledgeImportProperties;
import com.tsuk1.itsmygo.common.agent.LogisticsProperties;
import com.tsuk1.itsmygo.common.order.OrderTimeoutProperties;
import com.tsuk1.itsmygo.common.ratelimit.RateLimitProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.tsuk1.itsmygo.mapper")
@EnableScheduling
@EnableConfigurationProperties({RateLimitProperties.class, AgentProperties.class,
        AgentActionProperties.class, LogisticsProperties.class,
        KnowledgeImportProperties.class, OrderTimeoutProperties.class})
public class ItsMyGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItsMyGoApplication.class, args);
    }

}
