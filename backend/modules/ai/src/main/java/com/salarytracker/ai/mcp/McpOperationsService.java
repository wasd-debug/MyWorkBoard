package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

@Service
public class McpOperationsService {
    private static final int REGISTRATIONS_PER_HOUR = 20;
    private static final int ACTIVE_CLIENTS_PER_IP = 100;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserResolver currentUser;
    private final boolean enabled;
    private final boolean writeEnabled;
    private final boolean oauthEnabled;

    public McpOperationsService(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserResolver currentUser,
                                @Value("${app.mcp.enabled:true}") boolean enabled,
                                @Value("${app.mcp.write-enabled:false}") boolean writeEnabled,
                                @Value("${app.mcp.oauth-enabled:false}") boolean oauthEnabled) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.enabled = enabled;
        this.writeEnabled = writeEnabled;
        this.oauthEnabled = oauthEnabled;
    }

    public void requireRegistrationCapacity(RequestContext context) {
        if (context == null || context.ip() == null || context.ip().isBlank()) return;
        Integer recent = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mcp_oauth_client
                WHERE registration_ip=? AND created_at>DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 HOUR)
                """, Integer.class, context.ip());
        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM mcp_oauth_client WHERE registration_ip=? AND revoked_at IS NULL
                """, Integer.class, context.ip());
        if ((recent != null && recent >= REGISTRATIONS_PER_HOUR)
                || (active != null && active >= ACTIVE_CLIENTS_PER_IP)) {
            event(null, null, null, "client.register", "RATE_LIMITED", context,
                    Map.of("limitPerHour", REGISTRATIONS_PER_HOUR));
            throw new McpOAuthService.OAuthException("rate_limited", "客户端注册过于频繁，请稍后重试", 429);
        }
    }

    public void event(Long userId, String clientId, String tokenId, String eventType, String status,
                      RequestContext context, Map<String, ?> detail) {
        jdbc.update("""
                INSERT INTO mcp_protocol_event(user_id,client_id,token_id,event_type,status,request_ip,user_agent,detail_json)
                VALUES(?,?,?,?,?,?,?,?)
                """, userId, clientId, tokenId, truncate(eventType, 64), truncate(status, 32),
                context == null ? null : truncate(context.ip(), 64),
                context == null ? null : truncate(context.userAgent(), 255), json(detail));
    }

    public Diagnostics diagnostics(String baseUrl, String configuredBaseUrl, String observedBaseUrl) {
        long userId = currentUser.id();
        int activePat = count("""
                SELECT COUNT(*) FROM mcp_personal_token WHERE user_id=? AND token_type='PAT'
                  AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP(6))
                """, userId);
        int activeGrants = count("SELECT COUNT(*) FROM mcp_grant WHERE user_id=? AND revoked_at IS NULL", userId);
        int calls24h = count("""
                SELECT COUNT(*) FROM mcp_tool_call WHERE user_id=?
                  AND created_at>DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)
                """, userId);
        int failures24h = count("""
                SELECT COUNT(*) FROM mcp_protocol_event WHERE user_id=? AND status NOT IN ('SUCCESS','ACCEPTED')
                  AND created_at>DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)
                """, userId);
        List<String> warnings = new ArrayList<>();
        if (configuredBaseUrl == null || configuredBaseUrl.isBlank()) {
            warnings.add("未配置 APP_PUBLIC_BASE_URL，当前按请求头推导公开地址");
        } else if (!configuredBaseUrl.equals(observedBaseUrl)) {
            warnings.add("APP_PUBLIC_BASE_URL 与当前访问 Origin 不一致，请确认反向代理与客户端使用同一公开地址");
        }
        if (oauthEnabled && baseUrl.startsWith("http://")
                && !baseUrl.contains("localhost") && !baseUrl.contains("127.0.0.1")) {
            warnings.add("远程 OAuth 应使用 HTTPS 公开地址");
        }
        return new Diagnostics(baseUrl + "/mcp", baseUrl + "/.well-known/oauth-protected-resource/mcp",
                baseUrl + "/.well-known/oauth-authorization-server", enabled, writeEnabled, oauthEnabled,
                activePat, activeGrants, calls24h, failures24h,
                List.of("2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25"),
                configuredBaseUrl, observedBaseUrl, List.copyOf(warnings));
    }

    public List<ClientView> clients() {
        return jdbc.query("""
                SELECT c.client_id,c.client_name,c.redirect_uris,c.created_at,c.last_used_at,c.last_user_agent,
                       g.id grant_id,g.scopes,g.book_ids,g.updated_at,g.last_used_at grant_last_used_at,g.revoked_at
                FROM mcp_grant g JOIN mcp_oauth_client c ON c.client_id=g.client_id
                WHERE g.user_id=? ORDER BY COALESCE(g.last_used_at,g.updated_at) DESC
                """, (result, rowNum) -> client(result), currentUser.id());
    }

    @Transactional
    public void disconnectClient(String clientId) {
        long userId = currentUser.id();
        List<String> grantIds = jdbc.query("SELECT id FROM mcp_grant WHERE user_id=? AND client_id=? AND revoked_at IS NULL",
                (result, rowNum) -> result.getString(1), userId, clientId);
        if (grantIds.isEmpty()) throw new IllegalArgumentException("OAuth 客户端不存在或已经断开");
        for (String grantId : grantIds) {
            jdbc.update("UPDATE mcp_grant SET revoked_at=CURRENT_TIMESTAMP(6) WHERE id=?", grantId);
            jdbc.update("UPDATE mcp_personal_token SET revoked_at=CURRENT_TIMESTAMP(6) WHERE oauth_grant_id=? AND revoked_at IS NULL", grantId);
            jdbc.update("UPDATE mcp_oauth_code SET consumed_at=CURRENT_TIMESTAMP(6) WHERE grant_id=? AND consumed_at IS NULL", grantId);
        }
        event(userId, clientId, null, "client.disconnected", "SUCCESS", null,
                Map.of("revokedGrants", grantIds.size()));
    }

    public EventPage events(int page, int pageSize) {
        return events(page, pageSize, null);
    }

    public EventPage events(int page, int pageSize, String status) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        String filter = "SUCCESS".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status)
                ? " AND status IN ('SUCCESS','ACCEPTED')" : "FAILED".equalsIgnoreCase(status) ? " AND status NOT IN ('SUCCESS','ACCEPTED')" : "";
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM mcp_protocol_event WHERE user_id=?" + filter, Integer.class, currentUser.id());
        return jdbc.query("SELECT event_type,status,client_id,request_ip,user_agent,detail_json,created_at "
                        + "FROM mcp_protocol_event WHERE user_id=?" + filter + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (result, rowNum) -> new EventView(result.getString("event_type"), result.getString("status"),
                result.getString("client_id"), maskIp(result.getString("request_ip")),
                result.getString("user_agent"), readMap(result.getString("detail_json")),
                instant(result, "created_at")), currentUser.id(), safeSize, safePage * safeSize).stream()
                .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(), items ->
                        new EventPage(items, safePage, safeSize, total == null ? 0 : total,
                                Math.max(1, (long) Math.ceil((total == null ? 0 : total) / (double) safeSize)))));
    }

    /** Backward-compatible bounded list for internal callers and older tests. */
    public List<EventView> events(int limit) { return events(0, Math.min(Math.max(limit, 1), 100)).items(); }

    @Scheduled(cron = "0 35 3 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void cleanupExpiredData() {
        jdbc.update("DELETE FROM mcp_oauth_code WHERE expires_at<DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY) OR consumed_at<DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 1 DAY)");
        jdbc.update("""
                UPDATE mcp_personal_token
                SET token_hash=SHA2(CONCAT('expired:',id),256),refresh_token_hash=NULL,token_hint='已清理'
                WHERE token_type='OAUTH' AND refresh_token_hash IS NOT NULL
                  AND ((revoked_at IS NOT NULL AND revoked_at<DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 30 DAY))
                    OR (refresh_expires_at IS NOT NULL AND refresh_expires_at<DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 30 DAY)))
                """);
        jdbc.update("DELETE FROM mcp_protocol_event WHERE created_at<DATE_SUB(CURRENT_TIMESTAMP(6), INTERVAL 90 DAY)");
    }

    private ClientView client(ResultSet result) throws SQLException {
        return new ClientView(result.getString("client_id"), result.getString("client_name"),
                readSet(result.getString("redirect_uris")), result.getString("grant_id"),
                readSet(result.getString("scopes")), readSet(result.getString("book_ids")),
                instant(result, "created_at"), instant(result, "updated_at"),
                instant(result, "grant_last_used_at"), result.getString("last_user_agent"),
                instant(result, "revoked_at"));
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private String json(Map<String, ?> value) {
        if (value == null || value.isEmpty()) return null;
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { return "{}"; }
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return mapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception exception) { return Map.of("status", "unreadable"); }
    }

    private Set<String> readSet(String value) {
        if (value == null || value.isBlank()) return Set.of();
        try { return Set.copyOf(mapper.readValue(value, new TypeReference<List<String>>() { })); }
        catch (Exception exception) { return Set.of(); }
    }

    private String maskIp(String value) {
        if (value == null || value.isBlank()) return null;
        int separator = value.lastIndexOf('.');
        return separator > 0 ? value.substring(0, separator + 1) + "*" : value.length() > 8 ? value.substring(0, 8) + "…" : value;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record RequestContext(String ip, String userAgent) { }
    public record Diagnostics(String endpoint, String protectedResourceMetadata,
                              String authorizationServerMetadata, boolean enabled, boolean writeEnabled,
                              boolean oauthEnabled, int activePatCount, int activeGrantCount,
                              int callsLast24Hours, int failuresLast24Hours, List<String> supportedProtocolVersions,
                              String configuredBaseUrl, String observedBaseUrl, List<String> warnings) { }
    public record ClientView(String clientId, String clientName, Set<String> redirectUris, String grantId,
                             Set<String> scopes, Set<String> bookIds, Instant registeredAt, Instant authorizedAt,
                             Instant lastUsedAt, String lastUserAgent, Instant revokedAt) { }
    public record EventView(String eventType, String status, String clientId, String maskedIp,
                            String userAgent, Map<String, Object> detail, Instant createdAt) { }
    public record EventPage(List<EventView> items, int page, int pageSize, long total, long totalPages) { }
}
