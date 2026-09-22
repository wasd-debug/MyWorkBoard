package com.salarytracker.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ForbiddenException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DomainToolRegistryTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);

    @Test
    void listsDefinitionsInStableNameOrder() {
        DomainToolRegistry registry = new DomainToolRegistry(currentUser, List.of(
                tool("worktime.settings.get", Set.of("worktime:read")),
                tool("ledger.books.list", Set.of("ledger:read"))));

        assertEquals(List.of("ledger.books.list", "worktime.settings.get"),
                registry.definitions().stream().map(ToolDefinition::name).toList());
    }

    @Test
    void acceptsDocumentedTwoSegmentToolNames() {
        ToolDefinition definition = definition("ledger.overview", Set.of("ledger:read"));

        assertEquals("ledger.overview", definition.name());
    }

    @Test
    void rejectsDuplicateToolNamesAtStartup() {
        DomainTool first = tool("ledger.books.list", Set.of());
        DomainTool second = tool("ledger.books.list", Set.of());

        assertThrows(IllegalStateException.class,
                () -> new DomainToolRegistry(currentUser, List.of(first, second)));
    }

    @Test
    void checksCurrentUserAuthoritiesBeforeExecution() {
        DomainTool tool = mock(DomainTool.class);
        when(tool.definition()).thenReturn(definition("ledger.books.list", Set.of("ledger:read")));
        when(currentUser.required()).thenReturn(new CurrentUser(9L, "viewer", null, Set.of("worktime:read")));
        DomainToolRegistry registry = new DomainToolRegistry(currentUser, List.of(tool));

        assertThrows(ForbiddenException.class,
                () -> registry.invoke("ledger.books.list", mapper.createObjectNode()));
        verify(tool, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnknownInputFieldsBeforeExecution() {
        DomainTool tool = mock(DomainTool.class);
        when(tool.definition()).thenReturn(definition("ledger.books.list", Set.of("ledger:read")));
        when(currentUser.required()).thenReturn(new CurrentUser(9L, "viewer", null, Set.of("ledger:read")));
        DomainToolRegistry registry = new DomainToolRegistry(currentUser, List.of(tool));

        assertThrows(IllegalArgumentException.class,
                () -> registry.invoke("ledger.books.list", mapper.createObjectNode().put("userId", 99)));
        verify(tool, never()).execute(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void invokesAuthorizedTool() {
        DomainTool tool = mock(DomainTool.class);
        ToolDefinition definition = definition("ledger.books.list", Set.of("ledger:read"));
        ToolResult expected = ToolResult.completed("ok", mapper.createArrayNode());
        when(tool.definition()).thenReturn(definition);
        when(tool.execute(org.mockito.ArgumentMatchers.any())).thenReturn(expected);
        when(currentUser.required()).thenReturn(new CurrentUser(9L, "viewer", null, Set.of("ledger:read")));
        DomainToolRegistry registry = new DomainToolRegistry(currentUser, List.of(tool));

        assertEquals(expected, registry.invoke("ledger.books.list", null));
    }

    @Test
    void listsOnlyToolsAllowedForCurrentUser() {
        when(currentUser.required()).thenReturn(new CurrentUser(9L, "viewer", null, Set.of("ledger:read")));
        DomainToolRegistry registry = new DomainToolRegistry(currentUser, List.of(
                tool("ledger.books.list", Set.of("ledger:read")),
                tool("worktime.settings.get", Set.of("worktime:read"))));

        assertEquals(List.of("ledger.books.list"), registry.definitionsForCurrentUser().stream()
                .map(ToolDefinition::name).toList());
    }

    private DomainTool tool(String name, Set<String> authorities) {
        ToolDefinition definition = definition(name, authorities);
        return new DomainTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public ToolResult execute(JsonNode input) {
                return ToolResult.completed("ok", mapper.createObjectNode());
            }
        };
    }

    private ToolDefinition definition(String name, Set<String> authorities) {
        return new ToolDefinition(name, 1, "test tool", ToolRisk.R1, authorities, ToolSchemas.object(mapper));
    }
}
