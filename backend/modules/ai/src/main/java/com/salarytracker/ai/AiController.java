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
    private final AgentOrchestrator agent;

    public AiController(AgentOrchestrator agent) {
        this.agent = agent;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "chatWithAssistant")
    public ApiResponse<LlmGateway.ChatResponse> chat(@RequestBody ChatRequest body) {
        return ApiResponse.ok(agent.chat(body == null ? null : body.sessionId(),
                body == null ? "" : body.message(),
                body != null && body.ephemeral()));
    }

    /**
     * @param ephemeral 首页小组件等临时入口传 true：会话仍可续聊、动作卡仍可确认，
     *                  但不会出现在 AI 页面的会话列表里，7 天不活跃自动清理
     */
    public record ChatRequest(String sessionId, String message, boolean ephemeral) {
    }
}
