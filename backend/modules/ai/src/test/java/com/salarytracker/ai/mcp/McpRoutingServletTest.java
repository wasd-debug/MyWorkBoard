package com.salarytracker.ai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

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
}
