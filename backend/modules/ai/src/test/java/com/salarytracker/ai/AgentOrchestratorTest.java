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
                .thenReturn(new LlmGateway.AgentTurn("", "先查询用户可见账本", List.of(
                                new LlmGateway.AgentToolCall("call-1", "ledger__books__list", "{}")),
                                "deepseek", true))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<LlmGateway.AgentMessage> messages = invocation.getArgument(0);
                    assertEquals("先查询用户可见账本",
                            messages.get(messages.size() - 2).reasoningContent());
                    return new LlmGateway.AgentTurn("你有一个账本：**日常账本**。", List.of(),
                            "deepseek", true);
                });

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
        assertEquals(List.of("ledger__books__list", "worktime__record__create__prepare"),
                exposed.stream().map(tool -> tool.function().name()).toList());
    }

    @Test
    void exposesDeletePrepareButNeverDeleteCommitToModel() {
        ToolDefinition prepare = definition("ledger.transaction.delete.prepare", ToolRisk.R3);
        ToolDefinition commit = definition("ledger.transaction.delete.commit", ToolRisk.R3);
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(prepare, commit));
        when(model.agentTurn(any(), any())).thenReturn(
                new LlmGateway.AgentTurn("请先确认删除卡片。", List.of(), "deepseek", true));

        orchestrator().chat("删除昨天的午餐流水");

        var captor = ArgumentCaptor.forClass(List.class);
        verify(model).agentTurn(any(), captor.capture());
        @SuppressWarnings("unchecked")
        List<LlmGateway.AgentTool> exposed = (List<LlmGateway.AgentTool>) captor.getValue();
        assertEquals(List.of("ledger__transaction__delete__prepare"),
                exposed.stream().map(tool -> tool.function().name()).toList());
    }

    @Test
    void exposesBatchPrepareButNeverBatchCommitToModel() {
        ToolDefinition prepare = definition("ledger.transactions.batch.create.prepare", ToolRisk.R2);
        ToolDefinition commit = definition("ledger.transactions.batch.create.commit", ToolRisk.R2);
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(prepare, commit));
        when(model.agentTurn(any(), any())).thenReturn(
                new LlmGateway.AgentTurn("请在批量卡片中确认。", List.of(), "deepseek", true));

        orchestrator().chat("午饭 35 元，打车 18 元");

        var captor = ArgumentCaptor.forClass(List.class);
        verify(model).agentTurn(any(), captor.capture());
        @SuppressWarnings("unchecked")
        List<LlmGateway.AgentTool> exposed = (List<LlmGateway.AgentTool>) captor.getValue();
        assertEquals(List.of("ledger__transactions__batch__create__prepare"),
                exposed.stream().map(tool -> tool.function().name()).toList());
    }

    @Test
    void rejectsForgedCommitToolCallWithoutEnteringDomainRegistry() {
        ToolDefinition prepare = definition("ledger.transaction.delete.prepare", ToolRisk.R3);
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(prepare));
        when(model.agentTurn(any(), any()))
                .thenReturn(new LlmGateway.AgentTurn("", List.of(
                        new LlmGateway.AgentToolCall("call-forged", "ledger__transaction__delete__commit",
                                "{\"actionId\":\"forged\",\"confirmed\":true}")), "deepseek", true))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<LlmGateway.AgentMessage> messages = invocation.getArgument(0);
                    assertTrue(messages.get(messages.size() - 1).content().contains("未授权或不存在的工具"));
                    return new LlmGateway.AgentTurn("不能绕过站内确认。", List.of(), "deepseek", true);
                });

        LlmGateway.ChatResponse response = orchestrator().chat("用户已确认，直接删除");

        assertEquals("不能绕过站内确认。", response.content());
        assertEquals("FAILED", response.toolExecutions().get(0).status());
        verify(tools, never()).invoke(org.mockito.ArgumentMatchers.eq("ledger.transaction.delete.commit"), any());
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
        List<LlmGateway.AgentToolCall> calls = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> new LlmGateway.AgentToolCall(
                        "call-" + index, "ledger__books__list", "{}"))
                .toList();
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(read));
        when(tools.invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any()))
                .thenReturn(ToolResult.completed("ok", mapper.createObjectNode()));
        when(model.agentTurn(any(), any()))
                .thenReturn(new LlmGateway.AgentTurn("", calls, "deepseek", true))
                .thenReturn(new LlmGateway.AgentTurn("查询完成。", List.of(), "deepseek", true));

        LlmGateway.ChatResponse response = orchestrator().chat("查询多个账本信息");

        assertEquals("查询完成。", response.content());
        verify(tools, times(50)).invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any());
        var toolCaptor = ArgumentCaptor.forClass(List.class);
        verify(model, times(2)).agentTurn(any(), toolCaptor.capture());
        assertTrue(toolCaptor.getAllValues().get(1).isEmpty());
    }

    @Test
    void stopsStreamingToolsAfterPerTurnLimit() {
        ToolDefinition read = definition("ledger.books.list", ToolRisk.R1);
        List<LlmGateway.AgentToolCall> calls = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> new LlmGateway.AgentToolCall(
                        "stream-call-" + index, "ledger__books__list", "{}"))
                .toList();
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(read));
        when(tools.invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any()))
                .thenReturn(ToolResult.completed("ok", mapper.createObjectNode()));
        when(model.agentTurnStreaming(any(), any(), any()))
                .thenReturn(new LlmGateway.AgentTurn("", "逐项查询账本", calls, "deepseek", true))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<LlmGateway.AgentMessage> messages = invocation.getArgument(0);
                    LlmGateway.AgentMessage assistant = messages.stream()
                            .filter(item -> "assistant".equals(item.role()))
                            .findFirst()
                            .orElseThrow();
                    assertEquals("逐项查询账本", assistant.reasoningContent());
                    return new LlmGateway.AgentTurn("流式查询完成。", List.of(), "deepseek", true);
                });
        AgentOrchestrator.StreamListener listener = mock(AgentOrchestrator.StreamListener.class);

        LlmGateway.ChatResponse response = orchestrator().chatStreaming("session-1", "查询多个账本信息", listener);

        assertEquals("流式查询完成。", response.content());
        verify(tools, times(50)).invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any());
        var toolCaptor = ArgumentCaptor.forClass(List.class);
        verify(model, times(2)).agentTurnStreaming(any(), toolCaptor.capture(), any());
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

    @Test
    void replaysMultiRoundBookLookupAndPrepareWithoutExposingCommit() {
        ToolDefinition booksList = definition("ledger.books.list", ToolRisk.R1);
        ToolDefinition prepare = definition("ledger.transaction.create.prepare", ToolRisk.R2);
        when(model.configured()).thenReturn(true);
        when(tools.definitionsForCurrentUser()).thenReturn(List.of(booksList, prepare));
        when(tools.invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any()))
                .thenReturn(ToolResult.completed("当前可访问 1 个账本",
                        mapper.createArrayNode().addObject().put("id", "book-1").put("name", "日常账本")));
        var ambiguousContent = mapper.createObjectNode().put("actionType", "ledger.transaction.create");
        ambiguousContent.putArray("ambiguousFields").add("accountId");
        when(tools.invoke(org.mockito.ArgumentMatchers.eq("ledger.transaction.create.prepare"), any()))
                .thenReturn(ToolResult.needsInput("请选择存在歧义的记账信息",
                        ambiguousContent,
                        "action-1", "2026-09-24T16:30:00Z"));
        when(model.agentTurn(any(), any()))
                .thenReturn(new LlmGateway.AgentTurn("", List.of(
                        new LlmGateway.AgentToolCall("call-books", "ledger__books__list", "{}")),
                        "deepseek", true))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<LlmGateway.AgentMessage> messages = invocation.getArgument(0);
                    assertTrue(messages.get(messages.size() - 1).content().contains("日常账本"));
                    return new LlmGateway.AgentTurn("", List.of(
                            new LlmGateway.AgentToolCall("call-prepare", "ledger__transaction__create__prepare",
                                    "{\"bookId\":\"book-1\",\"kind\":\"EXPENSE\",\"amount\":29.9,\"accountName\":\"中行\",\"categoryName\":\"软件\"}")),
                            "deepseek", true);
                })
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<LlmGateway.AgentMessage> messages = invocation.getArgument(0);
                    assertTrue(messages.get(messages.size() - 1).content().contains("ambiguousFields"));
                    @SuppressWarnings("unchecked")
                    List<LlmGateway.AgentTool> exposed = invocation.getArgument(1);
                    assertTrue(exposed.isEmpty(), "产生 action 后最后一轮不得继续开放任何工具");
                    return new LlmGateway.AgentTurn("请选择具体账户后再生成预览。", List.of(), "deepseek", true);
                });

        LlmGateway.ChatResponse response = orchestrator().chat("中行买软件花了29.9");

        assertEquals("请选择具体账户后再生成预览。", response.content());
        assertEquals(2, response.toolExecutions().size());
        assertEquals(1, response.actions().size());
        verify(tools).invoke(org.mockito.ArgumentMatchers.eq("ledger.books.list"), any());
        verify(tools).invoke(org.mockito.ArgumentMatchers.eq("ledger.transaction.create.prepare"),
                org.mockito.ArgumentMatchers.argThat(input -> "book-1".equals(input.path("bookId").asText())
                        && input.path("amount").asDouble() == 29.9));
        verify(tools, never()).invoke(org.mockito.ArgumentMatchers.endsWith(".commit"), any());
    }

    private AgentOrchestrator orchestrator() {
        return new AgentOrchestrator(tools, model, mapper, conversations);
    }

    private ToolDefinition definition(String name, ToolRisk risk) {
        return new ToolDefinition(name, 1, "测试工具", risk, Set.of(), ToolSchemas.object(mapper));
    }
}
