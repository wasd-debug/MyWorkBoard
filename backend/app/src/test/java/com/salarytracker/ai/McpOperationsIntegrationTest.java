package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.mcp.McpOperationsService;
import com.salarytracker.ai.mcp.McpOAuthService;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpOperationsIntegrationTest extends MySqlIntegrationTestSupport {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void isolatesDiagnosticsAndDisconnectsOnlyCurrentUsersClientGrant() {
        long userId = createUser("owner");
        long otherUserId = createUser("other");
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        McpOperationsService operations = new McpOperationsService(jdbc, mapper, currentUser, true, false, true);

        String clientId = createClient("Diagnostics");
        String grantId = createGrant(userId, clientId);
        String otherClientId = createClient("Other");
        createGrant(otherUserId, otherClientId);
        operations.event(userId, clientId, null, "authorization.approved", "SUCCESS",
                new McpOperationsService.RequestContext("10.20.30.40", "Codex Test"), Map.of("scopeCount", 1));

        assertEquals(1, operations.clients().size());
        assertEquals("10.20.30.*", operations.events(10).get(0).maskedIp());
        assertEquals(1, operations.diagnostics("https://work.example", "https://work.example",
                "https://work.example").activeGrantCount());

        operations.disconnectClient(clientId);
        assertTrue(operations.clients().get(0).revokedAt() != null);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_grant WHERE id=? AND revoked_at IS NULL",
                Integer.class, grantId));
        assertThrows(IllegalArgumentException.class, () -> operations.disconnectClient(otherClientId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_grant WHERE client_id=? AND revoked_at IS NULL",
                Integer.class, otherClientId));
    }

    @Test
    void cleansExpiredCodesAndScrubsOldOauthTokenSecrets() {
        long userId = createUser("cleanup");
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        McpOperationsService operations = new McpOperationsService(jdbc, mapper, currentUser, true, false, true);
        String clientId = createClient("Cleanup");
        String grantId = createGrant(userId, clientId);
        jdbc.update("""
                INSERT INTO mcp_oauth_code(id,code_hash,grant_id,client_id,user_id,redirect_uri,resource_uri,
                    scopes,code_challenge,code_challenge_method,expires_at)
                VALUES(?,?,?,?,?,'https://client/callback','https://work/mcp','[\"mcp:worktime:read\"]',?,'S256',?)
                """, UUID.randomUUID().toString(), hex(), grantId, clientId, userId, "x".repeat(43),
                Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)));
        String tokenId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO mcp_personal_token(id,user_id,name,token_type,oauth_client_id,oauth_grant_id,
                    token_hash,refresh_token_hash,token_hint,scopes,expires_at,refresh_expires_at,revoked_at)
                VALUES(?,?,'OAuth','OAUTH',?,?,?,?,'wbo_…test','[\"mcp:worktime:read\"]',?,?,?)
                """, tokenId, userId, clientId, grantId, hex(), hex(),
                Timestamp.from(Instant.now().minus(40, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(40, ChronoUnit.DAYS)),
                Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)));

        operations.cleanupExpiredData();

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_oauth_code WHERE grant_id=?", Integer.class, grantId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_personal_token WHERE id=? AND refresh_token_hash IS NOT NULL",
                Integer.class, tokenId));
    }

    @Test
    void limitsDynamicClientRegistrationsPerIp() {
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        McpOperationsService operations = new McpOperationsService(jdbc, mapper, currentUser, true, false, true);
        for (int index = 0; index < 20; index++) {
            String clientId = createClient("Rate " + index);
            jdbc.update("UPDATE mcp_oauth_client SET registration_ip='10.0.0.8' WHERE client_id=?", clientId);
        }

        McpOAuthService.OAuthException exception = assertThrows(McpOAuthService.OAuthException.class,
                () -> operations.requireRegistrationCapacity(
                        new McpOperationsService.RequestContext("10.0.0.8", "Inspector")));
        assertEquals(429, exception.status());
    }

    private long createUser(String label) {
        String username = "mcp-ops-" + label + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', ?)", username, label);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private String createClient(String name) {
        String clientId = "mcp_test_" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO mcp_oauth_client(client_id,client_name,redirect_uris,grant_types,response_types,token_endpoint_auth_method)
                VALUES(?,?,'[\"https://client.example/callback\"]','[\"authorization_code\"]','[\"code\"]','none')
                """, clientId, name);
        return clientId;
    }

    private String createGrant(long userId, String clientId) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO mcp_grant(id,user_id,client_id,scopes) VALUES(?,?,?,'[\"mcp:worktime:read\"]')",
                id, userId, clientId);
        return id;
    }

    private String hex() { return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""); }
}
