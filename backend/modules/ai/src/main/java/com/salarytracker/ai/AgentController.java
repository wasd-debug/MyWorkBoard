package com.salarytracker.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
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

@RestController
@RequestMapping(value = "/api/v1/agent", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Agent")
@PreAuthorize("isAuthenticated()")
public class AgentController {
    private final DomainToolRegistry tools;
    private final PendingActionService actions;
    private final CurrentUserResolver currentUser;

    public AgentController(DomainToolRegistry tools, PendingActionService actions,
                           CurrentUserResolver currentUser) {
        this.tools = tools;
        this.actions = actions;
        this.currentUser = currentUser;
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

    @PostMapping("/actions/{actionId}/reject")
    @Operation(operationId = "rejectAgentAction")
    public ApiResponse<ActionResponse> reject(@PathVariable String actionId) {
        return ApiResponse.ok(ActionResponse.of(actions.reject(actionId, currentUser.id())));
    }

    @PostMapping("/actions/{actionId}/commit")
    @Operation(operationId = "commitAgentAction")
    public ApiResponse<ToolResult> commit(@PathVariable String actionId) {
        return ApiResponse.ok(tools.invoke("worktime.record.create.commit",
                JsonNodeFactory.instance.objectNode().put("actionId", actionId)));
    }

    public record ActionResponse(String id, String toolName, String status, String expiresAt) {
        static ActionResponse of(PendingAction action) {
            return new ActionResponse(action.id(), action.toolName(), action.status().name(),
                    action.expiresAt().toString());
        }
    }
}
