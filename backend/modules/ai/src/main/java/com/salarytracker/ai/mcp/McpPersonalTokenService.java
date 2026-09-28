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
    private static final Set<String> ALLOWED_SCOPES = Set.of(
            LEDGER_READ, WORKTIME_READ, LEDGER_PREPARE, WORKTIME_PREPARE);

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

    public List<TokenView> list() {
        return jdbc.query("SELECT * FROM mcp_personal_token WHERE user_id=? ORDER BY created_at DESC",
                (result, rowNum) -> map(result), currentUser.id());
    }

    @Transactional
    public CreatedToken create(CreateToken command) {
        long userId = currentUser.id();
        String name = cleanName(command == null ? null : command.name());
        Set<String> scopes = normalizeScopes(command == null ? null : command.scopes());
        Set<String> bookIds = normalizeBookIds(command == null ? null : command.bookIds(), userId, scopes);
        Instant expiresAt = normalizeExpiry(command == null ? null : command.expiresAt());
        String raw = "wbt_" + randomToken();
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO mcp_personal_token(id,user_id,name,token_hash,token_hint,scopes,book_ids,expires_at)
                VALUES(?,?,?,?,?,?,?,?)
                """, id, userId, name, hash(raw), hint(raw), json(scopes),
                bookIds.isEmpty() ? null : json(bookIds), expiresAt == null ? null : Timestamp.from(expiresAt));
        TokenView view = jdbc.query("SELECT * FROM mcp_personal_token WHERE id=? AND user_id=?",
                (result, rowNum) -> map(result), id, userId).get(0);
        return new CreatedToken(view, raw);
    }

    public void revoke(String id) {
        if (jdbc.update("UPDATE mcp_personal_token SET revoked_at=CURRENT_TIMESTAMP WHERE id=? AND user_id=? AND revoked_at IS NULL",
                id, currentUser.id()) != 1) {
            throw new IllegalArgumentException("Token 不存在或已经撤销");
        }
    }

    @Transactional
    public AuthenticatedToken authenticate(String raw) {
        if (raw == null || raw.isBlank()) throw new UnauthorizedException("MCP Token 缺失");
        List<TokenRow> rows = jdbc.query("""
                SELECT * FROM mcp_personal_token
                WHERE token_hash=? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP)
                """, (result, rowNum) -> row(result), hash(raw.trim()));
        if (rows.isEmpty()) throw new UnauthorizedException("MCP Token 无效、已过期或已撤销");
        TokenRow row = rows.get(0);
        CurrentUser user = authService.loadUser(row.userId());
        if (user == null) throw new UnauthorizedException("Token 所属用户不可用");
        jdbc.update("UPDATE mcp_personal_token SET last_used_at=CURRENT_TIMESTAMP WHERE id=?", row.id());
        return new AuthenticatedToken(row.id(), row.name(), user, row.scopes(), row.bookIds());
    }

    public void requireBook(AuthenticatedToken token, String bookId) {
        if (token == null || token.scopes().stream().noneMatch(scope ->
                LEDGER_READ.equals(scope) || LEDGER_PREPARE.equals(scope))) {
            throw new SecurityException("缺少账本 read 或 prepare scope");
        }
        if (bookId == null || bookId.isBlank()) return;
        if (!token.bookIds().isEmpty() && !token.bookIds().contains(bookId)) {
            throw new SecurityException("Token 无权访问该账本");
        }
    }

    public void audit(AuthenticatedToken token, String toolName, String status, long durationMs,
                      String parameterSummary, String resultSummary, String errorCode) {
        jdbc.update("""
                INSERT INTO mcp_tool_call(token_id,user_id,tool_name,status,duration_ms,parameter_summary,result_summary,error_code)
                VALUES(?,?,?,?,?,?,?,?)
                """, token.id(), token.user().id(), toolName, status, durationMs,
                parameterSummary, truncate(resultSummary, 500), errorCode);
    }

    private Set<String> normalizeScopes(Set<String> requested) {
        Set<String> scopes = requested == null ? Set.of() : new LinkedHashSet<>(requested);
        if (scopes.isEmpty()) throw new IllegalArgumentException("至少选择一个 MCP scope");
        if (!ALLOWED_SCOPES.containsAll(scopes)) throw new IllegalArgumentException("包含不支持的 MCP scope");
        return Set.copyOf(scopes);
    }

    private Set<String> normalizeBookIds(Set<String> requested, long userId, Set<String> scopes) {
        if (!scopes.contains(LEDGER_READ) && !scopes.contains(LEDGER_PREPARE)) return Set.of();
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
                row.bookIds(), instant(result, "expires_at"), instant(result, "revoked_at"),
                instant(result, "last_used_at"), instant(result, "created_at"));
    }

    private TokenRow row(ResultSet result) throws SQLException {
        return new TokenRow(result.getString("id"), result.getString("name"), result.getLong("user_id"), readSet(result.getString("scopes")),
                readSet(result.getString("book_ids")));
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

    public record CreateToken(String name, Set<String> scopes, Set<String> bookIds, Instant expiresAt) { }
    public record CreatedToken(TokenView token, String rawToken) { }
    public record TokenView(String id, String name, String tokenHint, Set<String> scopes, Set<String> bookIds,
                            Instant expiresAt, Instant revokedAt, Instant lastUsedAt, Instant createdAt) { }
    public record AuthenticatedToken(String id, String name, CurrentUser user, Set<String> scopes, Set<String> bookIds) { }
    private record TokenRow(String id, String name, long userId, Set<String> scopes, Set<String> bookIds) { }
}
