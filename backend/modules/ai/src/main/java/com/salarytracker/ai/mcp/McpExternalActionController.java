package com.salarytracker.ai.mcp;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/mcp/actions/confirmation", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
@Tag(name = "MCP External Actions")
public class McpExternalActionController {
    private final McpExternalActionService actions;
    private final CurrentUserResolver currentUser;

    public McpExternalActionController(McpExternalActionService actions, CurrentUserResolver currentUser) {
        this.actions = actions;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(operationId = "getMcpExternalActionConfirmation")
    public ApiResponse<McpExternalActionService.ExternalActionView> get(@RequestParam String token) {
        return ApiResponse.ok(actions.getForConfirmation(token, currentUser.id()));
    }

    @PostMapping("/approve")
    @Operation(operationId = "approveMcpExternalActionConfirmation")
    public ApiResponse<McpExternalActionService.ExternalActionView> approve(@RequestParam String token) {
        return ApiResponse.ok(actions.approveConfirmation(token, currentUser.id()));
    }

    @PostMapping("/reject")
    @Operation(operationId = "rejectMcpExternalActionConfirmation")
    public ApiResponse<McpExternalActionService.ExternalActionView> reject(@RequestParam String token) {
        return ApiResponse.ok(actions.rejectConfirmation(token, currentUser.id()));
    }
}
