package com.salarytracker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 真实时薪 · 加班追踪 后端入口
 */
@SpringBootApplication
@MapperScan("com.salarytracker.mapper")
public class SalaryTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SalaryTrackerApplication.class, args);
    }
}
