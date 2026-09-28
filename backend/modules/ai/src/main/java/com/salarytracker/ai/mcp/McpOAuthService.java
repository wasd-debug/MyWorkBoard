package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class McpOAuthService {
    public static final Set<String> SUPPORTED_SCOPES = Set.of(
            McpPersonalTokenService.LEDGER_READ, McpPersonalTokenService.LEDGER_PREPARE,
            McpPersonalTokenService.LEDGER_COMMIT, McpPersonalTokenService.WORKTIME_READ,
            McpPersonalTokenService.WORKTIME_PREPARE, McpPersonalTokenService.WORKTIME_COMMIT);
    private static final long ACCESS_TTL_SECONDS = 3600;
    private static final long REFRESH_TTL_DAYS = 30;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserResolver currentUser;
    private final McpPersonalTokenService personalTokens;
    private final LedgerBookService books;
    private final McpOperationsService operations;
    private final SecureRandom random = new SecureRandom();

    public McpOAuthService(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserResolver currentUser,
                           McpPersonalTokenService personalTokens, LedgerBookService books,
                           McpOperationsService operations) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.personalTokens = personalTokens;
        this.books = books;
        this.operations = operations;
    }

    @Transactional
    public ClientRegistration registerClient(RegisterClientRequest request) {
        return registerClient(request, null);
    }

    @Transactional
    public ClientRegistration registerClient(RegisterClientRequest request, McpOperationsService.RequestContext context) {
        operations.requireRegistrationCapacity(context);
        String name = clean(request == null ? null : request.clientName(), 160, "client_name");
        Set<String> redirects = validateRedirectUris(request == null ? null : request.redirectUris());
        Set<String> grants = normalized(request == null ? null : request.grantTypes(),
                Set.of("authorization_code", "refresh_token"));
        Set<String> responses = normalized(request == null ? null : request.responseTypes(), Set.of("code"));
        if (!Set.of("authorization_code", "refresh_token").containsAll(grants)
                || !Set.of("code").containsAll(responses)) {
            throw oauth("invalid_client_metadata", "仅支持 authorization_code、refresh_token 和 code");
        }
        String authMethod = request == null || request.tokenEndpointAuthMethod() == null
                ? "none" : request.tokenEndpointAuthMethod().trim();
        if (!"none".equals(authMethod)) throw oauth("invalid_client_metadata", "仅支持公共客户端 token_endpoint_auth_method=none");
        String clientId = "mcp_" + randomToken(24);
        jdbc.update("""
                INSERT INTO mcp_oauth_client(client_id,client_name,redirect_uris,grant_types,response_types,
                    token_endpoint_auth_method,registration_ip,registration_user_agent)
                VALUES(?,?,?,?,?,?,?,?)
                """, clientId, name, json(redirects), json(grants), json(responses), authMethod,
                context == null ? null : context.ip(), context == null ? null : context.userAgent());
        operations.event(null, clientId, null, "client.register", "SUCCESS", context,
                Map.of("clientName", name, "redirectCount", redirects.size()));
        return new ClientRegistration(clientId, name, redirects, grants, responses, authMethod,
                Instant.now().getEpochSecond());
    }

    public AuthorizationPreview preview(AuthorizationRequest request) {
        ClientRow client = validateAuthorization(request);
        Set<String> scopes = scope(request.scope());
        List<BookOption> availableBooks = scopes.stream().anyMatch(value -> value.startsWith("mcp:ledger:"))
                ? books.books().stream().map(book -> new BookOption(book.id(), book.name())).toList() : List.of();
        Set<String> selected = jdbc.query("""
                SELECT book_ids FROM mcp_grant WHERE user_id=? AND client_id=? AND revoked_at IS NULL
                """, (result, rowNum) -> readSet(result.getString("book_ids")), currentUser.id(), client.clientId())
                .stream().findFirst().orElseGet(() -> availableBooks.stream().map(BookOption::id)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        return new AuthorizationPreview(client.clientId(), client.clientName(), request.redirectUri(), scopes,
                availableBooks, selected, request.state());
    }

    public void validateAuthorizationRequest(AuthorizationRequest request) {
        validateAuthorization(request);
    }

    @Transactional
    public AuthorizationDecision decide(AuthorizationDecisionCommand command) {
        return decide(command, null);
    }

    @Transactional
    public AuthorizationDecision decide(AuthorizationDecisionCommand command,
                                        McpOperationsService.RequestContext context) {
        AuthorizationRequest request = command == null ? null : command.request();
        ClientRow client = validateAuthorization(request);
        if (!command.approved()) {
            operations.event(currentUser.id(), client.clientId(), null, "authorization.denied", "SUCCESS", context,
                    Map.of("scope", request.scope()));
            return new AuthorizationDecision(redirect(request.redirectUri(), Map.of("error", "access_denied",
                    "error_description", "用户拒绝授权"), request.state()));
        }
        Set<String> scopes = scope(request.scope());
        Set<String> bookIds = scopes.stream().anyMatch(value -> value.startsWith("mcp:ledger:"))
                ? personalTokens.normalizeBookIds(command.bookIds(), currentUser.id(), scopes) : Set.of();
        if (scopes.stream().anyMatch(value -> value.startsWith("mcp:ledger:")) && bookIds.isEmpty()) {
            throw oauth("invalid_scope", "账本权限至少选择一个账本");
        }
        String grantId = jdbc.query("SELECT id FROM mcp_grant WHERE user_id=? AND client_id=?",
                (result, rowNum) -> result.getString(1), currentUser.id(), client.clientId()).stream()
                .findFirst().orElse(UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO mcp_grant(id,user_id,client_id,scopes,book_ids,revoked_at)
                VALUES(?,?,?,?,?,NULL)
                ON DUPLICATE KEY UPDATE scopes=VALUES(scopes),book_ids=VALUES(book_ids),revoked_at=NULL
                """, grantId, currentUser.id(), client.clientId(), json(scopes), jsonOrNull(bookIds));
        jdbc.update("UPDATE mcp_personal_token SET revoked_at=CURRENT_TIMESTAMP(6) WHERE oauth_grant_id=? AND revoked_at IS NULL",
                grantId);
        jdbc.update("UPDATE mcp_oauth_code SET consumed_at=CURRENT_TIMESTAMP(6) WHERE grant_id=? AND consumed_at IS NULL",
                grantId);
        String code = "wbc_" + randomToken(36);
        jdbc.update("""
                INSERT INTO mcp_oauth_code(id,code_hash,grant_id,client_id,user_id,redirect_uri,resource_uri,
                    scopes,book_ids,code_challenge,code_challenge_method,expires_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), hash(code), grantId, client.clientId(), currentUser.id(),
                request.redirectUri(), request.resource(), json(scopes), jsonOrNull(bookIds), request.codeChallenge(),
                "S256", Timestamp.from(Instant.now().plus(5, ChronoUnit.MINUTES)));
        operations.event(currentUser.id(), client.clientId(), null, "authorization.approved", "SUCCESS", context,
                Map.of("scopeCount", scopes.size(), "bookCount", bookIds.size()));
        return new AuthorizationDecision(redirect(request.redirectUri(), Map.of("code", code), request.state()));
    }

    @Transactional
    public TokenResponse exchange(TokenRequest request) {
        return exchange(request, null);
    }

    @Transactional
    public TokenResponse exchange(TokenRequest request, McpOperationsService.RequestContext context) {
        if (request == null) throw oauth("invalid_request", "请求不能为空");
        String grantType = required(request.grantType(), "grant_type");
        TokenResponse response = switch (grantType) {
            case "authorization_code" -> exchangeCode(request);
            case "refresh_token" -> refresh(request);
            default -> throw oauth("unsupported_grant_type", "不支持的 grant_type");
        };
        IssuedToken issued = jdbc.query("""
                SELECT id,user_id,oauth_client_id FROM mcp_personal_token WHERE token_hash=?
                """, (result, rowNum) -> new IssuedToken(result.getString("id"), result.getLong("user_id"),
                result.getString("oauth_client_id")), hash(response.accessToken())).stream().findFirst().orElse(null);
        operations.event(issued == null ? null : issued.userId(), request.clientId(), issued == null ? null : issued.id(),
                "authorization_code".equals(grantType) ? "code.exchanged" : "token.refreshed",
                "SUCCESS", context, Map.of("scope", response.scope()));
        return response;
    }

    @Transactional
    public void revoke(String rawToken, String clientId) {
        revoke(rawToken, clientId, null);
    }

    @Transactional
    public void revoke(String rawToken, String clientId, McpOperationsService.RequestContext context) {
        if (rawToken == null || rawToken.isBlank()) return;
        IssuedToken issued = jdbc.query("""
                SELECT id,user_id,oauth_client_id FROM mcp_personal_token
                WHERE token_type='OAUTH' AND oauth_client_id=? AND (token_hash=? OR refresh_token_hash=?)
                """, (result, rowNum) -> new IssuedToken(result.getString("id"), result.getLong("user_id"),
                result.getString("oauth_client_id")), clientId, hash(rawToken.trim()), hash(rawToken.trim()))
                .stream().findFirst().orElse(null);
        int affected = jdbc.update("""
                UPDATE mcp_personal_token SET revoked_at=CURRENT_TIMESTAMP(6)
                WHERE token_type='OAUTH' AND oauth_client_id=? AND revoked_at IS NULL
                  AND (token_hash=? OR refresh_token_hash=?)
                """, clientId, hash(rawToken.trim()), hash(rawToken.trim()));
        operations.event(issued == null ? null : issued.userId(), issued == null ? null : clientId,
                issued == null ? null : issued.id(),
                "token.revoked", "SUCCESS", context,
                Map.of("matched", affected > 0));
    }

    public List<GrantView> grants() {
        return jdbc.query("""
                SELECT g.id,g.client_id,c.client_name,g.scopes,g.book_ids,g.created_at,g.updated_at,g.last_used_at
                FROM mcp_grant g JOIN mcp_oauth_client c ON c.client_id=g.client_id
                WHERE g.user_id=? AND g.revoked_at IS NULL ORDER BY g.updated_at DESC
                """, (result, rowNum) -> new GrantView(result.getString("id"), result.getString("client_id"),
                result.getString("client_name"), readSet(result.getString("scopes")),
                readSet(result.getString("book_ids")), instant(result, "created_at"),
                instant(result, "updated_at"), instant(result, "last_used_at")), currentUser.id());
    }

    @Transactional
    public void revokeGrant(String grantId) {
        if (jdbc.update("UPDATE mcp_grant SET revoked_at=CURRENT_TIMESTAMP(6) WHERE id=? AND user_id=? AND revoked_at IS NULL",
                grantId, currentUser.id()) != 1) throw new IllegalArgumentException("OAuth 授权不存在或已撤销");
        jdbc.update("UPDATE mcp_personal_token SET revoked_at=CURRENT_TIMESTAMP(6) WHERE oauth_grant_id=? AND revoked_at IS NULL",
                grantId);
        operations.event(currentUser.id(), null, null, "grant.revoked", "SUCCESS", null,
                Map.of("grantId", grantId));
    }

    public boolean isTrustedRedirect(String clientId, String redirectUri) {
        if (clientId == null || clientId.isBlank() || redirectUri == null || redirectUri.isBlank()) return false;
        return jdbc.query("SELECT redirect_uris FROM mcp_oauth_client WHERE client_id=? AND revoked_at IS NULL",
                (result, rowNum) -> readSet(result.getString(1)), clientId).stream()
                .findFirst().map(values -> values.contains(redirectUri)).orElse(false);
    }

    public String errorRedirect(String redirectUri, OAuthException exception, String state) {
        return redirect(redirectUri, Map.of("error", exception.error(),
                "error_description", exception.getMessage()), state);
    }

    private TokenResponse exchangeCode(TokenRequest request) {
        String clientId = required(request.clientId(), "client_id");
        CodeRow code = jdbc.query("""
                SELECT * FROM mcp_oauth_code WHERE code_hash=? FOR UPDATE
                """, (result, rowNum) -> code(result), hash(required(request.code(), "code"))).stream()
                .findFirst().orElseThrow(() -> oauth("invalid_grant", "授权码无效"));
        if (code.consumedAt() != null || !code.expiresAt().isAfter(Instant.now()))
            throw oauth("invalid_grant", "授权码已使用或已过期");
        if (!code.clientId().equals(clientId) || !code.redirectUri().equals(required(request.redirectUri(), "redirect_uri"))
                || !code.resourceUri().equals(required(request.resource(), "resource")))
            throw oauth("invalid_grant", "授权码与客户端、回调地址或资源不匹配");
        if (!pkce(required(request.codeVerifier(), "code_verifier")).equals(code.codeChallenge()))
            throw oauth("invalid_grant", "PKCE 校验失败");
        requireActiveClientAndGrant(clientId, code.grantId());
        jdbc.update("UPDATE mcp_oauth_code SET consumed_at=CURRENT_TIMESTAMP(6) WHERE id=? AND consumed_at IS NULL", code.id());
        return issue(code.userId(), code.clientId(), code.grantId(), code.scopes(), code.bookIds());
    }

    private TokenResponse refresh(TokenRequest request) {
        String clientId = required(request.clientId(), "client_id");
        String rawRefresh = required(request.refreshToken(), "refresh_token");
        OAuthTokenRow row = jdbc.query("""
                SELECT * FROM mcp_personal_token WHERE token_type='OAUTH' AND oauth_client_id=?
                  AND refresh_token_hash=? AND revoked_at IS NULL FOR UPDATE
                """, (result, rowNum) -> oauthToken(result), clientId, hash(rawRefresh)).stream()
                .findFirst().orElseThrow(() -> oauth("invalid_grant", "refresh token 无效或已轮换"));
        if (row.refreshExpiresAt() == null || !row.refreshExpiresAt().isAfter(Instant.now()))
            throw oauth("invalid_grant", "refresh token 已过期");
        requireActiveClientAndGrant(clientId, row.grantId());
        Set<String> scopes = request.scope() == null || request.scope().isBlank() ? row.scopes() : scope(request.scope());
        if (!row.scopes().containsAll(scopes)) throw oauth("invalid_scope", "刷新时不能扩大 scope");
        String access = "wbo_" + randomToken(36);
        String refresh = "wbr_" + randomToken(36);
        Instant accessExpiry = Instant.now().plusSeconds(ACCESS_TTL_SECONDS);
        Instant refreshExpiry = Instant.now().plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS);
        jdbc.update("""
                UPDATE mcp_personal_token SET token_hash=?,token_hint=?,refresh_token_hash=?,scopes=?,
                    expires_at=?,refresh_expires_at=?,last_used_at=CURRENT_TIMESTAMP(6)
                WHERE id=?
                """, hash(access), hint(access), hash(refresh), json(scopes), Timestamp.from(accessExpiry),
                Timestamp.from(refreshExpiry), row.id());
        return new TokenResponse(access, "Bearer", ACCESS_TTL_SECONDS, refresh, String.join(" ", scopes));
    }

    private TokenResponse issue(long userId, String clientId, String grantId, Set<String> scopes, Set<String> bookIds) {
        ClientRow client = client(clientId);
        String access = "wbo_" + randomToken(36);
        String refresh = "wbr_" + randomToken(36);
        Instant accessExpiry = Instant.now().plusSeconds(ACCESS_TTL_SECONDS);
        Instant refreshExpiry = Instant.now().plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS);
        jdbc.update("""
                INSERT INTO mcp_personal_token(id,user_id,name,token_type,oauth_client_id,oauth_grant_id,
                    token_hash,refresh_token_hash,token_hint,scopes,book_ids,expires_at,refresh_expires_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID().toString(), userId, "OAuth · " + client.clientName(), "OAUTH", clientId,
                grantId, hash(access), hash(refresh), hint(access), json(scopes), jsonOrNull(bookIds),
                Timestamp.from(accessExpiry), Timestamp.from(refreshExpiry));
        return new TokenResponse(access, "Bearer", ACCESS_TTL_SECONDS, refresh, String.join(" ", scopes));
    }

    private ClientRow validateAuthorization(AuthorizationRequest request) {
        if (request == null || !"code".equals(request.responseType()))
            throw oauth("unsupported_response_type", "仅支持 response_type=code");
        ClientRow client = client(required(request.clientId(), "client_id"));
        if (!client.redirectUris().contains(required(request.redirectUri(), "redirect_uri")))
            throw oauth("invalid_request", "redirect_uri 未登记");
        scope(request.scope());
        if (!"S256".equals(request.codeChallengeMethod()) || request.codeChallenge() == null
                || !request.codeChallenge().matches("[A-Za-z0-9_-]{43,128}"))
            throw oauth("invalid_request", "必须使用 PKCE S256");
        URI resource = uri(required(request.resource(), "resource"));
        if (request.resource().length() > 1000) throw oauth("invalid_target", "resource 过长");
        if (!"/mcp".equals(resource.getPath())) throw oauth("invalid_target", "resource 必须指向 MCP /mcp 端点");
        return client;
    }

    private ClientRow client(String clientId) {
        return jdbc.query("SELECT * FROM mcp_oauth_client WHERE client_id=? AND revoked_at IS NULL",
                (result, rowNum) -> new ClientRow(result.getString("client_id"), result.getString("client_name"),
                        readSet(result.getString("redirect_uris"))), clientId).stream().findFirst()
                .orElseThrow(() -> oauth("invalid_client", "OAuth 客户端不存在或已撤销"));
    }

    private void requireActiveClientAndGrant(String clientId, String grantId) {
        client(clientId);
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM mcp_grant WHERE id=? AND client_id=? AND revoked_at IS NULL",
                Integer.class, grantId, clientId);
        if (count == null || count == 0) throw oauth("invalid_grant", "用户授权已撤销");
    }

    private Set<String> scope(String raw) {
        Set<String> values = new LinkedHashSet<>(List.of(required(raw, "scope").trim().split("\\s+")));
        if (values.isEmpty() || !SUPPORTED_SCOPES.containsAll(values)) throw oauth("invalid_scope", "包含不支持的 scope");
        try { return personalTokens.normalizeScopes(values); }
        catch (IllegalArgumentException exception) { throw oauth("invalid_scope", exception.getMessage()); }
    }

    private Set<String> validateRedirectUris(Set<String> values) {
        if (values == null || values.isEmpty() || values.size() > 20)
            throw oauth("invalid_redirect_uri", "redirect_uris 数量应为 1-20");
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.length() > 1000) {
                throw oauth("invalid_redirect_uri", "redirect_uri 过长");
            }
            URI uri = uri(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
            boolean loopback = "http".equals(scheme) && ("localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost()));
            boolean custom = !Set.of("http", "https", "javascript", "data", "file").contains(scheme);
            if (uri.getFragment() != null || uri.getUserInfo() != null || !("https".equals(scheme) || loopback || custom))
                throw oauth("invalid_redirect_uri", "redirect_uri 必须是 HTTPS、环回 HTTP 或安全自定义 scheme");
            result.add(uri.toString());
        }
        return Set.copyOf(result);
    }

    private URI uri(String value) {
        try {
            URI uri = URI.create(required(value, "URI"));
            if (uri.getScheme() == null) throw new IllegalArgumentException();
            return uri;
        } catch (Exception exception) { throw oauth("invalid_request", "URI 格式无效"); }
    }

    private String redirect(String redirectUri, Map<String, String> values, String state) {
        StringBuilder result = new StringBuilder(redirectUri);
        result.append(redirectUri.contains("?") ? '&' : '?');
        values.forEach((key, value) -> result.append(encode(key)).append('=').append(encode(value)).append('&'));
        if (state != null && !state.isBlank()) result.append("state=").append(encode(state)).append('&');
        return result.substring(0, result.length() - 1);
    }

    private String pkce(String verifier) {
        if (!verifier.matches("[A-Za-z0-9._~-]{43,128}")) throw oauth("invalid_grant", "code_verifier 格式无效");
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) { throw new IllegalStateException("无法执行 PKCE 校验", exception); }
    }

    private String hash(String raw) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法计算 OAuth Token 摘要", exception); }
    }

    private String randomToken(int size) { byte[] bytes = new byte[size]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hint(String raw) { return raw.substring(0, 8) + "…" + raw.substring(raw.length() - 4); }
    private String encode(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String required(String value, String field) { if (value == null || value.isBlank()) throw oauth("invalid_request", field + " 不能为空"); return value.trim(); }
    private String clean(String value, int max, String field) { String result = required(value, field); if (result.length() > max) throw oauth("invalid_client_metadata", field + " 过长"); return result; }
    private Set<String> normalized(Set<String> value, Set<String> defaults) { return value == null || value.isEmpty() ? defaults : Set.copyOf(value); }
    private String json(Set<String> value) { try { return mapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("无法保存 OAuth 数据", exception); } }
    private String jsonOrNull(Set<String> value) { return value == null || value.isEmpty() ? null : json(value); }
    private Set<String> readSet(String value) { if (value == null || value.isBlank()) return Set.of(); try { return Set.copyOf(mapper.readValue(value, new TypeReference<List<String>>() { })); } catch (Exception exception) { throw new IllegalStateException("OAuth 数据损坏", exception); } }
    private Instant instant(ResultSet result, String column) throws SQLException { Timestamp value = result.getTimestamp(column); return value == null ? null : value.toInstant(); }
    private OAuthException oauth(String error, String description) { return new OAuthException(error, description); }

    private CodeRow code(ResultSet result) throws SQLException { return new CodeRow(result.getString("id"), result.getString("grant_id"), result.getString("client_id"), result.getLong("user_id"), result.getString("redirect_uri"), result.getString("resource_uri"), readSet(result.getString("scopes")), readSet(result.getString("book_ids")), result.getString("code_challenge"), instant(result, "expires_at"), instant(result, "consumed_at")); }
    private OAuthTokenRow oauthToken(ResultSet result) throws SQLException { return new OAuthTokenRow(result.getString("id"), result.getString("oauth_grant_id"), readSet(result.getString("scopes")), readSet(result.getString("book_ids")), instant(result, "refresh_expires_at")); }

    public record RegisterClientRequest(@JsonProperty("client_name") String clientName,
                                        @JsonProperty("redirect_uris") Set<String> redirectUris,
                                        @JsonProperty("grant_types") Set<String> grantTypes,
                                        @JsonProperty("response_types") Set<String> responseTypes,
                                        @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod) { }
    public record ClientRegistration(@JsonProperty("client_id") String clientId,
                                     @JsonProperty("client_name") String clientName,
                                     @JsonProperty("redirect_uris") Set<String> redirectUris,
                                     @JsonProperty("grant_types") Set<String> grantTypes,
                                     @JsonProperty("response_types") Set<String> responseTypes,
                                     @JsonProperty("token_endpoint_auth_method") String tokenEndpointAuthMethod,
                                     @JsonProperty("client_id_issued_at") long clientIdIssuedAt) { }
    public record AuthorizationRequest(String responseType, String clientId, String redirectUri, String scope,
                                       String state, String codeChallenge, String codeChallengeMethod, String resource) { }
    public record BookOption(String id, String name) { }
    public record AuthorizationPreview(String clientId, String clientName, String redirectUri, Set<String> scopes,
                                       List<BookOption> books, Set<String> selectedBookIds, String state) { }
    public record AuthorizationDecisionCommand(AuthorizationRequest request, boolean approved, Set<String> bookIds) { }
    public record AuthorizationDecision(String redirectUrl) { }
    public record TokenRequest(String grantType, String clientId, String code, String redirectUri,
                               String codeVerifier, String refreshToken, String scope, String resource) { }
    public record TokenResponse(@JsonProperty("access_token") String accessToken,
                                @JsonProperty("token_type") String tokenType,
                                @JsonProperty("expires_in") long expiresIn,
                                @JsonProperty("refresh_token") String refreshToken,
                                String scope) { }
    public record GrantView(String id, String clientId, String clientName, Set<String> scopes, Set<String> bookIds,
                            Instant createdAt, Instant updatedAt, Instant lastUsedAt) { }
    public static final class OAuthException extends IllegalArgumentException {
        private final String error;
        private final int status;
        OAuthException(String error, String description) { this(error, description, 400); }
        OAuthException(String error, String description, int status) {
            super(description);
            this.error = error;
            this.status = status;
        }
        public String error() { return error; }
        public int status() { return status; }
    }
    private record ClientRow(String clientId, String clientName, Set<String> redirectUris) { }
    private record CodeRow(String id, String grantId, String clientId, long userId, String redirectUri,
                           String resourceUri, Set<String> scopes, Set<String> bookIds, String codeChallenge,
                           Instant expiresAt, Instant consumedAt) { }
    private record OAuthTokenRow(String id, String grantId, Set<String> scopes, Set<String> bookIds,
                                 Instant refreshExpiresAt) { }
    private record IssuedToken(String id, long userId, String clientId) { }
}
