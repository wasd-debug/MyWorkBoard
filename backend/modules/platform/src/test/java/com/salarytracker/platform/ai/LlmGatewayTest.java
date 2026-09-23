package com.salarytracker.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmGatewayTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsOpenAiCompatibleToolMessagesAndParsesToolCalls() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
                      {"id":"call-2","type":"function","function":{"name":"ledger__overview","arguments":"{\\"bookId\\":\\"book-1\\"}"}}
                    ]}}],"usage":{"prompt_tokens":120,"completion_tokens":8,"total_tokens":128,
                    "prompt_cache_hit_tokens":80,"prompt_cache_miss_tokens":40}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/chat";
        LlmGateway gateway = new LlmGateway(mapper, RestClient.builder(), endpoint, "deepseek-chat", "test-key");
        List<LlmGateway.AgentMessage> messages = List.of(
                LlmGateway.AgentMessage.system("只读"),
                LlmGateway.AgentMessage.user("查看概览"),
                LlmGateway.AgentMessage.assistant("", List.of(
                        new LlmGateway.AgentToolCall("call-1", "ledger__books__list", "{}"))),
                LlmGateway.AgentMessage.tool("call-1", "{\"status\":\"completed\"}"));
        JsonNode parameters = mapper.readTree("{\"type\":\"object\",\"properties\":{}}");

        LlmGateway.AgentTurn turn = gateway.agentTurn(messages, List.of(
                LlmGateway.AgentTool.function("ledger__overview", "查询账本概览", parameters)));

        JsonNode sent = mapper.readTree(requestBody.get());
        assertEquals("deepseek-chat", sent.path("model").asText());
        assertEquals("auto", sent.path("tool_choice").asText());
        assertEquals("ledger__overview", sent.path("tools").path(0).path("function").path("name").asText());
        assertEquals("call-1", sent.path("messages").path(2).path("tool_calls").path(0).path("id").asText());
        assertEquals("call-1", sent.path("messages").path(3).path("tool_call_id").asText());
        assertTrue(sent.path("messages").path(3).path("tool_calls").isMissingNode());
        assertEquals("call-2", turn.toolCalls().get(0).id());
        assertEquals("ledger__overview", turn.toolCalls().get(0).name());
        assertEquals("{\"bookId\":\"book-1\"}", turn.toolCalls().get(0).arguments());
        assertEquals(120, turn.usage().inputTokens());
        assertEquals(8, turn.usage().outputTokens());
        assertEquals(80, turn.usage().cacheHitTokens());
    }

    @Test
    void parsesDeepSeekDsmlToolCallsInsteadOfExposingProtocolText() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            byte[] response = mapper.writeValueAsBytes(Map.of(
                    "choices", List.of(Map.of("message", Map.of(
                            "role", "assistant",
                            "content", """
                                    我来生成两笔记账预览：
                                    <|DSML|calls>
                                    <|DSML|invoke name="ledger__transaction__create__prepare">
                                    <|DSML|parameter name="bookId" string="true">book-1</|DSML|parameter>
                                    <|DSML|parameter name="kind" string="true">EXPENSE</|DSML|parameter>
                                    <|DSML|parameter name="amount" string="false">29.9</|DSML|parameter>
                                    <|DSML|parameter name="accountId" string="true">account-1</|DSML|parameter>
                                    <|DSML|parameter name="categoryId" string="true">category-1</|DSML|parameter>
                                    <|DSML|parameter name="occurredOn" string="true">2026-09-23</|DSML|parameter>
                                    <|DSML|parameter name="note" string="true">买梯子</|DSML|parameter>
                                    </|DSML|invoke>
                                    <|DSML|invoke name="ledger__transaction__create__prepare">
                                    <|DSML|parameter name="bookId" string="true">book-1</|DSML|parameter>
                                    <|DSML|parameter name="kind" string="true">EXPENSE</|DSML|parameter>
                                    <|DSML|parameter name="amount" string="false">50</|DSML|parameter>
                                    <|DSML|parameter name="accountId" string="true">account-1</|DSML|parameter>
                                    <|DSML|parameter name="categoryId" string="true">category-1</|DSML|parameter>
                                    <|DSML|parameter name="note" string="true">中转站</|DSML|parameter>
                                    </|DSML|invoke>
                                    </|DSML|calls>
                                    """))),
                    "usage", Map.of("prompt_tokens", 20, "completion_tokens", 12, "total_tokens", 32)));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        LlmGateway gateway = new LlmGateway(mapper, RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                "deepseek-chat", "test-key");
        LlmGateway.AgentTurn turn = gateway.agentTurn(List.of(LlmGateway.AgentMessage.user("记两笔账")),
                List.of(LlmGateway.AgentTool.function("ledger__transaction__create__prepare", "记账", mapper.readTree("{\"type\":\"object\"}"))));

        assertEquals("我来生成两笔记账预览：", turn.content());
        assertEquals(2, turn.toolCalls().size());
        assertEquals("ledger__transaction__create__prepare", turn.toolCalls().get(0).name());
        assertEquals(29.9, mapper.readTree(turn.toolCalls().get(0).arguments()).path("amount").asDouble());
        assertEquals("account-1", mapper.readTree(turn.toolCalls().get(1).arguments()).path("accountId").asText());
        assertTrue(turn.toolCalls().stream().noneMatch(call -> call.arguments().contains("DSML")));
    }

    @Test
    void omitsToolCallsForPlainAssistantHistory() throws Exception {
        JsonNode message = mapper.valueToTree(LlmGateway.AgentMessage.assistant("历史回答", List.of()));

        assertEquals("assistant", message.path("role").asText());
        assertEquals("历史回答", message.path("content").asText());
        assertTrue(message.path("tool_calls").isMissingNode());
    }

    @Test
    void streamsTextToolArgumentsAndUsage() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            byte[] response = ("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call-1\",\"function\":{\"name\":\"ledger__overview\",\"arguments\":\"{\\\"bookId\\\":\"}}]}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"\\\"book-1\\\"}\"}}]}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":4,\"total_tokens\":14,\"prompt_cache_hit_tokens\":6,\"prompt_cache_miss_tokens\":4,\"completion_tokens_details\":{\"reasoning_tokens\":2}}}\n\n"
                    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        LlmGateway gateway = new LlmGateway(mapper, RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/chat", "deepseek-chat", "test-key");
        List<String> deltas = new ArrayList<>();

        LlmGateway.AgentTurn turn = gateway.agentTurnStreaming(List.of(LlmGateway.AgentMessage.user("查询")),
                List.of(), deltas::add);

        assertEquals(List.of("你好"), deltas);
        assertEquals("你好", turn.content());
        assertEquals("ledger__overview", turn.toolCalls().get(0).name());
        assertEquals("{\"bookId\":\"book-1\"}", turn.toolCalls().get(0).arguments());
        assertEquals(14, turn.usage().totalTokens());
        assertEquals(2, turn.usage().reasoningTokens());
        assertTrue(turn.durationMs() >= 0);
    }

    @Test
    void suppressesStreamedDsmlAndRestoresMultipleToolCalls() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat", exchange -> {
            String first = mapper.writeValueAsString(Map.of("choices", List.of(Map.of("delta",
                    Map.of("content", "正在生成预览：\n<|DS")))));
            String second = mapper.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("delta", Map.of("content", """
                            ML|calls>
                            <|DSML|invoke name="ledger__transaction__create__prepare">
                            <|DSML|parameter name="amount" string="false">29.9</|DSML|parameter>
                            </|DSML|invoke>
                            <|DSML|invoke name="ledger__transaction__create__prepare">
                            <|DSML|parameter name="amount" string="false">50</|DSML|parameter>
                            </|DSML|invoke>
                            </|DSML|calls>"""))),
                    "usage", Map.of("prompt_tokens", 10, "completion_tokens", 20, "total_tokens", 30)));
            String dsml = "data: " + first + "\n\ndata: " + second + "\n\ndata: [DONE]\n\n";
            byte[] response = dsml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        LlmGateway gateway = new LlmGateway(mapper, RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/chat",
                "deepseek-chat", "test-key");
        List<String> deltas = new ArrayList<>();

        LlmGateway.AgentTurn turn = gateway.agentTurnStreaming(
                List.of(LlmGateway.AgentMessage.user("记两笔账")), List.of(), deltas::add);

        assertEquals("正在生成预览：", turn.content());
        assertEquals("正在生成预览：\n", String.join("", deltas));
        assertEquals(2, turn.toolCalls().size());
        assertEquals(29.9, mapper.readTree(turn.toolCalls().get(0).arguments()).path("amount").asDouble());
        assertEquals(50, mapper.readTree(turn.toolCalls().get(1).arguments()).path("amount").asInt());
        assertTrue(deltas.stream().noneMatch(part -> part.contains("DSML")));
    }
}
