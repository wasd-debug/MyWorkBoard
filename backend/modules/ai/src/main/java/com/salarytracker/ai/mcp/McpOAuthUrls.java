package com.salarytracker.ai.mcp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class McpOAuthUrls {
    private final String configuredBaseUrl;

    public McpOAuthUrls(@Value("${app.public-base-url:}") String configuredBaseUrl) {
        this.configuredBaseUrl = trim(configuredBaseUrl);
    }

    public String baseUrl(HttpServletRequest request) {
        if (!configuredBaseUrl.isBlank()) return configuredBaseUrl;
        String scheme = first(request.getHeader("X-Forwarded-Proto"), request.getScheme());
        String host = first(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        return trim(scheme + "://" + host);
    }

    public String resource(HttpServletRequest request) { return baseUrl(request) + "/mcp"; }

    private static String first(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred.split(",")[0].trim();
    }

    private static String trim(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
