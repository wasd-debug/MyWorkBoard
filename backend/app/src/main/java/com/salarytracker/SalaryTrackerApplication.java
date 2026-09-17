package com.salarytracker;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * 真实时薪 · 加班追踪 后端入口
 */
@SpringBootApplication
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
@MapperScan("com.salarytracker.mapper")
public class SalaryTrackerApplication {

    @org.springframework.context.annotation.Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @org.springframework.context.annotation.Bean
    LockProvider lockProvider(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .usingDbTime()
                .build());
    }

    public static void main(String[] args) {
        SpringApplication.run(SalaryTrackerApplication.class, args);
    }
}
