package com.interview.agent.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 面试智能助手 - 启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.interview.agent")
@MapperScan(basePackages = "com.interview.agent.dao.mapper")
@EnableAsync
public class InterviewAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewAgentApplication.class, args);
    }
}
