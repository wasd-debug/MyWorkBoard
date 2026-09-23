package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.session.AgentConversationService;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.platform.ai.LlmGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DomainToolRegistry tools = mock(DomainToolRegistry.class);
    private final LlmGateway model = mock(LlmGateway.class);
    private final AgentConversationService conversations = mock(AgentConversationService.class);

    @BeforeEach
    void setUpConversation() {
        when(conversations.open(nullable(String.class), any()))
                .thenReturn(new AgentConversationService.SessionContext("session-1", 7L, List.of()));
    }

    @Test
    void exposesAndExecutesOnlyAuthorizedReadTools() {
        ToolDefinition read = definition("ledger.books.list", ToolRisk.R1);
        ToolDefinition write = definition("worktime.record.create.prepare", ToolRisk.R2);
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(read, write));
        when(tools.invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any()))
                .thenReturn(ToolResult.completed("找到一个账本", mapper.createArrayNode().addObject().put("name", "日常账本")));
        when(model.agentTurn(any(), any()))
                .thenReturn(new LlmGateway.AgentTurn("", List.of(
                                new LlmGateway.AgentToolCall("call-1", "ledger__books__list", "{}")),
                                "deepseek", true))
                .thenReturn(new LlmGateway.AgentTurn("你有一个账本：**日常账本**。", List.of(),
                        "deepseek", true));

        LlmGateway.ChatResponse response = orchestrator().chat("我有哪些账本？");

        assertEquals("你有一个账本：**日常账本**。", response.content());
        assertEquals(1, response.toolExecutions().size());
        assertEquals("ledger.books.list", response.toolExecutions().get(0).name());
        assertEquals("COMPLETED", response.toolExecutions().get(0).status());
        assertTrue(response.durationMs() >= 0);
        verify(tools).invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any());
        var captor = ArgumentCaptor.forClass(List.class);
        verify(model, org.mockito.Mockito.atLeastOnce()).agentTurn(any(), captor.capture());
        @SuppressWarnings("unchecked")
        List<LlmGateway.AgentTool> exposed = (List<LlmGateway.AgentTool>) captor.getAllValues().get(0);
        assertEquals(List.of("ledger__books__list"), exposed.stream().map(tool -> tool.function().name()).toList());
    }

    @Test
    void returnsToolValidationFailureToModelWithoutThrowing() {
        ToolDefinition read = definition("worktime.records.search", ToolRisk.R1);
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(read));
        when(model.agentTurn(any(), any()))
                .thenReturn(new LlmGateway.AgentTurn("", List.of(
                                new LlmGateway.AgentToolCall("call-1", "worktime__records__search", "[]")),
                                "deepseek", true))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<LlmGateway.AgentMessage> messages = invocation.getArgument(0);
                    assertTrue(messages.get(messages.size() - 1).content().contains("工具参数必须是 JSON object"));
                    return new LlmGateway.AgentTurn("请补充查询范围。", List.of(), "deepseek", true);
                });

        LlmGateway.ChatResponse response = orchestrator().chat("查询工时");

        assertEquals("请补充查询范围。", response.content());
        verify(tools, never()).invoke(any(), any());
    }

    @Test
    void keepsExistingNotConfiguredFallback() {
        when(model.configured()).thenReturn(false);
        when(model.chat("你好")).thenReturn(new LlmGateway.ChatResponse("未配置", "not-configured", false));

        LlmGateway.ChatResponse response = orchestrator().chat("你好");

        assertEquals("未配置", response.content());
        verify(tools, never()).definitionsForCurrentUser();
    }

    @Test
    void stopsExecutingToolsAfterPerTurnLimit() {
        ToolDefinition read = definition("ledger.books.list", ToolRisk.R1);
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(read));
        when(tools.invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any()))
                .thenReturn(ToolResult.completed("ok", mapper.createObjectNode()));
        when(model.agentTurn(any(), any()))
                .thenReturn(new LlmGateway.AgentTurn("", List.of(
                        new LlmGateway.AgentToolCall("call-1", "ledger__books__list", "{}"),
                        new LlmGateway.AgentToolCall("call-2", "ledger__books__list", "{}"),
                        new LlmGateway.AgentToolCall("call-3", "ledger__books__list", "{}"),
                        new LlmGateway.AgentToolCall("call-4", "ledger__books__list", "{}"),
                        new LlmGateway.AgentToolCall("call-5", "ledger__books__list", "{}")),
                        "deepseek", true))
                .thenReturn(new LlmGateway.AgentTurn("查询完成。", List.of(), "deepseek", true));

        LlmGateway.ChatResponse response = orchestrator().chat("查询多个账本信息");

        assertEquals("查询完成。", response.content());
        verify(tools, times(4)).invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any());
        var toolCaptor = ArgumentCaptor.forClass(List.class);
        verify(model, times(2)).agentTurn(any(), toolCaptor.capture());
        assertTrue(toolCaptor.getAllValues().get(1).isEmpty());
    }

    @Test
    void includesRecentConversationMessagesAndPersistsFinalReply() {
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of());
        when(conversations.open("conversation-1", "它叫什么？"))
                .thenReturn(new AgentConversationService.SessionContext("conversation-1", 7L, List.of(
                        new AgentConversationService.StoredMessage("user", "我有几个账本？"),
                        new AgentConversationService.StoredMessage("assistant", "你有一个账本。"))));
        when(model.agentTurn(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<LlmGateway.AgentMessage> messages = invocation.getArgument(0);
            assertEquals(List.of("system", "user", "assistant", "user"),
                    messages.stream().map(LlmGateway.AgentMessage::role).toList());
            assertEquals("我有几个账本？", messages.get(1).content());
            assertEquals("它叫什么？", messages.get(3).content());
            return new LlmGateway.AgentTurn("默认账本。", List.of(), "deepseek", true);
        });

        LlmGateway.ChatResponse response = orchestrator().chat("conversation-1", "它叫什么？");

        assertEquals("conversation-1", response.sessionId());
        assertEquals("默认账本。", response.content());
        verify(conversations).complete(any(), org.mockito.ArgumentMatchers.eq("它叫什么？"),
                org.mockito.ArgumentMatchers.eq("默认账本。"));
    }

    private AgentOrchestrator orchestrator() {
        return new AgentOrchestrator(tools, model, mapper, conversations);
    }

    private ToolDefinition definition(String name, ToolRisk risk) {
        return new ToolDefinition(name, 1, "测试工具", risk, Set.of(), ToolSchemas.object(mapper));
    }
}
