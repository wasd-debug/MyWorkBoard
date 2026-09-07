package com.salarytracker.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
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
                      @Value("${app.ai.endpoint:}") String endpoint,
                      @Value("${app.ai.model:deepseek-chat}") String model,
                      @Value("${app.ai.api-key:}") String apiKey) {
        this.mapper = mapper;
        this.client = builder.build();
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
    }

    public Map<String, Object> chat(String message) {
        if (endpoint == null || endpoint.isBlank()) {
            return Map.of("content", "AI 网关尚未配置，已启用本地自然语言记账解析。", "provider", "local-fallback", "configured", false);
        }
        Map<String, Object> payload = Map.of("model", model, "messages", List.of(Map.of("role", "user", "content", message)), "temperature", 0.1);
        RestClient.RequestBodySpec request = client.post().uri(endpoint).contentType(MediaType.APPLICATION_JSON).body(payload);
        if (apiKey != null && !apiKey.isBlank()) request = request.header("Authorization", "Bearer " + apiKey);
        String body = request.retrieve().body(String.class);
        try {
            JsonNode root = mapper.readTree(body);
            String content = root.path("choices").path(0).path("message").path("content").asText(body);
            return Map.of("content", content, "provider", endpoint, "configured", true);
        } catch (Exception ignored) {
            return Map.of("content", body, "provider", endpoint, "configured", true);
        }
    }
}
