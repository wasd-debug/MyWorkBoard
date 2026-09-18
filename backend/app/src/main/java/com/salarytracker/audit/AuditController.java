package com.salarytracker.audit;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "Audit")
public class AuditController {
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserResolver currentUser;

    public AuditController(JdbcTemplate jdbcTemplate, CurrentUserResolver currentUser) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('audit:read') or hasRole('ADMIN')")
    @Operation(operationId = "listAuditLogs")
    public ApiResponse<List<AuditLog>> logs(@RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return ApiResponse.ok(jdbcTemplate.query(
                "SELECT id, module, action, target_type, target_id, detail_json, ip, cost_ms, created_at " +
                        "FROM audit_log WHERE user_id = ? ORDER BY id DESC LIMIT ?",
                (result, rowNum) -> new AuditLog(result.getLong("id"), result.getString("module"),
                        result.getString("action"), result.getString("target_type"), result.getString("target_id"),
                        result.getString("detail_json"), result.getString("ip"), result.getLong("cost_ms"),
                        result.getTimestamp("created_at").toInstant()), currentUser.id(), safeLimit));
    }

    public record AuditLog(long id, String module, String action, String targetType, String targetId,
                           String detailJson, String ip, long costMs, Instant createdAt) {
    }
}
