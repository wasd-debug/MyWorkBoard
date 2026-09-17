package com.salarytracker.platform.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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

    public Map<String, Object> chat(String message) {
        if (!configured()) {
            return Map.of("content", "AI 网关尚未配置，已启用本地自然语言记账解析。", "provider", "local-fallback", "configured", false);
        }
        Map<String, Object> payload = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", message)),
                "temperature", 0.1);
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
            return Map.of("content", content, "provider", endpoint, "configured", true);
        } catch (Exception ignored) {
            return Map.of("content", body, "provider", endpoint, "configured", true);
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
        List<Map<String, Object>> messages;
        if (image == null || image.length == 0) {
            messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt));
        } else {
            String encoded = java.util.Base64.getEncoder().encodeToString(image);
            List<Map<String, Object>> content = List.of(
                    Map.of("type", "text", "text", userPrompt),
                    Map.of("type", "image_url", "image_url",
                            Map.of("url", "data:" + mediaType + ";base64," + encoded)));
            messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", content));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("messages", messages);
        payload.put("temperature", 0.1);
        payload.put("response_format", Map.of("type", "json_object"));
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
}
