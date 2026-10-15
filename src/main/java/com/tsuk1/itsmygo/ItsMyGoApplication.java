package com.tsuk1.itsmygo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.tsuk1.itsmygo.mapper")
@EnableScheduling
public class ItsMyGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItsMyGoApplication.class, args);
    }

}
