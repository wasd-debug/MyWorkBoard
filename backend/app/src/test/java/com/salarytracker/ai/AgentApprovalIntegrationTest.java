package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.JdbcPendingActionRepository;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.approval.AgentApproval;
import com.salarytracker.ai.approval.AgentApprovalService;
import com.salarytracker.ai.approval.ApprovalStatus;
import com.salarytracker.ai.approval.JdbcAgentApprovalRepository;
import com.salarytracker.ai.approval.LedgerImportApprovalExecutor;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.ledger.LedgerAuditService;
import com.salarytracker.ledger.LedgerBookAccess;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerImportService;
import com.salarytracker.ledger.LedgerModels.Account;
import com.salarytracker.ledger.LedgerModels.AccountCommand;
import com.salarytracker.ledger.LedgerModels.Category;
import com.salarytracker.ledger.LedgerModels.CategoryCommand;
import com.salarytracker.ledger.LedgerModels.CategoryKind;
import com.salarytracker.ledger.LedgerModels.ImportPreview;
import com.salarytracker.ledger.LedgerModels.Member;
import com.salarytracker.ledger.LedgerTransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentApprovalIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void r4ApprovalImportsOnceAndKeepsApprovalUserScoped() throws Exception {
        Fixture fixture = fixture("agent-import-approval");
        String csv = "交易类型,日期,一级分类,二级分类,收入/支出账户,金额,成员,商家,项目,备注\n" +
                "支出,2026-09-27,住房,房租,现金,29.90,,,,审批导入\n";
        ImportPreview preview = fixture.imports.preview(fixture.bookId,
                new MockMultipartFile("file", "approval.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)),
                "AUTO");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        CurrentUserResolver currentUser = new CurrentUserResolver();
        PendingActionService actions = new PendingActionService(new JdbcPendingActionRepository(jdbc), mapper,
                new InteractionPolicy());
        ToolDefinition tool = new ToolDefinition("ledger.import.confirm.prepare", 1, "导入确认",
                ToolRisk.R4, Set.of("ledger:import"), ToolSchemas.object(mapper));
        var input = mapper.createObjectNode().put("bookId", fixture.bookId)
                .put("batchId", preview.batchId()).put("duplicateStrategy", "SKIP");
        PendingAction action = actions.prepare(currentUser.id(), tool, input, null, false, Duration.ofMinutes(30));
        LedgerImportApprovalExecutor executor = new LedgerImportApprovalExecutor(
                fixture.imports, actions, currentUser, mapper);
        AgentApprovalService approvals = new AgentApprovalService(new JdbcAgentApprovalRepository(jdbc), actions,
                executor, currentUser, mapper);
        AgentApproval approval = approvals.create(action, fixture.bookId, "确认导入", mapper.valueToTree(preview));

        AgentApproval completed = approvals.approveAndExecute(approval.id());
        assertEquals(ApprovalStatus.COMPLETED, completed.status());
        assertEquals(1L, transactionCount(fixture.bookId));

        assertEquals(ApprovalStatus.COMPLETED, approvals.approveAndExecute(approval.id()).status());
        assertEquals(1L, transactionCount(fixture.bookId));

        long otherUser = createUser("agent-import-approval-other");
        authenticate(otherUser, "agent-import-approval-other");
        assertThrows(IllegalArgumentException.class, () -> approvals.get(approval.id()));
    }

    private Fixture fixture(String prefix) {
        long userId = createUser(prefix);
        authenticate(userId, prefix);
        CurrentUserResolver currentUser = new CurrentUserResolver();
        LedgerBookAccess access = new LedgerBookAccess(jdbc, currentUser);
        String bookId = access.ensureDefaultBook();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LedgerAuditService audit = new LedgerAuditService(jdbc, mapper, access);
        LedgerBookService books = new LedgerBookService(jdbc, mapper, access, audit);
        LedgerTransactionService transactions = new LedgerTransactionService(jdbc, mapper, access, books, audit);
        LedgerImportService imports = new LedgerImportService(jdbc, mapper, access, transactions);
        Account account = books.createAccount(bookId, new AccountCommand(null, "现金", "wallet", "cash",
                "CNY", BigDecimal.ZERO, false), prefix + "-account");
        Category primary = books.createCategory(bookId, new CategoryCommand(null, "住房", "home",
                CategoryKind.EXPENSE, null, "#e18b41", false), prefix + "-primary");
        Category secondary = books.createCategory(bookId, new CategoryCommand(null, "房租", "home",
                CategoryKind.EXPENSE, primary.id(), "#e18b41", false), prefix + "-secondary");
        Member member = books.members(bookId).stream().filter(value -> value.userId() == userId).findFirst().orElseThrow();
        return new Fixture(bookId, account.id(), secondary.id(), member.id(), imports);
    }

    private long createUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', ?)", username, prefix);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private void authenticate(long userId, String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(userId, username, username, Set.of("ledger:read", "ledger:write", "ledger:import")),
                null, List.of()));
    }

    private long transactionCount(String bookId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_transaction WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?)",
                Long.class, bookId);
    }

    private record Fixture(String bookId, String accountId, String categoryId, String memberId,
                           LedgerImportService imports) { }
}
