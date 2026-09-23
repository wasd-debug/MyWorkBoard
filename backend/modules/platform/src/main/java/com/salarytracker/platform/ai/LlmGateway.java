package com.salarytracker.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
public class LlmGateway {
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final HttpClient streamingClient;

    public LlmGateway(ObjectMapper mapper,
                      RestClient.Builder builder,
                      @Value("${app.ai.endpoint:https://api.deepseek.com/chat/completions}") String endpoint,
                      @Value("${app.ai.model:deepseek-flash}") String model,
                      @Value("${app.ai.api-key:}") String apiKey) {
        this.mapper = mapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(45_000);
        this.client = builder.requestFactory(requestFactory).build();
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
        this.streamingClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public ChatResponse chat(String message) {
        if (!configured()) {
            return new ChatResponse("DeepSeek 尚未配置。请设置 DEEPSEEK_API_KEY 后重启后端。", "not-configured", false);
        }
        ChatCompletionRequest payload = new ChatCompletionRequest(
                model, List.of(new TextMessage("user", message)), 0.1, null);
        RestClient.RequestBodySpec request = client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON).body(payload);
        if (apiKey != null && !apiKey.isBlank()) request = request.header("Authorization", "Bearer " + apiKey);
        String body;
        try {
            body = request.retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            throw upstreamError(exception.getResponseBodyAsString(), exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new IllegalStateException("DeepSeek 暂时不可用，请稍后重试", exception);
        }
        try {
            JsonNode root = mapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText(body);
            return new ChatResponse(content, endpoint, true);
        } catch (Exception ignored) {
            return new ChatResponse(body, endpoint, true);
        }
    }

    public AgentTurn agentTurn(List<AgentMessage> messages, List<AgentTool> tools) {
        if (!configured()) {
            return new AgentTurn("DeepSeek 尚未配置。请设置 DEEPSEEK_API_KEY 后重启后端。",
                    List.of(), "not-configured", false);
        }
        ChatCompletionRequest payload = new ChatCompletionRequest(
                model, messages, 0.1, null, tools.isEmpty() ? null : tools, tools.isEmpty() ? null : "auto");
        String body = execute(payload);
        try {
            JsonNode message = mapper.readTree(body).path("choices").path(0).path("message");
            List<AgentToolCall> calls = new java.util.ArrayList<>();
            for (JsonNode call : message.path("tool_calls")) {
                calls.add(new AgentToolCall(
                        call.path("id").asText(),
                        call.path("function").path("name").asText(),
                        call.path("function").path("arguments").asText("{}")));
            }
            JsonNode usage = mapper.readTree(body).path("usage");
            TokenUsage tokenUsage = new TokenUsage(
                    usage.path("prompt_tokens").asLong(0),
                    usage.path("completion_tokens").asLong(0),
                    usage.path("total_tokens").asLong(0),
                    usage.path("prompt_cache_hit_tokens").asLong(0),
                    usage.path("prompt_cache_miss_tokens").asLong(0),
                    usage.path("completion_tokens_details").path("reasoning_tokens").asLong(0));
            return new AgentTurn(message.path("content").asText(""), List.copyOf(calls), endpoint, true,
                    tokenUsage, 0, 0);
        } catch (Exception exception) {
            throw new IllegalStateException("DeepSeek 返回了无法解析的 Agent 响应", exception);
        }
    }

    public AgentTurn agentTurnStreaming(List<AgentMessage> messages, List<AgentTool> tools,
                                        Consumer<String> contentConsumer) {
        return agentTurnStreaming(new RuntimeConfig(endpoint, model, apiKey, 0.1, 60_000), messages, tools,
                contentConsumer);
    }

    public AgentTurn agentTurnStreaming(RuntimeConfig config, List<AgentMessage> messages, List<AgentTool> tools,
                                        Consumer<String> contentConsumer) {
        if (config == null || !config.configured()) {
            return new AgentTurn("DeepSeek 尚未配置。请设置 DEEPSEEK_API_KEY 后重启后端。",
                    List.of(), "not-configured", false);
        }
        ChatCompletionRequest payload = new ChatCompletionRequest(config.model(), messages, config.temperature(), null,
                tools.isEmpty() ? null : tools, tools.isEmpty() ? null : "auto", true,
                new StreamOptions(true));
        long startedAt = System.nanoTime();
        long firstTokenMs = 0;
        StringBuilder content = new StringBuilder();
        Map<Integer, ToolCallBuilder> calls = new LinkedHashMap<>();
        TokenUsage usage = TokenUsage.empty();
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(config.endpoint()))
                    .timeout(Duration.ofMillis(config.timeoutMs()))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)));
            if (config.apiKey() != null && !config.apiKey().isBlank()) request.header("Authorization", "Bearer " + config.apiKey());
            HttpResponse<java.io.InputStream> response = streamingClient.send(request.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                throw upstreamError(body, response.statusCode());
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) continue;
                    JsonNode chunk = mapper.readTree(data);
                    JsonNode usageNode = chunk.path("usage");
                    if (!usageNode.isMissingNode() && !usageNode.isNull()) usage = tokenUsage(usageNode);
                    JsonNode delta = chunk.path("choices").path(0).path("delta");
                    String part = delta.path("content").asText("");
                    if (!part.isEmpty()) {
                        if (firstTokenMs == 0) firstTokenMs = elapsedMs(startedAt);
                        content.append(part);
                        contentConsumer.accept(part);
                    }
                    for (JsonNode call : delta.path("tool_calls")) {
                        int index = call.path("index").asInt(calls.size());
                        ToolCallBuilder builder = calls.computeIfAbsent(index, ignored -> new ToolCallBuilder());
                        if (!call.path("id").asText("").isEmpty()) builder.id.append(call.path("id").asText());
                        JsonNode function = call.path("function");
                        if (!function.path("name").asText("").isEmpty()) builder.name.append(function.path("name").asText());
                        if (!function.path("arguments").asText("").isEmpty()) builder.arguments.append(function.path("arguments").asText());
                    }
                }
            }
            List<AgentToolCall> toolCalls = calls.values().stream().map(ToolCallBuilder::build).toList();
            return new AgentTurn(content.toString(), toolCalls, config.endpoint(), true, usage,
                    elapsedMs(startedAt), firstTokenMs);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("DeepSeek 流式响应失败，请稍后重试", exception);
        }
    }

    private TokenUsage tokenUsage(JsonNode usage) {
        return new TokenUsage(usage.path("prompt_tokens").asLong(0),
                usage.path("completion_tokens").asLong(0), usage.path("total_tokens").asLong(0),
                usage.path("prompt_cache_hit_tokens").asLong(0),
                usage.path("prompt_cache_miss_tokens").asLong(0),
                usage.path("completion_tokens_details").path("reasoning_tokens").asLong(0));
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    public boolean configured() {
        return endpoint != null && !endpoint.isBlank() && apiKey != null && !apiKey.isBlank();
    }

    public String structured(String systemPrompt,
                             String userPrompt,
                             byte[] image,
                             String mediaType) {
        if (!configured()) throw new IllegalStateException("AI 网关尚未配置");
        List<ProviderMessage> messages;
        if (image == null || image.length == 0) {
            messages = List.of(
                    new TextMessage("system", systemPrompt),
                    new TextMessage("user", userPrompt));
        } else {
            String encoded = java.util.Base64.getEncoder().encodeToString(image);
            List<ContentPart> content = List.of(
                    new TextContent("text", userPrompt),
                    new ImageContent("image_url", new ImageUrl("data:" + mediaType + ";base64," + encoded)));
            messages = List.of(
                    new TextMessage("system", systemPrompt),
                    new RichMessage("user", content));
        }
        ChatCompletionRequest payload = new ChatCompletionRequest(
                model, messages, 0.1, new ResponseFormat("json_object"));
        RestClient.RequestBodySpec request = client.post().uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON).body(payload);
        if (apiKey != null && !apiKey.isBlank()) {
            request = request.header("Authorization", "Bearer " + apiKey);
        }
        String body = retrieve(request);
        try {
            JsonNode root = mapper.readTree(body);
            return root.path("choices").path(0).path("message").path("content").asText(body);
        } catch (Exception ignored) {
            return body;
        }
    }

    private IllegalStateException upstreamError(String body, int status) {
        String detail = "";
        try {
            JsonNode root = mapper.readTree(body);
            detail = root.path("error").path("message").asText("");
        } catch (Exception ignored) {
            // Providers may return an empty or non-JSON error body.
        }
        if (detail.isBlank()) {
            detail = status == 401 || status == 403
                    ? "DeepSeek API 密钥无效或已过期"
                    : "DeepSeek 暂时不可用，请稍后重试";
        }
        return new IllegalStateException(detail);
    }

    private String execute(ChatCompletionRequest payload) {
        RestClient.RequestBodySpec request = client.post().uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON).body(payload);
        if (apiKey != null && !apiKey.isBlank()) {
            request = request.header("Authorization", "Bearer " + apiKey);
        }
        return retrieve(request);
    }

    private String retrieve(RestClient.RequestBodySpec request) {
        try {
            return request.retrieve().body(String.class);
        } catch (RestClientResponseException exception) {
            throw upstreamError(exception.getResponseBodyAsString(), exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new IllegalStateException("DeepSeek 暂时不可用，请稍后重试", exception);
        }
    }

    public record ChatResponse(String content, String provider, boolean configured, String sessionId,
                               TokenUsage usage, long durationMs, long firstTokenMs,
                               List<ModelExecution> modelExecutions, List<ToolExecution> toolExecutions,
                               List<ActionRequest> actions) {
        public ChatResponse(String content, String provider, boolean configured) {
            this(content, provider, configured, null, TokenUsage.empty(), 0, 0, List.of(), List.of(), List.of());
        }

        public ChatResponse(String content, String provider, boolean configured, String sessionId) {
            this(content, provider, configured, sessionId, TokenUsage.empty(), 0, 0, List.of(), List.of(), List.of());
        }
    }

    public record ActionRequest(String status, String summary, JsonNode structuredContent,
                                String actionId, String expiresAt) { }

    public record TokenUsage(long inputTokens, long outputTokens, long totalTokens,
                             long cacheHitTokens, long cacheMissTokens, long reasoningTokens) {
        public TokenUsage(long inputTokens, long outputTokens, long totalTokens,
                          long cacheHitTokens, long cacheMissTokens) {
            this(inputTokens, outputTokens, totalTokens, cacheHitTokens, cacheMissTokens, 0);
        }

        public static TokenUsage empty() {
            return new TokenUsage(0, 0, 0, 0, 0, 0);
        }

        public TokenUsage plus(TokenUsage other) {
            if (other == null) return this;
            return new TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens,
                    totalTokens + other.totalTokens, cacheHitTokens + other.cacheHitTokens,
                    cacheMissTokens + other.cacheMissTokens, reasoningTokens + other.reasoningTokens);
        }
    }

    public record ModelExecution(int round, long durationMs, long firstTokenMs, TokenUsage usage) {
    }

    public record ToolExecution(String name, String status, String summary, long durationMs) {
    }

    public record RuntimeConfig(String endpoint, String model, String apiKey, double temperature, int timeoutMs) {
        public boolean configured() {
            return endpoint != null && !endpoint.isBlank() && model != null && !model.isBlank()
                    && apiKey != null && !apiKey.isBlank();
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AgentMessage(
            String role,
            String content,
            @JsonProperty("tool_call_id") String toolCallId,
            @JsonProperty("tool_calls") List<ProviderToolCall> toolCalls) {
        public static AgentMessage system(String content) {
            return new AgentMessage("system", content, null, null);
        }

        public static AgentMessage user(String content) {
            return new AgentMessage("user", content, null, null);
        }

        public static AgentMessage assistant(String content, List<AgentToolCall> calls) {
            List<ProviderToolCall> providerCalls = calls == null || calls.isEmpty() ? null : calls.stream()
                    .map(call -> new ProviderToolCall(call.id(), "function",
                            new ProviderFunctionCall(call.name(), call.arguments())))
                    .toList();
            return new AgentMessage("assistant", content, null, providerCalls);
        }

        public static AgentMessage tool(String callId, String content) {
            return new AgentMessage("tool", content, callId, null);
        }
    }

    public record AgentTool(String type, AgentFunction function) {
        public static AgentTool function(String name, String description, JsonNode parameters) {
            return new AgentTool("function", new AgentFunction(name, description, parameters));
        }
    }

    public record AgentFunction(String name, String description, JsonNode parameters) {
    }

    public record AgentToolCall(String id, String name, String arguments) {
    }

    public record AgentTurn(String content, List<AgentToolCall> toolCalls, String provider, boolean configured,
                            TokenUsage usage, long durationMs, long firstTokenMs) {
        public AgentTurn(String content, List<AgentToolCall> toolCalls, String provider, boolean configured) {
            this(content, toolCalls, provider, configured, TokenUsage.empty(), 0, 0);
        }

        public AgentTurn(String content, List<AgentToolCall> toolCalls, String provider, boolean configured,
                         TokenUsage usage) {
            this(content, toolCalls, provider, configured, usage, 0, 0);
        }
    }

    private static final class ToolCallBuilder {
        private final StringBuilder id = new StringBuilder();
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();

        private AgentToolCall build() {
            return new AgentToolCall(id.toString(), name.toString(), arguments.isEmpty() ? "{}" : arguments.toString());
        }
    }

    public record ProviderToolCall(String id, String type, ProviderFunctionCall function) {
    }

    public record ProviderFunctionCall(String name, String arguments) {
    }

    private sealed interface ProviderMessage permits TextMessage, RichMessage {
    }

    private record TextMessage(String role, String content) implements ProviderMessage {
    }

    private record RichMessage(String role, List<ContentPart> content) implements ProviderMessage {
    }

    private sealed interface ContentPart permits TextContent, ImageContent {
    }

    private record TextContent(String type, String text) implements ContentPart {
    }

    private record ImageContent(String type, @JsonProperty("image_url") ImageUrl imageUrl) implements ContentPart {
    }

    private record ImageUrl(String url) {
    }

    private record ResponseFormat(String type) {
    }

    private record StreamOptions(@JsonProperty("include_usage") boolean includeUsage) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionRequest(String model, List<?> messages, double temperature,
                                         @JsonProperty("response_format") ResponseFormat responseFormat,
                                         List<AgentTool> tools,
                                         @JsonProperty("tool_choice") String toolChoice,
                                         Boolean stream,
                                         @JsonProperty("stream_options") StreamOptions streamOptions) {
        private ChatCompletionRequest(String model, List<?> messages, double temperature,
                                      ResponseFormat responseFormat) {
            this(model, messages, temperature, responseFormat, null, null, null, null);
        }

        private ChatCompletionRequest(String model, List<?> messages, double temperature,
                                      ResponseFormat responseFormat, List<AgentTool> tools, String toolChoice) {
            this(model, messages, temperature, responseFormat, tools, toolChoice, null, null);
        }
    }
}
