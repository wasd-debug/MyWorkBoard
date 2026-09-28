package com.salarytracker.ai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpOAuthPublicControllerTest {
    @Test
    void redirectsAuthorizationErrorsOnlyToPreviouslyRegisteredRedirectUri() {
        McpOAuthService oauth = mock(McpOAuthService.class);
        McpOAuthUrls urls = new McpOAuthUrls("");
        McpOAuthPublicController controller = new McpOAuthPublicController(oauth, urls, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth/authorize");
        request.setScheme("https");
        request.addHeader("Host", "work.example");
        doThrow(new McpOAuthService.OAuthException("invalid_scope", "scope 无效"))
                .when(oauth).validateAuthorizationRequest(any());
        when(oauth.isTrustedRedirect("client", "https://client.example/callback")).thenReturn(true);
        when(oauth.errorRedirect(eq("https://client.example/callback"), any(McpOAuthService.OAuthException.class),
                eq("state"))).thenReturn("https://client.example/callback?error=invalid_scope&state=state");

        var response = controller.authorize("code", "client", "https://client.example/callback",
                "bad", "state", "x".repeat(43), "S256", "https://work.example/mcp", request);

        assertEquals(302, response.getStatusCode().value());
        assertTrue(response.getHeaders().getLocation().toString().startsWith("https://client.example/callback?error=invalid_scope"));
        assertEquals(CacheControl.noStore().getHeaderValue(), response.getHeaders().getCacheControl());
    }
}
