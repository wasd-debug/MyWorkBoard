package com.salarytracker.platform;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Component
@org.aspectj.lang.annotation.Aspect
public class AuditAspect {
    private final JdbcTemplate jdbcTemplate;

    public AuditAspect(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @org.aspectj.lang.annotation.Around("@annotation(audit)")
    public Object record(org.aspectj.lang.ProceedingJoinPoint joinPoint, Audit audit) throws Throwable {
        long started = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            try {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                Long userId = null;
                if (authentication != null && authentication.getPrincipal() instanceof com.salarytracker.identity.CurrentUser currentUser) {
                    userId = currentUser.id();
                }
                HttpServletRequest request = null;
                if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                    request = attributes.getRequest();
                }
                jdbcTemplate.update("INSERT INTO audit_log (user_id, module, action, target_type, detail_json, ip, user_agent, cost_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        userId, audit.module(), audit.action(), audit.targetType(),
                        "{\"method\":\"" + joinPoint.getSignature().getName() + "\"}",
                        request == null ? "" : request.getRemoteAddr(),
                        request == null ? "" : request.getHeader("User-Agent"),
                        System.currentTimeMillis() - started);
            } catch (Exception ignored) {
            }
        }
    }
}
