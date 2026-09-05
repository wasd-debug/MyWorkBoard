package com.salarytracker.audit;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/audit", "/api/audit"})
public class AuditController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserResolver currentUser;

    public AuditController(JdbcTemplate jdbcTemplate, CurrentUserResolver currentUser) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('audit:read') or hasRole('ADMIN')")
    public ApiResponse<List<Map<String, Object>>> logs(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return ApiResponse.ok(jdbcTemplate.queryForList("SELECT id, module, action, target_type, target_id, detail_json, ip, cost_ms, created_at FROM audit_log WHERE user_id = ? ORDER BY id DESC LIMIT ?", currentUser.id(), safeLimit));
    }
}
