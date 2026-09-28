package com.salarytracker.ai.mcp;

import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "MCP OAuth Protocol")
public class McpOAuthPublicController {
    private final McpOAuthService oauth;
    private final McpOAuthUrls urls;
    private final boolean enabled;

    public McpOAuthPublicController(McpOAuthService oauth, McpOAuthUrls urls,
                                    @Value("${app.mcp.oauth-enabled:false}") boolean enabled) {
        this.oauth = oauth;
        this.urls = urls;
        this.enabled = enabled;
    }

    @GetMapping({"/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp"})
    @Operation(operationId = "getMcpOAuthProtectedResource")
    public ResponseEntity<Map<String, Object>> protectedResource(HttpServletRequest request) {
        requireEnabled();
        String base = urls.baseUrl(request);
        return metadata(Map.of("resource", base + "/mcp", "authorization_servers", List.of(base),
                "bearer_methods_supported", List.of("header"), "scopes_supported", McpOAuthService.SUPPORTED_SCOPES));
    }

    @GetMapping({"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"})
    @Operation(operationId = "getMcpOAuthAuthorizationServer")
    public ResponseEntity<Map<String, Object>> authorizationServer(HttpServletRequest request) {
        requireEnabled();
        String base = urls.baseUrl(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("issuer", base);
        body.put("authorization_endpoint", base + "/oauth/authorize");
        body.put("token_endpoint", base + "/oauth/token");
        body.put("registration_endpoint", base + "/oauth/register");
        body.put("revocation_endpoint", base + "/oauth/revoke");
        body.put("response_types_supported", List.of("code"));
        body.put("grant_types_supported", List.of("authorization_code", "refresh_token"));
        body.put("token_endpoint_auth_methods_supported", List.of("none"));
        body.put("code_challenge_methods_supported", List.of("S256"));
        body.put("scopes_supported", McpOAuthService.SUPPORTED_SCOPES);
        return metadata(body);
    }

    @PostMapping(value = "/oauth/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "registerMcpOAuthClient")
    public McpOAuthService.ClientRegistration register(@RequestBody McpOAuthService.RegisterClientRequest request,
                                                       HttpServletRequest servletRequest) {
        requireEnabled();
        return oauth.registerClient(request, requestContext(servletRequest));
    }

    @GetMapping("/oauth/authorize")
    @Operation(operationId = "authorizeMcpOAuth")
    public ResponseEntity<Void> authorize(@RequestParam(name = "response_type") String responseType,
                                          @RequestParam(name = "client_id") String clientId,
                                          @RequestParam(name = "redirect_uri") String redirectUri,
                                          @RequestParam String scope,
                                          @RequestParam(required = false) String state,
                                          @RequestParam(name = "code_challenge") String codeChallenge,
                                          @RequestParam(name = "code_challenge_method") String codeChallengeMethod,
                                          @RequestParam String resource,
                                          HttpServletRequest request) {
        requireEnabled();
        try {
            requireResource(resource, request);
            oauth.validateAuthorizationRequest(new McpOAuthService.AuthorizationRequest(responseType, clientId,
                    redirectUri, scope, state, codeChallenge, codeChallengeMethod, resource));
            String query = request.getQueryString();
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(urls.baseUrl(request) + "/oauth/consent" + (query == null ? "" : "?" + query)))
                    .build();
        } catch (McpOAuthService.OAuthException exception) {
            if (!oauth.isTrustedRedirect(clientId, redirectUri)) throw exception;
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(oauth.errorRedirect(redirectUri, exception, state)))
                    .cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache").build();
        }
    }

    @PostMapping(value = "/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(operationId = "exchangeMcpOAuthToken")
    public ResponseEntity<McpOAuthService.TokenResponse> token(
                                               @RequestParam(name = "grant_type") String grantType,
                                               @RequestParam(name = "client_id") String clientId,
                                               @RequestParam(required = false) String code,
                                               @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                               @RequestParam(name = "code_verifier", required = false) String codeVerifier,
                                               @RequestParam(name = "refresh_token", required = false) String refreshToken,
                                               @RequestParam(required = false) String scope,
                                               @RequestParam(required = false) String resource,
                                               HttpServletRequest request) {
        requireEnabled();
        if (resource != null && !resource.isBlank()) requireResource(resource, request);
        McpOAuthService.TokenResponse body = oauth.exchange(new McpOAuthService.TokenRequest(grantType, clientId,
                code, redirectUri, codeVerifier, refreshToken, scope, resource), requestContext(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header(HttpHeaders.PRAGMA, "no-cache").body(body);
    }

    @PostMapping(value = "/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(operationId = "revokeMcpOAuthToken")
    public ResponseEntity<Void> revoke(@RequestParam String token,
                                       @RequestParam(name = "client_id") String clientId,
                                       HttpServletRequest request) {
        requireEnabled();
        oauth.revoke(token, clientId, requestContext(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").build();
    }

    @ExceptionHandler(McpOAuthService.OAuthException.class)
    ResponseEntity<Map<String, String>> oauthError(McpOAuthService.OAuthException exception) {
        return ResponseEntity.status(exception.status()).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(Map.of("error", exception.error(), "error_description", exception.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> metadata(Map<String, Object> body) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))).body(body);
    }

    private void requireResource(String resource, HttpServletRequest request) {
        if (!urls.resource(request).equals(resource)) {
            throw new McpOAuthService.OAuthException("invalid_target", "resource 必须是当前站点的 MCP /mcp 端点");
        }
    }
    private McpOperationsService.RequestContext requestContext(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        return new McpOperationsService.RequestContext(ip, request.getHeader("User-Agent"));
    }
    private void requireEnabled() { if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
