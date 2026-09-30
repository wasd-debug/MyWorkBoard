package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.mcp.McpPersonalTokenService;
import com.salarytracker.identity.AuthService;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpPersonalTokenIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void createsAuthenticatesScopesAndRevokesHashedToken() {
        long userId = createUser();
        CurrentUser user = new CurrentUser(userId, "mcp-user", "MCP", Set.of("ledger:read", "worktime:read"));
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        AuthService auth = mock(AuthService.class);
        when(currentUser.id()).thenReturn(userId);
        when(auth.loadUser(userId)).thenReturn(user);
        McpPersonalTokenService service = new McpPersonalTokenService(jdbc,
                new ObjectMapper().findAndRegisterModules(), currentUser, auth);

        McpPersonalTokenService.CreatedToken created = service.create(new McpPersonalTokenService.CreateToken(
                "本地 Codex", Set.of(McpPersonalTokenService.LEDGER_READ, McpPersonalTokenService.WORKTIME_READ,
                McpPersonalTokenService.LEDGER_PREPARE, McpPersonalTokenService.WORKTIME_PREPARE,
                McpPersonalTokenService.LEDGER_COMMIT, McpPersonalTokenService.WORKTIME_COMMIT),
                Set.of(), Instant.now().plus(7, ChronoUnit.DAYS)));

        assertTrue(created.rawToken().startsWith("wbt_"));
        assertFalse(created.token().tokenHint().contains(created.rawToken()));
        assertEquals(64, jdbc.queryForObject("SELECT LENGTH(token_hash) FROM mcp_personal_token WHERE id=?",
                Integer.class, created.token().id()));
        assertEquals(created.token().id(), service.authenticate(created.rawToken()).id());
        assertEquals("本地 Codex", service.authenticate(created.rawToken()).name());
        assertTrue(service.authenticate(created.rawToken()).scopes().contains(McpPersonalTokenService.LEDGER_PREPARE));
        assertTrue(service.authenticate(created.rawToken()).scopes().contains(McpPersonalTokenService.LEDGER_COMMIT));
        assertTrue(service.list().get(0).lastUsedAt() != null);

        service.revoke(created.token().id());
        assertThrows(UnauthorizedException.class, () -> service.authenticate(created.rawToken()));
    }

    @Test
    void rejectsUnsupportedScopesAndPastExpiry() {
        long userId = createUser();
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        McpPersonalTokenService service = new McpPersonalTokenService(jdbc,
                new ObjectMapper().findAndRegisterModules(), currentUser, mock(AuthService.class));

        assertThrows(IllegalArgumentException.class, () -> service.create(
                new McpPersonalTokenService.CreateToken("越权", Set.of("mcp:ledger:admin"), Set.of(),
                        Instant.now().plus(1, ChronoUnit.DAYS))));
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new McpPersonalTokenService.CreateToken("孤立提交", Set.of(McpPersonalTokenService.LEDGER_COMMIT),
                        Set.of(), Instant.now().plus(1, ChronoUnit.DAYS))));
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new McpPersonalTokenService.CreateToken("过期", Set.of(McpPersonalTokenService.WORKTIME_READ),
                        Set.of(), Instant.now().minus(1, ChronoUnit.MINUTES))));
    }

    @Test
    void permissionTemplatesExpandScopesAndPersistTokenPolicy() {
        long userId = createUser();
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        McpPersonalTokenService service = new McpPersonalTokenService(jdbc,
                new ObjectMapper().findAndRegisterModules(), currentUser, mock(AuthService.class));
        McpPersonalTokenService.CreatedToken created = service.create(new McpPersonalTokenService.CreateToken(
                "完整工作台", Set.of(), Set.of(), Instant.now().plus(1, ChronoUnit.DAYS),
                "FULL_WORKSPACE", "DISABLED", 300));
        assertEquals("FULL_WORKSPACE", created.token().permissionTemplate());
        assertEquals("DISABLED", created.token().highRiskPolicy());
        assertEquals(300, created.token().rateLimitPerMinute());
        assertTrue(created.token().scopes().contains(McpPersonalTokenService.LEDGER_COMMIT));
        assertTrue(created.token().scopes().contains(McpPersonalTokenService.WORKTIME_PREPARE));
    }

    @Test
    void purgesOnlyOwnedRevokedPatAndKeepsAuditRows() {
        long userId = createUser();
        long otherUserId = createUser();
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        McpPersonalTokenService service = new McpPersonalTokenService(jdbc,
                new ObjectMapper().findAndRegisterModules(), currentUser, mock(AuthService.class));
        McpPersonalTokenService.CreatedToken created = service.create(new McpPersonalTokenService.CreateToken(
                "待删除", Set.of(McpPersonalTokenService.WORKTIME_READ), Set.of(),
                Instant.now().plus(1, ChronoUnit.DAYS)));

        assertThrows(IllegalArgumentException.class, () -> service.purge(created.token().id()));
        service.revoke(created.token().id());
        jdbc.update("""
                INSERT INTO mcp_tool_call(token_id,user_id,tool_name,status,duration_ms)
                VALUES(?,?,'worktime.records.search','SUCCESS',10)
                """, created.token().id(), userId);
        jdbc.update("""
                INSERT INTO mcp_protocol_event(user_id,token_id,event_type,status)
                VALUES(?,?,'mcp.request','SUCCESS')
                """, userId, created.token().id());
        service.purge(created.token().id());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_personal_token WHERE id=?",
                Integer.class, created.token().id()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_tool_call WHERE user_id=? AND token_id IS NULL",
                Integer.class, userId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM mcp_protocol_event WHERE user_id=? AND token_id IS NULL",
                Integer.class, userId));

        String otherTokenId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO mcp_personal_token(id,user_id,name,token_hash,token_hint,scopes,revoked_at)
                VALUES(?,?,?,SHA2(?,256),'wbt_other','[\"mcp:worktime:read\"]',CURRENT_TIMESTAMP)
                """, otherTokenId, otherUserId, "其他用户", otherTokenId);
        assertThrows(IllegalArgumentException.class, () -> service.purge(otherTokenId));

        String oauthTokenId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO mcp_personal_token(id,user_id,name,token_hash,token_hint,scopes,token_type,revoked_at)
                VALUES(?,?,?,SHA2(?,256),'oauth_other','[\"mcp:worktime:read\"]','OAUTH',CURRENT_TIMESTAMP)
                """, oauthTokenId, userId, "OAuth", oauthTokenId);
        assertThrows(IllegalArgumentException.class, () -> service.purge(oauthTokenId));
    }

    private long createUser() {
        String username = "mcp-token-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'MCP')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
