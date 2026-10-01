package com.salarytracker.ai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpRoutingServletTest {
    @Test
    void unsupportedProtocolVersionReturnsBadRequestWithoutAuthenticationChallenge() throws Exception {
        var servlet = new McpServerConfiguration.McpRoutingServlet(mock(McpPersonalTokenService.class),
                mock(McpOperationsService.class), Map.of(), true, false, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("MCP-Protocol-Version", "2099-01-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.service(request, response);

        assertEquals(400, response.getStatus());
        assertFalse(response.containsHeader("WWW-Authenticate"));
        assertEquals("unsupported_protocol_version",
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).get("error").asText());
    }

    @Test
    void workBuddyProtocolVersionPassesRoutingGuard() throws Exception {
        var tokens = mock(McpPersonalTokenService.class);
        when(tokens.authenticate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.salarytracker.platform.UnauthorizedException("invalid token"));
        var servlet = new McpServerConfiguration.McpRoutingServlet(tokens,
                mock(McpOperationsService.class), Map.of(), true, false, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
        request.addHeader("MCP-Protocol-Version", "2025-11-25");
        MockHttpServletResponse response = new MockHttpServletResponse();

        servlet.service(request, response);

        // The request must reach authentication; it must not be rejected as a protocol mismatch.
        assertEquals(401, response.getStatus());
        assertFalse(response.getContentAsString().contains("unsupported_protocol_version"));
    }
}
