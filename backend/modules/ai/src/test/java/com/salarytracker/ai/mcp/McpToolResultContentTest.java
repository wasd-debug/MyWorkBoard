package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ledger.LedgerAccountListTool;
import com.salarytracker.ai.tool.ledger.LedgerBooksListTool;
import com.salarytracker.ai.tool.ledger.LedgerCategoryListTool;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerModels.Account;
import com.salarytracker.ledger.LedgerModels.Book;
import com.salarytracker.ledger.LedgerModels.Category;
import com.salarytracker.ledger.LedgerModels.CategoryKind;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class McpToolResultContentTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final LedgerBookService books = mock(LedgerBookService.class);
    private final CurrentUserResolver users = mock(CurrentUserResolver.class);
    private final McpPersonalTokenService tokens = mock(McpPersonalTokenService.class);
    private final McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
    private final McpServerConfiguration configuration = new McpServerConfiguration();
    private final DomainToolRegistry registry = new DomainToolRegistry(users, List.of(
            new LedgerBooksListTool(books, mapper), new LedgerAccountListTool(books, mapper),
            new LedgerCategoryListTool(books, mapper)));
    private McpPersonalTokenService.AuthenticatedToken token;

    @BeforeEach
    void setUp() {
        var user = new CurrentUser(7L, "alice", "Alice", Set.of("ROLE_USER", "ledger:read"));
        token = new McpPersonalTokenService.AuthenticatedToken("token-1", "test", user,
                Set.of(McpPersonalTokenService.LEDGER_READ), Set.of("book-1"));
        when(users.required()).thenReturn(user);
        when(exchange.transportContext()).thenReturn(McpTransportContext.create(
                Map.of(McpServerConfiguration.TOKEN_CONTEXT_KEY, token)));
    }

    @Test
    void textOnlyClientCanReadEveryAccountIdAndEscapedName() throws Exception {
        when(books.accounts("book-1", false)).thenReturn(List.of(
                new Account("account-1", "现金\"备用\"\n钱包", "wallet", "ASSET", "CNY",
                        BigDecimal.ZERO, new BigDecimal("120.50"), false, 3L, "2026-10-08"),
                new Account("account-2", "银行卡", "bank", "ASSET", "CNY",
                        BigDecimal.ZERO, BigDecimal.TEN, false, 4L, "2026-10-08")));

        var result = call("ledger.account.list", Map.of("bookId", "book-1"));
        JsonNode payload = textPayload(result, "当前账本有 2 个账户");

        assertEquals("account-1", payload.path("structuredContent").path(0).path("id").asText());
        assertEquals("现金\"备用\"\n钱包", payload.path("structuredContent").path(0).path("name").asText());
        assertEquals("account-2", payload.path("structuredContent").path(1).path("id").asText());
        verify(tokens).requireBook(token, "book-1");
        verify(books).accounts("book-1", false);
    }

    @Test
    void textOnlyClientCanReadCategoryIdsKindsAndParentRelations() throws Exception {
        when(books.categories("book-1", false)).thenReturn(List.of(
                new Category("parent-1", "餐饮", "tag", CategoryKind.EXPENSE, null,
                        "#fff", false, 1L, "2026-10-08"),
                new Category("category-1", "午餐", "tag", CategoryKind.EXPENSE, "parent-1",
                        "#fff", false, 2L, "2026-10-08")));

        var result = call("ledger.category.list", Map.of("bookId", "book-1"));
        JsonNode items = textPayload(result, "当前账本有 2 个分类").path("structuredContent");

        assertEquals("parent-1", items.path(0).path("id").asText());
        assertEquals("category-1", items.path(1).path("id").asText());
        assertEquals("EXPENSE", items.path(1).path("kind").asText());
        assertEquals("parent-1", items.path(1).path("parentId").asText());
    }

    @Test
    void bookListTextContainsOnlyBooksAllowedByToken() throws Exception {
        when(books.books()).thenReturn(List.of(book("book-1"), book("book-2")));

        var result = call("ledger.books.list", Map.of());
        JsonNode items = textPayload(result, "当前可访问 2 个账本").path("structuredContent");

        assertEquals(1, items.size());
        assertEquals("book-1", items.path(0).path("id").asText());
        assertFalse(((McpSchema.TextContent) result.content().get(0)).text().contains("book-2"));
    }

    @Test
    void emptyListRemainsAnExplicitJsonArrayForTextOnlyClients() throws Exception {
        when(books.accounts("book-1", false)).thenReturn(List.of());

        JsonNode items = textPayload(call("ledger.account.list", Map.of("bookId", "book-1")),
                "当前账本有 0 个账户").path("structuredContent");

        assertTrue(items.isArray());
        assertEquals(0, items.size());
    }

    @Test
    void deniedBookReturnsNoResourceDataInEitherRepresentation() {
        doThrow(new SecurityException("无权访问账本")).when(tokens).requireBook(token, "book-2");

        var result = call("ledger.account.list", Map.of("bookId", "book-2"));

        assertTrue(result.isError());
        assertEquals("无权访问账本", ((McpSchema.TextContent) result.content().get(0)).text());
        assertFalse(mapper.valueToTree(result.structuredContent()).has("structuredContent"));
        verifyNoInteractions(books);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private McpSchema.CallToolResult call(String name, Map<String, Object> arguments) {
        var specification = configuration.domainSpecifications(registry, tokens, mapper,
                        mock(McpExternalActionService.class), Set.of(McpPersonalTokenService.LEDGER_READ))
                .stream().filter(item -> name.equals(item.tool().name())).findFirst().orElseThrow();
        return specification.callHandler().apply(exchange, new McpSchema.CallToolRequest(name, arguments));
    }

    private JsonNode textPayload(McpSchema.CallToolResult result, String summary) throws Exception {
        assertFalse(result.isError());
        String text = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(text.startsWith(summary + "\n"));
        JsonNode payload = mapper.readTree(text.substring(text.indexOf('\n') + 1));
        assertEquals(mapper.readTree(mapper.writeValueAsString(result.structuredContent())), payload);
        assertEquals("completed", payload.path("status").asText());
        assertEquals(summary, payload.path("summary").asText());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        return payload;
    }

    private Book book(String id) {
        return new Book(id, "日常账本", "CNY", 7L, 1L, false,
                "OWNER", "账本主人", List.of("TRANSACTION_ANY_WRITE"), 1L, 12L, "2026-10-08");
    }
}
