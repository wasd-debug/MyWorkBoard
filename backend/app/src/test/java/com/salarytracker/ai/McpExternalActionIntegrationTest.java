package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.JdbcPendingActionRepository;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.mcp.McpExternalActionService;
import com.salarytracker.ai.mcp.McpPersonalTokenService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ledger.LedgerTransactionCreateCommitTool;
import com.salarytracker.ai.tool.worktime.WorktimeRecordCreateCommitTool;
import com.salarytracker.identity.AuthService;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.ledger.LedgerAuditService;
import com.salarytracker.ledger.LedgerBookAccess;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Account;
import com.salarytracker.ledger.LedgerModels.AccountCommand;
import com.salarytracker.ledger.LedgerModels.Category;
import com.salarytracker.ledger.LedgerModels.CategoryCommand;
import com.salarytracker.ledger.LedgerModels.CategoryKind;
import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.worktime.WorktimeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpExternalActionIntegrationTest extends MySqlIntegrationTestSupport {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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

    @Test
    void commitsApprovedR2LedgerActionOnceAndPublishesProjectionSync() {
        long userId = createUser();
        CurrentUser user = new CurrentUser(userId, "mcp-commit-user", "MCP Commit",
                Set.of("ledger:read", "ledger:write"));
        authenticate(user);
        CurrentUserResolver currentUser = new CurrentUserResolver();
        LedgerBookAccess access = new LedgerBookAccess(jdbc, currentUser);
        String bookId = access.ensureDefaultBook();
        LedgerAuditService audit = new LedgerAuditService(jdbc, mapper, access);
        LedgerBookService books = new LedgerBookService(jdbc, mapper, access, audit);
        LedgerTransactionService transactions = new LedgerTransactionService(jdbc, mapper, access, books, audit);
        Account account = books.createAccount(bookId, new AccountCommand(null, "现金", "wallet", "cash",
                "CNY", BigDecimal.ZERO, false), "mcp-commit-account");
        Category primary = books.createCategory(bookId, new CategoryCommand(null, "餐饮", "food",
                CategoryKind.EXPENSE, null, "#e18b41", false), "mcp-commit-primary");
        Category secondary = books.createCategory(bookId, new CategoryCommand(null, "午餐", "food",
                CategoryKind.EXPENSE, primary.id(), "#e18b41", false), "mcp-commit-secondary");

        AuthService auth = mock(AuthService.class);
        when(auth.loadUser(userId)).thenReturn(user);
        McpPersonalTokenService tokens = new McpPersonalTokenService(jdbc, mapper, currentUser, auth);
        McpPersonalTokenService.CreatedToken created = tokens.create(new McpPersonalTokenService.CreateToken(
                "Codex Commit", Set.of(McpPersonalTokenService.LEDGER_PREPARE,
                McpPersonalTokenService.LEDGER_COMMIT), Set.of(bookId),
                Instant.now().plus(1, ChronoUnit.DAYS)));
        McpPersonalTokenService.AuthenticatedToken token = tokens.authenticate(created.rawToken());
        PendingActionService actions = new PendingActionService(new JdbcPendingActionRepository(jdbc), mapper,
                new InteractionPolicy());
        McpExternalActionService external = new McpExternalActionService(jdbc, mapper, actions);
        ToolDefinition prepare = definition("ledger.transaction.create.prepare", ToolRisk.R2);
        DomainTool commit = new LedgerTransactionCreateCommitTool(transactions, actions, currentUser, mapper);
        DomainToolRegistry registry = new DomainToolRegistry(currentUser, List.of(tool(prepare), commit));
        ObjectNode input = mapper.createObjectNode();
        input.put("bookId", bookId).put("accountId", account.id()).put("categoryId", secondary.id())
                .put("kind", "EXPENSE").put("amount", 36.50).put("occurredOn", LocalDate.now().toString())
                .put("note", "MCP commit 集成测试");
        PendingAction action = actions.prepare(userId, prepare, input, null, false, Duration.ofMinutes(15));
        ToolResult prepared = ToolResult.needsConfirmation("待确认记账", mapper.createObjectNode(),
                action.id(), action.expiresAt().toString());
        McpExternalActionService.Binding binding = external.bind(token, prepare, prepared,
                "Codex", "http://localhost");
        String confirmationToken = binding.confirmationUrl().substring(binding.confirmationUrl().indexOf("token=") + 6);
        external.approveConfirmation(confirmationToken, userId);

        McpExternalActionService.CommitResult first = external.commitForToken(token, action.id(), registry, tokens);
        McpExternalActionService.CommitResult replay = external.commitForToken(token, action.id(), registry, tokens);

        assertEquals("COMPLETED", first.status().name());
        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(ActionStatus.COMPLETED, actions.getForUser(action.id(), userId).status());
        assertEquals(1L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM ledger_transaction
                WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?) AND client_op_id=?
                """, Long.class, bookId, action.id()));
        assertEquals(1L, jdbc.queryForObject("""
                SELECT COUNT(*) FROM ledger_sync_oplog
                WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?) AND op_id=?
                """, Long.class, bookId, action.id()));
        assertEquals("COMPLETED", external.getForToken(token, action.id()).commitStatus());
    }

    @Test
    void rejectsCommitWithoutScopeBeforeApprovalAndForR3Action() {
        Fixture fixture = fixture();
        DomainToolRegistry r2Registry = registry(fixture, fixture.definition, ToolRisk.R2, new AtomicInteger());
        PendingAction waiting = fixture.actions.prepare(fixture.user.id(), fixture.definition,
                mapper.createObjectNode().put("amount", 10), null, false, Duration.ofMinutes(15));
        ToolResult waitingResult = ToolResult.needsConfirmation("待确认", mapper.createObjectNode(),
                waiting.id(), waiting.expiresAt().toString());
        fixture.external.bind(fixture.token, fixture.definition, waitingResult, "Codex", "http://localhost");
        assertThrows(IllegalStateException.class,
                () -> fixture.external.commitForToken(fixture.token, waiting.id(), r2Registry, fixture.tokens));

        McpPersonalTokenService.AuthenticatedToken noCommit = new McpPersonalTokenService.AuthenticatedToken(
                fixture.token.id(), fixture.token.name(), fixture.user,
                Set.of(McpPersonalTokenService.LEDGER_PREPARE), Set.of());
        fixture.actions.approve(waiting.id(), fixture.user.id());
        assertThrows(SecurityException.class,
                () -> fixture.external.commitForToken(noCommit, waiting.id(), r2Registry, fixture.tokens));

        ToolDefinition r3 = definition("ledger.transaction.delete.prepare", ToolRisk.R3);
        PendingAction dangerous = fixture.actions.prepare(fixture.user.id(), r3,
                mapper.createObjectNode().put("transactionId", "tx"), 1L, false, Duration.ofMinutes(15));
        ToolResult dangerousResult = ToolResult.needsConfirmation("待确认删除", mapper.createObjectNode(),
                dangerous.id(), dangerous.expiresAt().toString());
        fixture.external.bind(fixture.token, r3, dangerousResult, "Codex", "http://localhost");
        fixture.actions.approve(dangerous.id(), fixture.user.id());
        assertThrows(SecurityException.class, () -> fixture.external.commitForToken(
                fixture.token, dangerous.id(), registry(fixture, r3, ToolRisk.R3, new AtomicInteger()), fixture.tokens));
    }

    @Test
    void commitsApprovedWorktimeCreateOnce() {
        long userId = createUser();
        CurrentUser user = new CurrentUser(userId, "mcp-worktime-user", "MCP Worktime",
                Set.of("worktime:read", "worktime:write"));
        authenticate(user);
        CurrentUserResolver currentUser = new CurrentUserResolver();
        AuthService auth = mock(AuthService.class);
        when(auth.loadUser(userId)).thenReturn(user);
        McpPersonalTokenService tokens = new McpPersonalTokenService(jdbc, mapper, currentUser, auth);
        McpPersonalTokenService.CreatedToken created = tokens.create(new McpPersonalTokenService.CreateToken(
                "WorkBuddy Commit", Set.of(McpPersonalTokenService.WORKTIME_PREPARE,
                McpPersonalTokenService.WORKTIME_COMMIT), Set.of(), Instant.now().plus(1, ChronoUnit.DAYS)));
        McpPersonalTokenService.AuthenticatedToken token = tokens.authenticate(created.rawToken());
        PendingActionService actions = new PendingActionService(new JdbcPendingActionRepository(jdbc), mapper,
                new InteractionPolicy());
        McpExternalActionService external = new McpExternalActionService(jdbc, mapper, actions);
        ToolDefinition prepare = definition("worktime.record.create.prepare", ToolRisk.R2, "worktime:write");
        DomainTool commit = new WorktimeRecordCreateCommitTool(
                new WorktimeService(jdbc, mapper, currentUser), actions, currentUser, mapper);
        DomainToolRegistry registry = new DomainToolRegistry(currentUser, List.of(tool(prepare), commit));
        String date = LocalDate.now().minusDays(14).toString();
        ObjectNode input = mapper.createObjectNode().put("date", date).put("start", "09:00")
                .put("end", "18:00").put("rest", 30).put("note", "MCP 工时提交测试");
        PendingAction action = actions.prepare(userId, prepare, input, null, false, Duration.ofMinutes(15));
        ToolResult prepared = ToolResult.needsConfirmation("待确认工时", mapper.createObjectNode(),
                action.id(), action.expiresAt().toString());
        McpExternalActionService.Binding binding = external.bind(token, prepare, prepared,
                "WorkBuddy", "http://localhost");
        String confirmationToken = binding.confirmationUrl().substring(binding.confirmationUrl().indexOf("token=") + 6);
        external.approveConfirmation(confirmationToken, userId);

        assertFalse(external.commitForToken(token, action.id(), registry, tokens).replayed());
        assertTrue(external.commitForToken(token, action.id(), registry, tokens).replayed());
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_record WHERE user_id=? AND date=? AND deleted=FALSE",
                Long.class, userId, date));
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
                "Codex", Set.of(McpPersonalTokenService.LEDGER_PREPARE, McpPersonalTokenService.LEDGER_COMMIT), Set.of(),
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

    private DomainToolRegistry registry(Fixture fixture, ToolDefinition prepare, ToolRisk commitRisk,
                                        AtomicInteger executions) {
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.required()).thenReturn(fixture.user);
        when(currentUser.id()).thenReturn(fixture.user.id());
        ToolDefinition commit = definition(prepare.name().replace(".prepare", ".commit"), commitRisk);
        DomainTool commitTool = new DomainTool() {
            @Override public ToolDefinition definition() { return commit; }
            @Override public ToolResult execute(com.fasterxml.jackson.databind.JsonNode input) {
                executions.incrementAndGet();
                fixture.actions.beginCommit(input.path("actionId").asText(), fixture.user.id());
                fixture.actions.transition(input.path("actionId").asText(), fixture.user.id(), ActionStatus.COMPLETED);
                return ToolResult.completed("操作已提交", mapper.createObjectNode().put("saved", true));
            }
        };
        return new DomainToolRegistry(currentUser, List.of(tool(prepare), commitTool));
    }

    private DomainTool tool(ToolDefinition definition) {
        return new DomainTool() {
            @Override public ToolDefinition definition() { return definition; }
            @Override public ToolResult execute(com.fasterxml.jackson.databind.JsonNode input) {
                throw new UnsupportedOperationException("测试不执行 prepare tool");
            }
        };
    }

    private ToolDefinition definition(String name, ToolRisk risk) {
        return definition(name, risk, "ledger:write");
    }

    private ToolDefinition definition(String name, ToolRisk risk, String authority) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        schema.putObject("properties").putObject("actionId").put("type", "string");
        return new ToolDefinition(name, 1, "MCP 测试工具", risk, Set.of(authority), schema);
    }

    private void authenticate(CurrentUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
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
