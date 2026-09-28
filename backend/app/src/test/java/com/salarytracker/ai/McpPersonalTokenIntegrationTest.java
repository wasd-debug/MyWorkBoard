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
                McpPersonalTokenService.LEDGER_PREPARE, McpPersonalTokenService.WORKTIME_PREPARE),
                Set.of(), Instant.now().plus(7, ChronoUnit.DAYS)));

        assertTrue(created.rawToken().startsWith("wbt_"));
        assertFalse(created.token().tokenHint().contains(created.rawToken()));
        assertEquals(64, jdbc.queryForObject("SELECT LENGTH(token_hash) FROM mcp_personal_token WHERE id=?",
                Integer.class, created.token().id()));
        assertEquals(created.token().id(), service.authenticate(created.rawToken()).id());
        assertEquals("本地 Codex", service.authenticate(created.rawToken()).name());
        assertTrue(service.authenticate(created.rawToken()).scopes().contains(McpPersonalTokenService.LEDGER_PREPARE));
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
                new McpPersonalTokenService.CreateToken("越权", Set.of("mcp:ledger:commit"), Set.of(),
                        Instant.now().plus(1, ChronoUnit.DAYS))));
        assertThrows(IllegalArgumentException.class, () -> service.create(
                new McpPersonalTokenService.CreateToken("过期", Set.of(McpPersonalTokenService.WORKTIME_READ),
                        Set.of(), Instant.now().minus(1, ChronoUnit.MINUTES))));
    }

    private long createUser() {
        String username = "mcp-token-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'MCP')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
