package com.apigentest;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * APIGenTest 后端启动类
 */
@SpringBootApplication
@MapperScan("com.apigentest.mapper")
@EnableScheduling
public class ApiGenTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGenTestApplication.class, args);
    }
}