package com.salarytracker.ai.mcp;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/mcp/tokens", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
@Tag(name = "MCP Personal Tokens")
public class McpPersonalTokenController {
    private final McpPersonalTokenService tokens;

    public McpPersonalTokenController(McpPersonalTokenService tokens) { this.tokens = tokens; }

    @GetMapping
    @Operation(operationId = "listMcpPersonalTokens")
    public ApiResponse<McpPersonalTokenService.TokenPage> list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int pageSize,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "ALL") String status) {
        return ApiResponse.ok(tokens.list(page, pageSize, status));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createMcpPersonalToken")
    public ApiResponse<McpPersonalTokenService.CreatedToken> create(
            @RequestBody McpPersonalTokenService.CreateToken command) {
        return ApiResponse.ok(tokens.create(command));
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "revokeMcpPersonalToken")
    public ApiResponse<Map<String, Boolean>> revoke(@PathVariable String id) {
        tokens.revoke(id);
        return ApiResponse.ok(Map.of("revoked", true));
    }

    @DeleteMapping("/{id}/purge")
    @Operation(operationId = "purgeMcpPersonalToken")
    public ApiResponse<Map<String, Boolean>> purge(@PathVariable String id) {
        tokens.purge(id);
        return ApiResponse.ok(Map.of("deleted", true));
    }
}
