package com.salarytracker.identity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.salarytracker.platform.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshTtlSeconds;
    private final SecureRandom secureRandom = new SecureRandom();
    /**
     * 每个带 Bearer 的请求都要取一次身份（三张表 JOIN）。缓存 TTL 就是「改角色/禁用账号后
     * 最晚多久生效」的上限，默认 30 秒。缓存值为 Optional 是为了把「用户不存在/已禁用」也缓存住，
     * 避免无效令牌反复打库。登录、注册、刷新这些低频且要求读到最新状态的路径走 loadUserFresh。
     */
    private final Cache<Long, Optional<CurrentUser>> userCache;

    public AuthService(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder, JwtService jwtService,
                       @Value("${app.jwt.refresh-ttl:2592000}") long refreshTtlSeconds,
                       @Value("${app.auth.user-cache-ttl-seconds:30}") long userCacheTtlSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTtlSeconds = refreshTtlSeconds;
        this.userCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofSeconds(Math.max(1, userCacheTtlSeconds)))
                .build();
    }

    @Transactional
    public AuthTokens register(String username, String password, String nickname, String email) {
        validateCredentials(username, password);
        try {
            jdbcTemplate.update("INSERT INTO app_user (username, password_hash, nickname, email) VALUES (?, ?, ?, ?)",
                    username.trim(), passwordEncoder.encode(password), nickname == null ? "" : nickname.trim(),
                    email == null || email.isBlank() ? null : email.trim());
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名或邮箱已存在");
        }
        long userId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, username.trim());
        jdbcTemplate.update("INSERT INTO user_role (user_id, role_id) SELECT ?, id FROM role WHERE code = 'USER'", userId);
        jdbcTemplate.update("INSERT INTO work_setting (user_id) VALUES (?)", userId);
        return issue(userId, username.trim(), nickname == null ? "" : nickname.trim(), "register");
    }

    public AuthTokens login(String username, String password, String device) {
        if (username == null || password == null || username.isBlank()) throw new UnauthorizedException("用户名或密码错误");
        LoginRow row;
        try {
            row = jdbcTemplate.queryForObject("SELECT id, username, nickname, password_hash, status FROM app_user WHERE username = ?",
                    (result, rowNum) -> new LoginRow(result.getLong("id"), result.getString("username"),
                            result.getString("nickname"), result.getString("password_hash"), result.getString("status")),
                    username.trim());
        } catch (Exception exception) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(row.status()) || !passwordEncoder.matches(password, row.passwordHash())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        return issue(row.id(), row.username(), row.nickname(), device);
    }

    @Transactional
    public AuthTokens refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new UnauthorizedException("刷新令牌缺失");
        String hash = hash(rawToken);
        List<UserRow> rows = jdbcTemplate.query("SELECT rt.user_id, u.username, u.nickname FROM refresh_token rt JOIN app_user u ON u.id = rt.user_id WHERE rt.token_hash = ? AND rt.revoked_at IS NULL AND rt.expires_at > CURRENT_TIMESTAMP",
                (result, rowNum) -> new UserRow(result.getLong("user_id"), result.getString("username"),
                        result.getString("nickname")), hash);
        if (rows.isEmpty()) throw new UnauthorizedException("刷新令牌无效或已过期");
        UserRow row = rows.get(0);
        jdbcTemplate.update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ?", hash);
        return issue(row.id(), row.username(), row.nickname(), "refresh");
    }

    public void logout(String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            jdbcTemplate.update("UPDATE refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ?", hash(rawToken));
        }
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            throw new IllegalArgumentException("新密码长度需为 8-128 位");
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof CurrentUser user)) throw new UnauthorizedException("请先登录");
        String hash = jdbcTemplate.queryForObject("SELECT password_hash FROM app_user WHERE id = ?", String.class, user.id());
        if (!passwordEncoder.matches(currentPassword, hash)) throw new UnauthorizedException("当前密码错误");
        jdbcTemplate.update("UPDATE app_user SET password_hash = ? WHERE id = ?", passwordEncoder.encode(newPassword), user.id());
    }

    /** 请求链路用：命中缓存时不回库。 */
    public CurrentUser loadUser(long id) {
        return userCache.get(id, this::queryUser).orElse(null);
    }

    /** 登录/注册/刷新用：必须读到提交后的真实状态，不写缓存也不读缓存。 */
    public CurrentUser loadUserFresh(long id) {
        return queryUser(id).orElse(null);
    }

    /** 管理侧改动角色或禁用账号后可直接调用，让变更立即生效而不必等 TTL 到期。 */
    public void invalidateUser(long id) {
        userCache.invalidate(id);
    }

    private Optional<CurrentUser> queryUser(long id) {
        List<AuthorityRow> rows = jdbcTemplate.query("SELECT u.id, u.username, u.nickname, r.code role_code, p.code permission_code FROM app_user u JOIN user_role ur ON ur.user_id = u.id JOIN role r ON r.id = ur.role_id LEFT JOIN role_permission rp ON rp.role_id = r.id LEFT JOIN permission p ON p.id = rp.permission_id WHERE u.id = ? AND u.status = 'ACTIVE'",
                (result, rowNum) -> new AuthorityRow(result.getLong("id"), result.getString("username"),
                        result.getString("nickname"), result.getString("role_code"),
                        result.getString("permission_code")), id);
        if (rows.isEmpty()) return Optional.empty();
        AuthorityRow first = rows.get(0);
        Set<String> authorities = new HashSet<>();
        for (AuthorityRow row : rows) {
            if (row.roleCode() != null) authorities.add("ROLE_" + row.roleCode());
            if (row.permissionCode() != null) authorities.add(row.permissionCode());
        }
        return Optional.of(new CurrentUser(id, first.username(), first.nickname(), Set.copyOf(authorities)));
    }

    private AuthTokens issue(long userId, String username, String nickname, String device) {
        CurrentUser user = loadUserFresh(userId);
        if (user == null) throw new UnauthorizedException("用户不可用");
        String refresh = randomToken();
        jdbcTemplate.update("INSERT INTO refresh_token (user_id, token_hash, device, expires_at) VALUES (?, ?, ?, ?)",
                userId, hash(refresh), normalizeDevice(device), Timestamp.from(Instant.now().plusSeconds(refreshTtlSeconds)));
        return new AuthTokens(jwtService.createAccessToken(user), refresh, user);
    }

    private String normalizeDevice(String device) {
        if (device == null || device.isBlank()) return "";
        return device.length() <= 120 ? device : device.substring(0, 120);
    }

    private void validateCredentials(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_@.\\-]{3,120}")) throw new IllegalArgumentException("用户名需为 3-120 位字母、数字或邮箱格式");
        if (password == null || password.length() < 8 || password.length() > 128) throw new IllegalArgumentException("密码长度需为 8-128 位");
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "." + UUID.randomUUID();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).replace('-', '0');
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record AuthTokens(String accessToken, String refreshToken, CurrentUser user) {
    }

    private record LoginRow(long id, String username, String nickname, String passwordHash, String status) {
    }

    private record UserRow(long id, String username, String nickname) {
    }

    private record AuthorityRow(long id, String username, String nickname, String roleCode, String permissionCode) {
    }
}
