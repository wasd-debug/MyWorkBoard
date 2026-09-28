package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.JdbcPendingActionRepository;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.mcp.McpExternalActionService;
import com.salarytracker.ai.mcp.McpPersonalTokenService;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.identity.AuthService;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpExternalActionIntegrationTest extends MySqlIntegrationTestSupport {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void bindsApprovesAndQueriesActionWithoutWritingBusinessData() {
        Fixture fixture = fixture();
        long transactionsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transaction", Long.class);
        PendingAction action = fixture.actions.prepare(fixture.user.id(), fixture.definition,
                mapper.createObjectNode().put("amount", 88), null, false, Duration.ofMinutes(15));
        ToolResult prepared = ToolResult.needsConfirmation("待确认记账", mapper.createObjectNode()
                        .put("actionType", "ledger.transaction.create").set("preview",
                                mapper.createObjectNode().put("amount", 88)),
                action.id(), action.expiresAt().toString());

        McpExternalActionService.Binding binding = fixture.external.bind(fixture.token, fixture.definition,
                prepared, "Codex/1.0", "http://127.0.0.1:5173");
        assertTrue(binding.confirmationUrl().startsWith("http://127.0.0.1:5173/mcp/actions/confirm?token="));
        assertEquals(64, jdbc.queryForObject(
                "SELECT LENGTH(confirmation_token_hash) FROM mcp_external_action WHERE action_id=?",
                Integer.class, action.id()));
        assertEquals("WAITING_CONFIRMATION", fixture.external.getForToken(fixture.token, action.id()).status());

        String confirmationToken = binding.confirmationUrl().substring(binding.confirmationUrl().indexOf("token=") + 6);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.external.getForConfirmation(confirmationToken, fixture.user.id() + 1));
        assertEquals("APPROVED", fixture.external.approveConfirmation(confirmationToken, fixture.user.id()).status());
        assertEquals("APPROVED", fixture.external.approveConfirmation(confirmationToken, fixture.user.id()).status());
        assertEquals(transactionsBefore,
                jdbc.queryForObject("SELECT COUNT(*) FROM ledger_transaction", Long.class));
        assertEquals(1, fixture.external.listForToken(fixture.token, 20).size());
    }

    @Test
    void isolatesTokensCancelsActionsAndInvalidatesConfirmationAfterRevocation() {
        Fixture fixture = fixture();
        PendingAction action = fixture.actions.prepare(fixture.user.id(), fixture.definition,
                mapper.createObjectNode().put("amount", 12), null, false, Duration.ofMinutes(15));
        ToolResult prepared = ToolResult.needsConfirmation("待确认", mapper.createObjectNode(),
                action.id(), action.expiresAt().toString());
        McpExternalActionService.Binding binding = fixture.external.bind(fixture.token, fixture.definition,
                prepared, "WorkBuddy", "http://localhost");

        McpPersonalTokenService.AuthenticatedToken otherToken = new McpPersonalTokenService.AuthenticatedToken(
                UUID.randomUUID().toString(), "其他客户端", fixture.user,
                Set.of(McpPersonalTokenService.LEDGER_PREPARE), Set.of());
        assertThrows(IllegalArgumentException.class,
                () -> fixture.external.getForToken(otherToken, action.id()));
        assertEquals("CANCELLED", fixture.external.cancelForToken(fixture.token, action.id()).status());

        fixture.tokens.revoke(fixture.token.id());
        String confirmationToken = binding.confirmationUrl().substring(binding.confirmationUrl().indexOf("token=") + 6);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.external.getForConfirmation(confirmationToken, fixture.user.id()));
    }

    @Test
    void rejectsResolvedBookOutsideTokenScopeAndCancelsPreparedAction() {
        Fixture fixture = fixture();
        PendingAction action = fixture.actions.prepare(fixture.user.id(), fixture.definition,
                mapper.createObjectNode().put("bookId", "outside-book").put("amount", 12),
                null, false, Duration.ofMinutes(15));
        McpPersonalTokenService.AuthenticatedToken restricted = new McpPersonalTokenService.AuthenticatedToken(
                fixture.token.id(), fixture.token.name(), fixture.user,
                Set.of(McpPersonalTokenService.LEDGER_PREPARE), Set.of("allowed-book"));

        assertThrows(SecurityException.class,
                () -> fixture.external.requireActionBook(restricted, action.id(), fixture.tokens));
        assertEquals("CANCELLED", fixture.actions.getForUser(action.id(), fixture.user.id()).status().name());
    }

    @Test
    void confirmsActionWhenLegacyTokenTableUsesDifferentCollation() {
        jdbc.execute("""
                ALTER TABLE mcp_external_action
                MODIFY token_id VARCHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
                """);
        jdbc.execute("""
                ALTER TABLE mcp_personal_token
                MODIFY id CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL
                """);
        Fixture fixture = fixture();
        PendingAction action = fixture.actions.prepare(fixture.user.id(), fixture.definition,
                mapper.createObjectNode().put("amount", 21), null, false, Duration.ofMinutes(15));
        ToolResult prepared = ToolResult.needsConfirmation("待确认", mapper.createObjectNode(),
                action.id(), action.expiresAt().toString());
        McpExternalActionService.Binding binding = fixture.external.bind(fixture.token, fixture.definition,
                prepared, "Codex", "http://localhost");
        String confirmationToken = binding.confirmationUrl().substring(binding.confirmationUrl().indexOf("token=") + 6);

        assertEquals("WAITING_CONFIRMATION",
                fixture.external.getForConfirmation(confirmationToken, fixture.user.id()).status());
        assertEquals("APPROVED",
                fixture.external.approveConfirmation(confirmationToken, fixture.user.id()).status());
    }

    private Fixture fixture() {
        long userId = createUser();
        CurrentUser user = new CurrentUser(userId, "mcp-action-user", "MCP Action", Set.of("ledger:write"));
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        AuthService auth = mock(AuthService.class);
        when(currentUser.id()).thenReturn(userId);
        when(auth.loadUser(userId)).thenReturn(user);
        McpPersonalTokenService tokens = new McpPersonalTokenService(jdbc, mapper, currentUser, auth);
        McpPersonalTokenService.CreatedToken created = tokens.create(new McpPersonalTokenService.CreateToken(
                "Codex", Set.of(McpPersonalTokenService.LEDGER_PREPARE), Set.of(),
                Instant.now().plus(1, ChronoUnit.DAYS)));
        McpPersonalTokenService.AuthenticatedToken token = tokens.authenticate(created.rawToken());
        PendingActionService actions = new PendingActionService(new JdbcPendingActionRepository(jdbc), mapper,
                new InteractionPolicy());
        McpExternalActionService external = new McpExternalActionService(jdbc, mapper, actions);
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        schema.putObject("properties").putObject("amount").put("type", "number");
        ToolDefinition definition = new ToolDefinition("ledger.transaction.create.prepare", 2,
                "生成记账预览", ToolRisk.R2, Set.of("ledger:write"), schema);
        return new Fixture(user, tokens, token, actions, external, definition);
    }

    private long createUser() {
        String username = "mcp-action-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'MCP')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private record Fixture(CurrentUser user, McpPersonalTokenService tokens,
                           McpPersonalTokenService.AuthenticatedToken token, PendingActionService actions,
                           McpExternalActionService external, ToolDefinition definition) { }
}
