package com.salarytracker.ai;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.ai.LlmGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/ai", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "AI")
public class AiController {
    private final LlmGateway gateway;

    public AiController(LlmGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "chatWithAssistant")
    public ApiResponse<LlmGateway.ChatResponse> chat(@RequestBody ChatRequest body) {
        return ApiResponse.ok(gateway.chat(body == null ? "" : body.message()));
    }

    public record ChatRequest(String message) {
    }
}
