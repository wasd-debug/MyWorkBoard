package com.salarytracker.ai.mcp;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mcp/oauth")
@PreAuthorize("isAuthenticated()")
@Tag(name = "MCP OAuth")
public class McpOAuthAuthorizationController {
    private final McpOAuthService oauth;
    private final McpOAuthUrls urls;
    private final boolean enabled;

    public McpOAuthAuthorizationController(McpOAuthService oauth, McpOAuthUrls urls,
                                           @Value("${app.mcp.oauth-enabled:false}") boolean enabled) {
        this.oauth = oauth;
        this.urls = urls;
        this.enabled = enabled;
    }

    @GetMapping("/authorization")
    @Operation(operationId = "previewMcpOAuthAuthorization")
    public ApiResponse<McpOAuthService.AuthorizationPreview> preview(
            @RequestParam(name = "response_type") String responseType,
            @RequestParam(name = "client_id") String clientId,
            @RequestParam(name = "redirect_uri") String redirectUri,
            @RequestParam String scope,
            @RequestParam(required = false) String state,
            @RequestParam(name = "code_challenge") String codeChallenge,
            @RequestParam(name = "code_challenge_method") String codeChallengeMethod,
            @RequestParam String resource,
            HttpServletRequest servletRequest) {
        requireEnabled();
        requireResource(resource, servletRequest);
        return ApiResponse.ok(oauth.preview(new McpOAuthService.AuthorizationRequest(responseType, clientId,
                redirectUri, scope, state, codeChallenge, codeChallengeMethod, resource)));
    }

    @PostMapping("/authorization/decision")
    @Operation(operationId = "decideMcpOAuthAuthorization")
    public ApiResponse<McpOAuthService.AuthorizationDecision> decide(
            @RequestBody McpOAuthService.AuthorizationDecisionCommand command,
            HttpServletRequest servletRequest) {
        requireEnabled();
        if (command == null || command.request() == null) {
            throw new IllegalArgumentException("授权请求不能为空");
        }
        requireResource(command.request().resource(), servletRequest);
        return ApiResponse.ok(oauth.decide(command));
    }

    @GetMapping("/grants")
    @Operation(operationId = "listMcpOAuthGrants")
    public ApiResponse<List<McpOAuthService.GrantView>> grants() {
        requireEnabled();
        return ApiResponse.ok(oauth.grants());
    }

    @DeleteMapping("/grants/{id}")
    @Operation(operationId = "revokeMcpOAuthGrant")
    public ApiResponse<Map<String, Boolean>> revoke(@PathVariable String id) {
        requireEnabled();
        oauth.revokeGrant(id);
        return ApiResponse.ok(Map.of("revoked", true));
    }

    private void requireResource(String resource, HttpServletRequest request) {
        if (!urls.resource(request).equals(resource)) {
            throw new IllegalArgumentException("resource 必须是当前站点的 MCP /mcp 端点");
        }
    }
    private void requireEnabled() { if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
