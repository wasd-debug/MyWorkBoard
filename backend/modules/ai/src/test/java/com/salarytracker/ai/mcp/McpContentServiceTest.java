package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.identity.CurrentUser;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpContentServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DomainToolRegistry registry = mock(DomainToolRegistry.class);
    private final McpPersonalTokenService tokens = mock(McpPersonalTokenService.class);
    private final McpContentService service = new McpContentService(registry, tokens, mapper);
    private final McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

    @BeforeEach
    void setUp() {
        var user = new CurrentUser(7L, "alice", "Alice", Set.of("ROLE_USER", "ledger:read", "worktime:read"));
        var token = new McpPersonalTokenService.AuthenticatedToken("token-1", "test", user,
                Set.of(McpPersonalTokenService.LEDGER_READ, McpPersonalTokenService.WORKTIME_READ),
                Set.of("book-1"));
        when(exchange.transportContext()).thenReturn(McpTransportContext.create(
                Map.of(McpServerConfiguration.TOKEN_CONTEXT_KEY, token)));
    }

    @Test
    void listsOnlyResourcesAndTemplatesAllowedByScopes() {
        var resources = service.resources(Set.of(McpPersonalTokenService.LEDGER_READ));
        var templates = service.resourceTemplates(Set.of(McpPersonalTokenService.LEDGER_READ));
        var prompts = service.prompts(Set.of(McpPersonalTokenService.LEDGER_READ));

        assertEquals(Set.of("workbench://help/mcp", "workbench://help/tools",
                        "workbench://help/scopes", "workbench://ledger/books"),
                resources.stream().map(item -> item.resource().uri()).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("workbench://ledger/{bookId}/overview",
                        "workbench://ledger/{bookId}/reports/{period}"),
                templates.stream().map(item -> item.resourceTemplate().uriTemplate())
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("monthly-review", "ledger-summary"),
                prompts.stream().map(item -> item.prompt().name()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void readsLedgerOverviewThroughDomainToolAndChecksBookScope() {
        when(registry.invoke(eq("ledger.overview"), any())).thenReturn(
                ToolResult.completed("ok", mapper.createObjectNode().put("balance", 120)));
        var specification = findTemplate("workbench://ledger/{bookId}/overview");

        McpSchema.ReadResourceResult result = specification.readHandler().apply(exchange,
                new McpSchema.ReadResourceRequest("workbench://ledger/book-1/overview"));

        assertTrue(((McpSchema.TextResourceContents) result.contents().get(0)).text().contains("120"));
        verify(tokens).requireBook(any(), eq("book-1"));
        verify(registry).invoke(eq("ledger.overview"), any());
    }

    @Test
    void rejectsWorktimeRangeLongerThan366Days() {
        var specification = service.resourceTemplates(Set.of(McpPersonalTokenService.WORKTIME_READ)).get(0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                specification.readHandler().apply(exchange, new McpSchema.ReadResourceRequest(
                        "workbench://worktime/records/2025-01-01/2026-01-03")));

        assertEquals("MCP 单次查询日期跨度不能超过 366 天", exception.getMessage());
    }

    @Test
    void buildsPromptWithoutExecutingBusinessWrite() {
        McpServerFeatures.SyncPromptSpecification prompt = service.prompts(
                        Set.of(McpPersonalTokenService.LEDGER_READ)).stream()
                .filter(item -> "monthly-review".equals(item.prompt().name())).findFirst().orElseThrow();

        McpSchema.GetPromptResult result = prompt.promptHandler().apply(exchange,
                new McpSchema.GetPromptRequest("monthly-review",
                        Map.of("bookId", "book-1", "period", "2026-09")));

        String text = ((McpSchema.TextContent) result.messages().get(0).content()).text();
        assertTrue(text.contains("workbench://ledger/book-1/reports/2026-09"));
        verify(tokens).requireBook(any(), eq("book-1"));
    }

    private McpServerFeatures.SyncResourceTemplateSpecification findTemplate(String uriTemplate) {
        return service.resourceTemplates(Set.of(McpPersonalTokenService.LEDGER_READ)).stream()
                .filter(item -> uriTemplate.equals(item.resourceTemplate().uriTemplate()))
                .findFirst().orElseThrow();
    }
}
