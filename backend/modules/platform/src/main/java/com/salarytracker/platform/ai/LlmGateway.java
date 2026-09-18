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

@Service
public class LlmGateway {
    private final ObjectMapper mapper;
    private final RestClient client;
    private final String endpoint;
    private final String model;
    private final String apiKey;

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
    }

    public ChatResponse chat(String message) {
        if (!configured()) {
            return new ChatResponse("AI 网关尚未配置，已启用本地自然语言记账解析。", "local-fallback", false);
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

    public record ChatResponse(String content, String provider, boolean configured) {
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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionRequest(String model, List<ProviderMessage> messages, double temperature,
                                         @JsonProperty("response_format") ResponseFormat responseFormat) {
    }
}
