package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.platform.ai.LlmGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DomainToolRegistry tools = mock(DomainToolRegistry.class);
    private final LlmGateway model = mock(LlmGateway.class);

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

        LlmGateway.ChatResponse response = new AgentOrchestrator(tools, model, mapper).chat("我有哪些账本？");

        assertEquals("你有一个账本：**日常账本**。", response.content());
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

        LlmGateway.ChatResponse response = new AgentOrchestrator(tools, model, mapper).chat("查询工时");

        assertEquals("请补充查询范围。", response.content());
        verify(tools, never()).invoke(any(), any());
    }

    @Test
    void keepsExistingNotConfiguredFallback() {
        when(model.configured()).thenReturn(false);
        when(model.chat("你好")).thenReturn(new LlmGateway.ChatResponse("未配置", "not-configured", false));

        LlmGateway.ChatResponse response = new AgentOrchestrator(tools, model, mapper).chat("你好");

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

        LlmGateway.ChatResponse response = new AgentOrchestrator(tools, model, mapper).chat("查询多个账本信息");

        assertEquals("查询完成。", response.content());
        verify(tools, times(4)).invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any());
        var toolCaptor = ArgumentCaptor.forClass(List.class);
        verify(model, times(2)).agentTurn(any(), toolCaptor.capture());
        assertTrue(toolCaptor.getAllValues().get(1).isEmpty());
    }

    private ToolDefinition definition(String name, ToolRisk risk) {
        return new ToolDefinition(name, 1, "测试工具", risk, Set.of(), ToolSchemas.object(mapper));
    }
}
