package com.salarytracker.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.session.AgentConversationService;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Iterator;

@RestController
@RequestMapping(value = "/api/v1/agent", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Agent")
@PreAuthorize("isAuthenticated()")
public class AgentController {
    private final DomainToolRegistry tools;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final AgentConversationService conversations;

    public AgentController(DomainToolRegistry tools, PendingActionService actions,
                           CurrentUserResolver currentUser, ObjectMapper mapper,
                           AgentConversationService conversations) {
        this.tools = tools;
        this.actions = actions;
        this.currentUser = currentUser;
        this.mapper = mapper;
        this.conversations = conversations;
    }

    @GetMapping("/tools")
    @Operation(operationId = "listAgentTools")
    public ApiResponse<List<ToolDefinition>> listTools() {
        return ApiResponse.ok(tools.definitionsForCurrentUser());
    }

    @PostMapping(value = "/tools/{toolName}/invoke", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "invokeAgentTool")
    public ApiResponse<ToolResult> invoke(@PathVariable String toolName,
                                          @RequestBody(required = false) JsonNode input) {
        return ApiResponse.ok(tools.invoke(toolName,
                input == null ? JsonNodeFactory.instance.objectNode() : input));
    }

    @PostMapping("/actions/{actionId}/approve")
    @Operation(operationId = "approveAgentAction")
    public ApiResponse<ActionResponse> approve(@PathVariable String actionId) {
        return ApiResponse.ok(ActionResponse.of(actions.approve(actionId, currentUser.id())));
    }

    @GetMapping("/actions/{actionId}")
    @Operation(operationId = "getAgentAction")
    public ApiResponse<ActionResponse> action(@PathVariable String actionId) {
        return ApiResponse.ok(ActionResponse.of(actions.getForUser(actionId, currentUser.id())));
    }

    @PostMapping("/actions/{actionId}/reject")
    @Operation(operationId = "rejectAgentAction")
    public ApiResponse<ActionResponse> reject(@PathVariable String actionId) {
        return ApiResponse.ok(ActionResponse.of(actions.reject(actionId, currentUser.id())));
    }

    @PostMapping(value = "/actions/{actionId}/answer", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "answerAgentAction")
    public ApiResponse<ToolResult> answer(@PathVariable String actionId,
                                          @RequestBody(required = false) JsonNode answer) {
        long userId = currentUser.id();
        PendingAction action = actions.getForUser(actionId, userId);
        if (action.status() != com.salarytracker.ai.action.ActionStatus.WAITING_INPUT) {
            throw new IllegalStateException("action 当前不等待补充输入");
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode merged = (com.fasterxml.jackson.databind.node.ObjectNode)
                    JsonNodeFactory.instance.objectNode().setAll(
                            (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(action.inputSnapshot()));
            JsonNode values = answer == null ? JsonNodeFactory.instance.objectNode() : answer;
            if (!values.isObject()) throw new IllegalArgumentException("补充输入必须是 JSON object");
            Iterator<java.util.Map.Entry<String, JsonNode>> fields = values.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                merged.set(field.getKey(), field.getValue());
            }
            ToolResult result = tools.invoke(action.toolName(), merged);
            conversations.replaceAction(actionId, mapper.valueToTree(result));
            actions.transition(actionId, userId, com.salarytracker.ai.action.ActionStatus.CANCELLED);
            return ApiResponse.ok(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("无法恢复 action 参数", exception);
        }
    }

    @PostMapping("/actions/{actionId}/commit")
    @Operation(operationId = "commitAgentAction")
    public ApiResponse<ToolResult> commit(@PathVariable String actionId) {
        PendingAction action = actions.getForUser(actionId, currentUser.id());
        if (!action.toolName().endsWith(".prepare")) throw new IllegalArgumentException("action 工具不支持提交");
        String commitTool = action.toolName().substring(0, action.toolName().length() - ".prepare".length()) + ".commit";
        return ApiResponse.ok(tools.invoke(commitTool,
                JsonNodeFactory.instance.objectNode().put("actionId", actionId)));
    }

    public record ActionResponse(String id, String toolName, String status, String expiresAt) {
        static ActionResponse of(PendingAction action) {
            return new ActionResponse(action.id(), action.toolName(), action.status().name(),
                    action.expiresAt().toString());
        }
    }
}
