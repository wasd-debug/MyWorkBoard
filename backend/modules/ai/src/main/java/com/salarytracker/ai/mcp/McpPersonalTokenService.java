package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.AuthService;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.UnauthorizedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class McpPersonalTokenService {
    public static final String LEDGER_READ = "mcp:ledger:read";
    public static final String WORKTIME_READ = "mcp:worktime:read";
    public static final String LEDGER_PREPARE = "mcp:ledger:prepare";
    public static final String WORKTIME_PREPARE = "mcp:worktime:prepare";
    public static final String LEDGER_COMMIT = "mcp:ledger:commit";
    public static final String WORKTIME_COMMIT = "mcp:worktime:commit";
    private static final Set<String> ALLOWED_SCOPES = Set.of(
            LEDGER_READ, WORKTIME_READ, LEDGER_PREPARE, WORKTIME_PREPARE, LEDGER_COMMIT, WORKTIME_COMMIT);
    private static final Set<String> TEMPLATES = Set.of("READ_ONLY", "PREPARE", "COMMIT", "FULL_WORKSPACE");
    private static final Set<String> HIGH_RISK_POLICIES = Set.of("APPROVAL_ONLY", "DISABLED");

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserResolver currentUser;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();

    public McpPersonalTokenService(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserResolver currentUser,
                                   AuthService authService) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.authService = authService;
    }

    public TokenPage list(int page, int pageSize) {
        return list(page, pageSize, "ALL");
    }

    public TokenPage list(int page, int pageSize, String status) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, pageSize), 100);
        String filter = "REVOKED".equalsIgnoreCase(status) ? " AND revoked_at IS NOT NULL" : "ACTIVE".equalsIgnoreCase(status) ? " AND revoked_at IS NULL" : "";
        long userId = currentUser.id();
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM mcp_personal_token WHERE user_id=? AND token_type='PAT'" + filter, Integer.class, userId);
        List<TokenView> items = jdbc.query("SELECT * FROM mcp_personal_token WHERE user_id=? AND token_type='PAT'" + filter + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (result, rowNum) -> map(result), userId, safeSize, safePage * safeSize);
        return new TokenPage(items, safePage, safeSize, total == null ? 0 : total,
                Math.max(1, (long) Math.ceil((total == null ? 0 : total) / (double) safeSize)));
    }

    /** Backward-compatible full-page access for internal callers and older tests. */
    public List<TokenView> list() { return list(0, 100).items(); }

    @Transactional
    public CreatedToken create(CreateToken command) {
        long userId = currentUser.id();
        String name = cleanName(command == null ? null : command.name());
        String requestedTemplate = command == null ? null : command.permissionTemplate();
        Set<String> scopes = requestedTemplate == null || requestedTemplate.isBlank()
                ? normalizeScopes(command == null ? null : command.scopes())
                : normalizeScopes(normalizeTemplate(requestedTemplate), command.scopes());
        String template = requestedTemplate == null || requestedTemplate.isBlank()
                ? inferTemplate(scopes) : normalizeTemplate(requestedTemplate);
        Set<String> bookIds = normalizeBookIds(command == null ? null : command.bookIds(), userId, scopes);
        Instant expiresAt = normalizeExpiry(command == null ? null : command.expiresAt());
        String highRiskPolicy = normalizeHighRiskPolicy(command == null ? null : command.highRiskPolicy());
        int rateLimit = normalizeRateLimit(command == null ? null : command.rateLimitPerMinute());
        String raw = "wbt_" + randomToken();
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO mcp_personal_token(id,user_id,name,token_hash,token_hint,scopes,permission_template,
                    high_risk_policy,rate_limit_per_minute,book_ids,expires_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, id, userId, name, hash(raw), hint(raw), json(scopes),
                template, highRiskPolicy, rateLimit, bookIds.isEmpty() ? null : json(bookIds),
                expiresAt == null ? null : Timestamp.from(expiresAt));
        TokenView view = jdbc.query("SELECT * FROM mcp_personal_token WHERE id=? AND user_id=?",
                (result, rowNum) -> map(result), id, userId).get(0);
        return new CreatedToken(view, raw);
    }

    public void revoke(String id) {
        if (jdbc.update("UPDATE mcp_personal_token SET revoked_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND token_type='PAT' AND revoked_at IS NULL",
                id, currentUser.id()) != 1) {
            throw new IllegalArgumentException("Token 不存在或已经撤销");
        }
    }

    @Transactional
    public AuthenticatedToken authenticate(String raw) {
        return authenticate(raw, true);
    }

    public AuthenticatedToken authenticate(String raw, boolean oauthEnabled) {
        return authenticate(raw, oauthEnabled, null, null);
    }

    public AuthenticatedToken authenticate(String raw, boolean oauthEnabled, String requestIp, String userAgent) {
        if (raw == null || raw.isBlank()) throw new UnauthorizedException("MCP Token 缺失");
        List<TokenRow> rows = jdbc.query("""
                SELECT * FROM mcp_personal_token
                WHERE token_hash=? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)
                """, (result, rowNum) -> row(result), hash(raw.trim()));
        if (rows.isEmpty()) throw new UnauthorizedException("MCP Token 无效、已过期或已撤销");
        TokenRow row = rows.get(0);
        if ("OAUTH".equals(row.tokenType()) && !oauthEnabled) {
            throw new UnauthorizedException("MCP OAuth 当前未启用");
        }
        CurrentUser user = authService.loadUser(row.userId());
        if (user == null) throw new UnauthorizedException("Token 所属用户不可用");
        jdbc.update("UPDATE mcp_personal_token SET last_used_at=CURRENT_TIMESTAMP WHERE id=?", row.id());
        if ("OAUTH".equals(row.tokenType())) {
            jdbc.update("""
                    UPDATE mcp_oauth_client SET last_used_at=CURRENT_TIMESTAMP(6),last_ip=?,last_user_agent=?
                    WHERE client_id=?
                    """, truncate(requestIp, 64), truncate(userAgent, 255), row.oauthClientId());
            jdbc.update("UPDATE mcp_grant SET last_used_at=CURRENT_TIMESTAMP(6) WHERE id=?", row.oauthGrantId());
        }
        return new AuthenticatedToken(row.id(), row.name(), user, row.scopes(), row.bookIds(), row.tokenType(),
                row.permissionTemplate(), row.highRiskPolicy(), row.rateLimitPerMinute(),
                row.oauthClientId(), row.oauthGrantId());
    }

    public void requireBook(AuthenticatedToken token, String bookId) {
        if (token == null || token.scopes().stream().noneMatch(scope ->
                LEDGER_READ.equals(scope) || LEDGER_PREPARE.equals(scope) || LEDGER_COMMIT.equals(scope))) {
            throw new SecurityException("缺少账本 read、prepare 或 commit scope");
        }
        if (bookId == null || bookId.isBlank()) return;
        if (!token.bookIds().isEmpty() && !token.bookIds().contains(bookId)) {
            throw new SecurityException("Token 无权访问该账本");
        }
    }

    public void audit(AuthenticatedToken token, String toolName, String status, long durationMs,
                      String parameterSummary, String resultSummary, String errorCode) {
        jdbc.update("""
                INSERT INTO mcp_tool_call(token_id,user_id,auth_type,oauth_client_id,tool_name,status,duration_ms,
                    parameter_summary,result_summary,error_code)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, token.id(), token.user().id(), token.tokenType(), token.oauthClientId(), toolName, status, durationMs,
                parameterSummary, truncate(resultSummary, 500), errorCode);
    }

    Set<String> normalizeScopes(String template, Set<String> requested) {
        if ("READ_ONLY".equals(template)) return Set.of(LEDGER_READ, WORKTIME_READ);
        if ("PREPARE".equals(template)) return Set.of(LEDGER_READ, WORKTIME_READ, LEDGER_PREPARE, WORKTIME_PREPARE);
        if ("COMMIT".equals(template) || "FULL_WORKSPACE".equals(template)) return Set.of(
                LEDGER_READ, WORKTIME_READ, LEDGER_PREPARE, WORKTIME_PREPARE, LEDGER_COMMIT, WORKTIME_COMMIT);
        return normalizeScopes(requested);
    }

    Set<String> normalizeScopes(Set<String> requested) {
        Set<String> scopes = requested == null ? Set.of() : new LinkedHashSet<>(requested);
        if (scopes.isEmpty()) throw new IllegalArgumentException("至少选择一个 MCP scope");
        if (!ALLOWED_SCOPES.containsAll(scopes)) throw new IllegalArgumentException("包含不支持的 MCP scope");
        if (scopes.contains(LEDGER_COMMIT) && !scopes.contains(LEDGER_PREPARE)) {
            throw new IllegalArgumentException("账本 commit scope 必须与账本 prepare scope 同时授予");
        }
        if (scopes.contains(WORKTIME_COMMIT) && !scopes.contains(WORKTIME_PREPARE)) {
            throw new IllegalArgumentException("工时 commit scope 必须与工时 prepare scope 同时授予");
        }
        return Set.copyOf(scopes);
    }

    private String normalizeTemplate(String value) {
        String normalized = value == null || value.isBlank() ? "READ_ONLY" : value.trim().toUpperCase();
        if (!TEMPLATES.contains(normalized)) throw new IllegalArgumentException("不支持的 MCP 权限模板");
        return normalized;
    }

    private String inferTemplate(Set<String> scopes) {
        if (scopes.contains(LEDGER_COMMIT) || scopes.contains(WORKTIME_COMMIT)) return "COMMIT";
        if (scopes.contains(LEDGER_PREPARE) || scopes.contains(WORKTIME_PREPARE)) return "PREPARE";
        return "READ_ONLY";
    }

    private String normalizeHighRiskPolicy(String value) {
        String normalized = value == null || value.isBlank() ? "APPROVAL_ONLY" : value.trim().toUpperCase();
        if (!HIGH_RISK_POLICIES.contains(normalized)) throw new IllegalArgumentException("不支持的高风险策略");
        return normalized;
    }

    private int normalizeRateLimit(Integer value) {
        int normalized = value == null ? 120 : value;
        if (normalized < 1 || normalized > 600) throw new IllegalArgumentException("Token 限流必须在 1-600 次/分钟");
        return normalized;
    }

    Set<String> normalizeBookIds(Set<String> requested, long userId, Set<String> scopes) {
        if (!scopes.contains(LEDGER_READ) && !scopes.contains(LEDGER_PREPARE)
                && !scopes.contains(LEDGER_COMMIT)) return Set.of();
        Set<String> ids = requested == null ? Set.of() : new LinkedHashSet<>(requested);
        if (ids.isEmpty()) return Set.of();
        for (String id : ids) {
            Integer count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM ledger_book b JOIN ledger_book_member m ON m.book_id=b.id
                    WHERE b.public_id=? AND b.deleted=FALSE AND m.user_id=? AND m.deleted=FALSE
                    """, Integer.class, id, userId);
            if (count == null || count == 0) throw new IllegalArgumentException("包含无权访问的账本");
        }
        return Set.copyOf(ids);
    }

    private Instant normalizeExpiry(Instant value) {
        Instant now = Instant.now();
        Instant expiresAt = value == null ? now.plus(90, ChronoUnit.DAYS) : value;
        if (!expiresAt.isAfter(now)) throw new IllegalArgumentException("过期时间必须晚于当前时间");
        if (expiresAt.isAfter(now.plus(366, ChronoUnit.DAYS))) throw new IllegalArgumentException("有效期不能超过 366 天");
        return expiresAt;
    }

    private TokenView map(ResultSet result) throws SQLException {
        TokenRow row = row(result);
        return new TokenView(row.id(), result.getString("name"), result.getString("token_hint"), row.scopes(),
                result.getString("permission_template"), result.getString("high_risk_policy"), result.getInt("rate_limit_per_minute"),
                row.bookIds(), instant(result, "expires_at"), instant(result, "revoked_at"),
                instant(result, "last_used_at"), instant(result, "created_at"));
    }

    private TokenRow row(ResultSet result) throws SQLException {
        return new TokenRow(result.getString("id"), result.getString("name"), result.getLong("user_id"),
                result.getString("token_type"), readSet(result.getString("scopes")),
                result.getString("permission_template"), result.getString("high_risk_policy"), result.getInt("rate_limit_per_minute"),
                readSet(result.getString("book_ids")), result.getString("oauth_client_id"),
                result.getString("oauth_grant_id"));
    }

    private Set<String> readSet(String value) {
        if (value == null || value.isBlank()) return Set.of();
        try { return Set.copyOf(mapper.readValue(value, new TypeReference<List<String>>() { })); }
        catch (Exception exception) { throw new IllegalStateException("MCP Token 权限数据损坏", exception); }
    }

    private String json(Set<String> value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("无法保存 MCP Token", exception); }
    }

    private String hash(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("无法计算 Token 摘要", exception); }
    }

    private String randomToken() {
        byte[] bytes = new byte[36];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hint(String raw) { return raw.substring(0, 8) + "…" + raw.substring(raw.length() - 4); }
    private String cleanName(String value) {
        String result = value == null ? "外部 Agent" : value.trim();
        if (result.isEmpty() || result.length() > 120) throw new IllegalArgumentException("Token 名称长度应为 1-120 个字符");
        return result;
    }
    private String truncate(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record CreateToken(String name, Set<String> scopes, Set<String> bookIds, Instant expiresAt,
                              String permissionTemplate, String highRiskPolicy, Integer rateLimitPerMinute) {
        public CreateToken(String name, Set<String> scopes, Set<String> bookIds, Instant expiresAt) {
            this(name, scopes, bookIds, expiresAt, null, null, null);
        }
    }
    public record CreatedToken(TokenView token, String rawToken) { }
    public record TokenView(String id, String name, String tokenHint, Set<String> scopes, String permissionTemplate,
                            String highRiskPolicy, int rateLimitPerMinute, Set<String> bookIds,
                            Instant expiresAt, Instant revokedAt, Instant lastUsedAt, Instant createdAt) { }
    public record TokenPage(List<TokenView> items, int page, int pageSize, long total, long totalPages) { }
    public record AuthenticatedToken(String id, String name, CurrentUser user, Set<String> scopes,
                                     Set<String> bookIds, String tokenType, String permissionTemplate,
                                     String highRiskPolicy, int rateLimitPerMinute, String oauthClientId,
                                     String oauthGrantId) {
        public AuthenticatedToken(String id, String name, CurrentUser user, Set<String> scopes, Set<String> bookIds) {
            this(id, name, user, scopes, bookIds, "PAT", "READ_ONLY", "APPROVAL_ONLY", 120, null, null);
        }
    }
    private record TokenRow(String id, String name, long userId, String tokenType, Set<String> scopes,
                            String permissionTemplate, String highRiskPolicy, int rateLimitPerMinute,
                            Set<String> bookIds, String oauthClientId, String oauthGrantId) { }
}
