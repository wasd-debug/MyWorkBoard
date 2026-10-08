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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
