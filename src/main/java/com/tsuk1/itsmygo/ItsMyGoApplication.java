package com.tsuk1.itsmygo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tsuk1.itsmygo.mapper")
public class ItsMyGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItsMyGoApplication.class, args);
    }

}
