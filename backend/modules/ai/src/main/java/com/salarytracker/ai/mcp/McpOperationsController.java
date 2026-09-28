package com.salarytracker.ai.mcp;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp")
@PreAuthorize("isAuthenticated()")
@Tag(name = "MCP Operations")
public class McpOperationsController {
    private final McpOperationsService operations;
    private final McpOAuthUrls urls;

    public McpOperationsController(McpOperationsService operations, McpOAuthUrls urls) {
        this.operations = operations;
        this.urls = urls;
    }

    @GetMapping("/diagnostics")
    @Operation(operationId = "getMcpDiagnostics")
    public ApiResponse<McpOperationsService.Diagnostics> diagnostics(HttpServletRequest request) {
        return ApiResponse.ok(operations.diagnostics(urls.baseUrl(request), urls.configuredBaseUrl(),
                urls.requestBaseUrl(request)));
    }

    @GetMapping("/oauth/clients")
    @Operation(operationId = "listMcpOAuthClients")
    public ApiResponse<List<McpOperationsService.ClientView>> clients() {
        return ApiResponse.ok(operations.clients());
    }

    @DeleteMapping("/oauth/clients/{clientId}")
    @Operation(operationId = "disconnectMcpOAuthClient")
    public ApiResponse<Map<String, Boolean>> disconnect(@PathVariable String clientId) {
        operations.disconnectClient(clientId);
        return ApiResponse.ok(Map.of("disconnected", true));
    }

    @GetMapping("/events")
    @Operation(operationId = "listMcpProtocolEvents")
    public ApiResponse<List<McpOperationsService.EventView>> events(@RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(operations.events(limit));
    }
}
