package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ledger.LedgerAccountListTool;
import com.salarytracker.ai.tool.ledger.LedgerCategoryListTool;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.salarytracker.ai.mcp.McpPersonalTokenService.LEDGER_COMMIT;
import static com.salarytracker.ai.mcp.McpPersonalTokenService.LEDGER_PREPARE;
import static com.salarytracker.ai.mcp.McpPersonalTokenService.LEDGER_READ;
import static com.salarytracker.ai.mcp.McpPersonalTokenService.WORKTIME_PREPARE;
import static com.salarytracker.ai.mcp.McpPersonalTokenService.WORKTIME_READ;
import static com.salarytracker.ai.mcp.McpPersonalTokenService.TASK_COMMIT;
import static com.salarytracker.ai.mcp.McpPersonalTokenService.TASK_PREPARE;
import static com.salarytracker.ai.mcp.McpPersonalTokenService.TASK_READ;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class McpToolVisibilityTest {
    @Test
    void readScopeIncludesEveryLedgerAndWorktimeReadToolWithoutAllowListDrift() {
        Set<String> read = Set.of(LEDGER_READ, WORKTIME_READ);

        assertTrue(McpServerConfiguration.visible("ledger.account.list", read));
        assertTrue(McpServerConfiguration.visible("ledger.category.list", read));
        assertTrue(McpServerConfiguration.visible("ledger.merchant.list", read));
        assertTrue(McpServerConfiguration.visible("ledger.project.list", read));
        assertTrue(McpServerConfiguration.visible("ledger.schedule.list", read));
        assertTrue(McpServerConfiguration.visible("ledger.recycle.list", read));
        assertTrue(McpServerConfiguration.visible("worktime.settings.get", read));
        assertTrue(McpServerConfiguration.visible("worktime.records.search", read));
        assertFalse(McpServerConfiguration.visible("ledger.account.create.prepare", read));
        assertFalse(McpServerConfiguration.visible("worktime.settings.update.prepare", read));
    }

    @Test
    void advertisedCatalogIncludesRealAccountAndCategorySchemasWithLedgerReadOnly() {
        var mapper = new ObjectMapper();
        var books = mock(LedgerBookService.class);
        var registry = new DomainToolRegistry(mock(CurrentUserResolver.class), List.of(
                new LedgerAccountListTool(books, mapper), new LedgerCategoryListTool(books, mapper)));
        var configuration = new McpServerConfiguration();
        var specifications = configuration.domainSpecifications(registry, mock(McpPersonalTokenService.class),
                mapper, mock(McpExternalActionService.class), Set.of(LEDGER_READ));

        assertEquals(List.of("ledger.account.list", "ledger.category.list"),
                specifications.stream().map(specification -> specification.tool().name()).toList());
        for (var specification : specifications) {
            var tool = mapper.valueToTree(specification.tool());
            assertEquals("bookId", tool.path("inputSchema").path("required").get(0).asText());
            assertEquals("string", tool.path("inputSchema").path("properties").path("bookId").path("type").asText());
            assertTrue(tool.path("inputSchema").path("properties").has("includeHidden"));
            assertTrue(specification.tool().description().contains("不得编造"));
        }
        assertTrue(configuration.domainSpecifications(registry, mock(McpPersonalTokenService.class),
                mapper, mock(McpExternalActionService.class), Set.of(WORKTIME_READ)).isEmpty());
    }

    @Test
    void prepareScopeIncludesManagementLifecycleImportAndScheduleTools() {
        Set<String> prepare = Set.of(LEDGER_READ, WORKTIME_READ, LEDGER_PREPARE, WORKTIME_PREPARE);

        assertTrue(McpServerConfiguration.visible("ledger.account.create.prepare", prepare));
        assertTrue(McpServerConfiguration.visible("ledger.category.delete.prepare", prepare));
        assertTrue(McpServerConfiguration.visible("ledger.schedule.run.prepare", prepare));
        assertTrue(McpServerConfiguration.visible("ledger.recycle.restore.prepare", prepare));
        assertTrue(McpServerConfiguration.visible("ledger.import.preview.prepare", prepare));
        assertTrue(McpServerConfiguration.visible("ledger.export.prepare", prepare));
        assertTrue(McpServerConfiguration.visible("worktime.settings.update.prepare", prepare));
        assertFalse(McpServerConfiguration.visible("ledger.account.create.commit", prepare));
    }

    @Test
    void commitScopeDoesNotExposeDomainCommitTools() {
        Set<String> scopes = Set.of(LEDGER_READ, WORKTIME_READ, LEDGER_PREPARE, WORKTIME_PREPARE, LEDGER_COMMIT);

        assertFalse(McpServerConfiguration.visible("ledger.account.create.commit", scopes));
        assertFalse(McpServerConfiguration.visible("ledger.recycle.purge.commit", scopes));
        assertFalse(McpServerConfiguration.visible("worktime.record.create.commit", scopes));
    }

    @Test
    void taskToolsFollowTaskScopesAndNeverAdvertiseDomainCommit() {
        assertTrue(McpServerConfiguration.visible("task.list.query", Set.of(TASK_READ)));
        assertTrue(McpServerConfiguration.visible("habit.checkin.query", Set.of(TASK_READ)));
        assertTrue(McpServerConfiguration.visible("focus.stats.query", Set.of(TASK_READ)));
        assertFalse(McpServerConfiguration.visible("task.create.prepare", Set.of(TASK_READ)));
        assertTrue(McpServerConfiguration.visible("task.create.prepare", Set.of(TASK_READ, TASK_PREPARE)));
        assertFalse(McpServerConfiguration.visible("task.create.commit", Set.of(TASK_READ, TASK_PREPARE, TASK_COMMIT)));
        // 工时 scope 不应放行任务工具，任务 scope 也不应放行工时工具。
        assertFalse(McpServerConfiguration.visible("task.today", Set.of(WORKTIME_READ)));
        assertFalse(McpServerConfiguration.visible("worktime.records.search", Set.of(TASK_READ)));
    }

    @Test
    void requiredScopeFollowsNamespaceInsteadOfFallingBackToWorktime() {
        assertEquals(TASK_READ, McpServerConfiguration.requiredScope("task.list.query"));
        assertEquals(TASK_READ, McpServerConfiguration.requiredScope("task.query"));
        assertEquals(TASK_READ, McpServerConfiguration.requiredScope("habit.checkin.query"));
        assertEquals(TASK_READ, McpServerConfiguration.requiredScope("habit.stats"));
        assertEquals(TASK_READ, McpServerConfiguration.requiredScope("focus.stats.query"));
        assertEquals(TASK_READ, McpServerConfiguration.requiredScope("countdown.list"));
        assertEquals(TASK_PREPARE, McpServerConfiguration.requiredScope("task.create.prepare"));
        assertEquals(WORKTIME_READ, McpServerConfiguration.requiredScope("worktime.records.search"));
        assertEquals(LEDGER_PREPARE, McpServerConfiguration.requiredScope("ledger.account.create.prepare"));
        assertNull(McpServerConfiguration.requiredScope("agent.action.commit"));
    }

    /**
     * 回归：requireScope 曾经把 task/habit/focus 一律判成工时 scope，导致只授予
     * mcp:task:read 的 Token 能列出工具却调用全部 403。新增的 P1 只读工具同样要覆盖。
     */
    @Test
    void readOnlyTaskTokenCanInvokeEveryAdvertisedTaskTool() {
        Set<String> taskOnly = Set.of(TASK_READ);
        for (String name : List.of("task.query", "task.list.query", "task.get", "task.search", "task.calendar",
                "task.today", "task.overdue", "task.stats.summary", "task.settings.get", "task.filters.list",
                "task.lists.list", "task.tags.list",
                "habit.list", "habit.stats", "habit.calendar", "habit.checkin.query",
                "focus.stats.query", "focus.sessions.list", "focus.current", "countdown.list")) {
            assertTrue(McpServerConfiguration.visible(name, taskOnly), name + " 应对任务 scope 可见");
            String required = McpServerConfiguration.requiredScope(name);
            assertTrue(taskOnly.contains(required), name + " 需要 " + required + "，不应降级到工时 scope");
        }
    }
}
