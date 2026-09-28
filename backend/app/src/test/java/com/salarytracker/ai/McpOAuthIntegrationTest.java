package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.mcp.McpOAuthService;
import com.salarytracker.ai.mcp.McpPersonalTokenService;
import com.salarytracker.identity.AuthService;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.platform.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpOAuthIntegrationTest extends MySqlIntegrationTestSupport {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void exchangesPkceCodeRotatesRefreshAndRevokesGrant() throws Exception {
        long userId = createUser();
        CurrentUser user = new CurrentUser(userId, "oauth-user", "OAuth", Set.of("worktime:read"));
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        AuthService auth = mock(AuthService.class);
        when(currentUser.id()).thenReturn(userId);
        when(auth.loadUser(userId)).thenReturn(user);
        McpPersonalTokenService personal = new McpPersonalTokenService(jdbc, mapper, currentUser, auth);
        McpOAuthService oauth = new McpOAuthService(jdbc, mapper, currentUser, personal, mock(LedgerBookService.class));

        McpOAuthService.ClientRegistration client = oauth.registerClient(new McpOAuthService.RegisterClientRequest(
                "Codex QA", Set.of("http://127.0.0.1:1455/callback"), null, null, "none"));
        String verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        McpOAuthService.AuthorizationRequest request = new McpOAuthService.AuthorizationRequest("code",
                client.clientId(), "http://127.0.0.1:1455/callback", McpPersonalTokenService.WORKTIME_READ,
                "state-1", challenge, "S256", "http://localhost/mcp");

        McpOAuthService.AuthorizationPreview preview = oauth.preview(request);
        assertEquals("Codex QA", preview.clientName());
        assertTrue(preview.books().isEmpty());
        String redirect = oauth.decide(new McpOAuthService.AuthorizationDecisionCommand(
                request, true, Set.of())).redirectUrl();
        String code = query(redirect, "code");
        assertEquals("state-1", query(redirect, "state"));

        McpOAuthService.TokenResponse issued = oauth.exchange(new McpOAuthService.TokenRequest(
                "authorization_code", client.clientId(), code, request.redirectUri(), verifier,
                null, null, request.resource()));
        assertTrue(issued.accessToken().startsWith("wbo_"));
        assertTrue(issued.refreshToken().startsWith("wbr_"));
        assertEquals(userId, personal.authenticate(issued.accessToken(), true).user().id());
        assertTrue(personal.list().isEmpty(), "OAuth token 不应出现在 PAT 列表");
        assertThrows(McpOAuthService.OAuthException.class, () -> oauth.exchange(new McpOAuthService.TokenRequest(
                "authorization_code", client.clientId(), code, request.redirectUri(), verifier,
                null, null, request.resource())));

        McpOAuthService.TokenResponse refreshed = oauth.exchange(new McpOAuthService.TokenRequest(
                "refresh_token", client.clientId(), null, null, null, issued.refreshToken(), null, null));
        assertNotEquals(issued.accessToken(), refreshed.accessToken());
        assertThrows(UnauthorizedException.class, () -> personal.authenticate(issued.accessToken(), true));
        assertThrows(McpOAuthService.OAuthException.class, () -> oauth.exchange(new McpOAuthService.TokenRequest(
                "refresh_token", client.clientId(), null, null, null, issued.refreshToken(), null, null)));
        assertEquals(1, oauth.grants().size());
        oauth.revokeGrant(oauth.grants().get(0).id());
        assertThrows(UnauthorizedException.class, () -> personal.authenticate(refreshed.accessToken(), true));
        assertTrue(oauth.grants().isEmpty());
    }

    @Test
    void rejectsUnsafeRedirectPlainPkceAndInvalidScopeCombinations() throws Exception {
        long userId = createUser();
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        McpPersonalTokenService personal = new McpPersonalTokenService(jdbc, mapper, currentUser, mock(AuthService.class));
        McpOAuthService oauth = new McpOAuthService(jdbc, mapper, currentUser, personal, mock(LedgerBookService.class));

        assertThrows(McpOAuthService.OAuthException.class, () -> oauth.registerClient(
                new McpOAuthService.RegisterClientRequest("Bad", Set.of("http://example.com/callback"),
                        null, null, "none")));
        var client = oauth.registerClient(new McpOAuthService.RegisterClientRequest("Inspector",
                Set.of("https://client.example/callback"), null, null, "none"));
        assertThrows(McpOAuthService.OAuthException.class, () -> oauth.preview(
                new McpOAuthService.AuthorizationRequest("code", client.clientId(),
                        "https://client.example/callback", McpPersonalTokenService.WORKTIME_READ,
                        null, "challenge", "plain", "https://work.example/mcp")));
        String verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        assertThrows(McpOAuthService.OAuthException.class, () -> oauth.preview(
                new McpOAuthService.AuthorizationRequest("code", client.clientId(),
                        "https://client.example/callback", McpPersonalTokenService.WORKTIME_COMMIT,
                        null, challenge, "S256", "https://work.example/mcp")));
    }

    @Test
    void reauthorizationInvalidatesPreviouslyIssuedCode() throws Exception {
        long userId = createUser();
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        McpPersonalTokenService personal = new McpPersonalTokenService(jdbc, mapper, currentUser, mock(AuthService.class));
        McpOAuthService oauth = new McpOAuthService(jdbc, mapper, currentUser, personal, mock(LedgerBookService.class));
        var client = oauth.registerClient(new McpOAuthService.RegisterClientRequest("Reauthorize QA",
                Set.of("http://127.0.0.1:1455/callback"), null, null, "none"));
        String verifier = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        var request = new McpOAuthService.AuthorizationRequest("code", client.clientId(),
                "http://127.0.0.1:1455/callback", McpPersonalTokenService.WORKTIME_READ,
                "state", challenge, "S256", "http://localhost/mcp");

        String firstCode = query(oauth.decide(new McpOAuthService.AuthorizationDecisionCommand(
                request, true, Set.of())).redirectUrl(), "code");
        String secondCode = query(oauth.decide(new McpOAuthService.AuthorizationDecisionCommand(
                request, true, Set.of())).redirectUrl(), "code");

        assertThrows(McpOAuthService.OAuthException.class, () -> oauth.exchange(new McpOAuthService.TokenRequest(
                "authorization_code", client.clientId(), firstCode, request.redirectUri(), verifier,
                null, null, request.resource())));
        assertTrue(oauth.exchange(new McpOAuthService.TokenRequest("authorization_code", client.clientId(),
                secondCode, request.redirectUri(), verifier, null, null, request.resource()))
                .accessToken().startsWith("wbo_"));
    }

    private String query(String raw, String name) {
        String query = URI.create(raw).getRawQuery();
        for (String entry : query.split("&")) {
            String[] pair = entry.split("=", 2);
            if (name.equals(java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                return java.net.URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private long createUser() {
        String username = "mcp-oauth-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'OAuth')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
