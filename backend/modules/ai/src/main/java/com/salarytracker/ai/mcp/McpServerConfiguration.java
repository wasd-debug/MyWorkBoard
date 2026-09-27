package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class McpServerConfiguration {
    private static final String TOKEN_CONTEXT_KEY = "salary.mcp.token";
    private static final String LEDGER_PREFIX = "ledger.";
    private static final String WORKTIME_PREFIX = "worktime.";
    private static final Set<String> EXPOSED_TOOLS = Set.of(
            "worktime.settings.get", "worktime.records.search",
            "ledger.books.list", "ledger.overview", "ledger.transactions.search",
            "ledger.transaction.history", "ledger.reports.summary", "ledger.budgets.list");

    private final List<McpSyncServer> servers = new ArrayList<>();

    @Bean
    ServletRegistrationBean<HttpServlet> mcpServletRegistration(
            DomainToolRegistry registry, McpPersonalTokenService tokens, ObjectMapper mapper,
            @Value("${app.mcp.enabled:true}") boolean enabled) {
        Map<String, HttpServletStreamableServerTransportProvider> providers = new LinkedHashMap<>();
        providers.put(McpPersonalTokenService.LEDGER_READ,
                provider(registry, tokens, mapper, Set.of(McpPersonalTokenService.LEDGER_READ)));
        providers.put(McpPersonalTokenService.WORKTIME_READ,
                provider(registry, tokens, mapper, Set.of(McpPersonalTokenService.WORKTIME_READ)));
        providers.put("both", provider(registry, tokens, mapper,
                Set.of(McpPersonalTokenService.LEDGER_READ, McpPersonalTokenService.WORKTIME_READ)));
        McpRoutingServlet servlet = new McpRoutingServlet(tokens, providers, enabled);
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(servlet, "/mcp", "/mcp/*");
        registration.setName("mcpStreamableHttp");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    private HttpServletStreamableServerTransportProvider provider(
            DomainToolRegistry registry, McpPersonalTokenService tokens, ObjectMapper mapper, Set<String> scopes) {
        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .keepAliveInterval(Duration.ofSeconds(30))
                .maxRequestSize(1024 * 1024)
                .contextExtractor(request -> McpTransportContext.create(Map.of(
                        TOKEN_CONTEXT_KEY, request.getAttribute(TOKEN_CONTEXT_KEY))))
                .build();
        List<McpServerFeatures.SyncToolSpecification> specifications = registry.definitions().stream()
                .filter(definition -> visible(definition.name(), scopes))
                .filter(definition -> EXPOSED_TOOLS.contains(definition.name()))
                .map(definition -> specification(definition, registry, tokens, mapper))
                .toList();
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("salary-sync", "1.0.0")
                .instructions("个人工作台只读 MCP。所有工具均按 Token 用户、scope 和账本范围执行。")
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                .strictToolNameValidation(false)
                .requestTimeout(Duration.ofSeconds(30))
                .tools(specifications)
                .build();
        servers.add(server);
        return transport;
    }

    private McpServerFeatures.SyncToolSpecification specification(
            ToolDefinition definition, DomainToolRegistry registry, McpPersonalTokenService tokens,
            ObjectMapper mapper) {
        Map<String, Object> schema = mapper.convertValue(definition.inputSchema(), new TypeReference<>() { });
        McpSchema.Tool tool = McpSchema.Tool.builder(definition.name(), schema)
                .description(definition.description())
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> invoke(exchange.transportContext(), request.arguments(),
                        definition, registry, tokens, mapper))
                .build();
    }

    private McpSchema.CallToolResult invoke(McpTransportContext context, Map<String, Object> arguments,
                                             ToolDefinition definition, DomainToolRegistry registry,
                                             McpPersonalTokenService tokens, ObjectMapper mapper) {
        long startedAt = System.nanoTime();
        McpPersonalTokenService.AuthenticatedToken token =
                (McpPersonalTokenService.AuthenticatedToken) context.get(TOKEN_CONTEXT_KEY);
        String status = "FAILED";
        String summary = null;
        String errorCode = null;
        try {
            requireScope(token, definition.name());
            JsonNode input = mapper.valueToTree(arguments == null ? Map.of() : arguments);
            validateQueryBounds(input);
            if (definition.name().startsWith(LEDGER_PREFIX)) {
                tokens.requireBook(token, input.path("bookId").asText(null));
            }
            var authorities = token.user().authorities().stream().map(SimpleGrantedAuthority::new).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(token.user(), null, authorities));
            ToolResult result = registry.invoke(definition.name(), input);
            JsonNode structured = scopedContent(definition.name(), result.structuredContent(), token, mapper);
            status = result.status().name();
            summary = result.summary();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", result.status().name().toLowerCase());
            payload.put("summary", result.summary());
            payload.put("structuredContent", mapper.convertValue(structured, Object.class));
            payload.put("actionId", null);
            payload.put("confirmationUrl", null);
            payload.put("expiresAt", null);
            return McpSchema.CallToolResult.builder()
                    .addTextContent(result.summary())
                    .structuredContent(payload)
                    .isError(false)
                    .build();
        } catch (SecurityException exception) {
            status = "DENIED";
            summary = exception.getMessage();
            errorCode = "MCP_SCOPE_DENIED";
            return error(summary, errorCode);
        } catch (IllegalArgumentException exception) {
            summary = exception.getMessage();
            errorCode = "INVALID_ARGUMENT";
            return error(summary, errorCode);
        } catch (Exception exception) {
            summary = "工具执行失败";
            errorCode = "TOOL_EXECUTION_FAILED";
            return error(summary, errorCode);
        } finally {
            SecurityContextHolder.clearContext();
            if (token != null) {
                try {
                    tokens.audit(token, definition.name(), status, elapsed(startedAt),
                            safeArguments(arguments, mapper), summary, errorCode);
                } catch (Exception ignored) { /* audit failure must not expose internals to the client */ }
            }
        }
    }

    private JsonNode scopedContent(String toolName, JsonNode content,
                                   McpPersonalTokenService.AuthenticatedToken token, ObjectMapper mapper) {
        if (!"ledger.books.list".equals(toolName) || content == null || !content.isArray() || token.bookIds().isEmpty()) {
            return content == null ? mapper.createObjectNode() : content;
        }
        ArrayNode filtered = mapper.createArrayNode();
        content.forEach(item -> {
            if (token.bookIds().contains(item.path("id").asText())
                    || token.bookIds().contains(item.path("publicId").asText())) filtered.add(item);
        });
        return filtered;
    }

    private void requireScope(McpPersonalTokenService.AuthenticatedToken token, String toolName) {
        if (token == null) throw new SecurityException("MCP Token 上下文缺失");
        String required = toolName.startsWith(LEDGER_PREFIX)
                ? McpPersonalTokenService.LEDGER_READ : McpPersonalTokenService.WORKTIME_READ;
        if (!token.scopes().contains(required)) throw new SecurityException("Token 缺少工具所需 scope");
    }

    private void validateQueryBounds(JsonNode input) {
        String from = input.path("from").asText(null);
        String to = input.path("to").asText(null);
        if (from != null && to != null
                && ChronoUnit.DAYS.between(LocalDate.parse(from), LocalDate.parse(to)) > 366) {
            throw new IllegalArgumentException("MCP 单次查询日期跨度不能超过 366 天");
        }
    }

    private boolean visible(String name, Set<String> scopes) {
        return name.startsWith(LEDGER_PREFIX) && scopes.contains(McpPersonalTokenService.LEDGER_READ)
                || name.startsWith(WORKTIME_PREFIX) && scopes.contains(McpPersonalTokenService.WORKTIME_READ);
    }

    private McpSchema.CallToolResult error(String summary, String code) {
        return McpSchema.CallToolResult.builder().addTextContent(summary).isError(true)
                .structuredContent(Map.of("status", "failed", "summary", summary, "errorCode", code)).build();
    }

    private String safeArguments(Map<String, Object> arguments, ObjectMapper mapper) {
        try {
            Map<String, Object> safe = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
            safe.keySet().removeIf(key -> key.toLowerCase().contains("token") || key.toLowerCase().contains("key"));
            return mapper.writeValueAsString(safe);
        } catch (Exception ignored) { return "{}"; }
    }

    private long elapsed(long startedAt) { return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000); }

    @PreDestroy
    void close() { servers.forEach(server -> server.closeGracefully()); }

    static final class McpRoutingServlet extends HttpServlet {
        private final McpPersonalTokenService tokens;
        private final Map<String, HttpServletStreamableServerTransportProvider> providers;
        private final boolean enabled;
        private final Map<String, RateWindow> rates = new ConcurrentHashMap<>();

        McpRoutingServlet(McpPersonalTokenService tokens,
                          Map<String, HttpServletStreamableServerTransportProvider> providers, boolean enabled) {
            this.tokens = tokens;
            this.providers = providers;
            this.enabled = enabled;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            if (!enabled) { httpResponse.sendError(404); return; }
            try {
                String authorization = httpRequest.getHeader("Authorization");
                String raw = authorization != null && authorization.startsWith("Bearer ")
                        ? authorization.substring(7).trim() : null;
                McpPersonalTokenService.AuthenticatedToken token = tokens.authenticate(raw);
                if (!allow(token.id())) {
                    httpResponse.setStatus(429);
                    httpResponse.setHeader("Retry-After", "60");
                    httpResponse.setContentType("application/json;charset=UTF-8");
                    httpResponse.getWriter().write("{\"error\":\"rate_limited\",\"error_description\":\"调用过于频繁，请稍后重试\"}");
                    return;
                }
                httpRequest.setAttribute(TOKEN_CONTEXT_KEY, token);
                String key = token.scopes().contains(McpPersonalTokenService.LEDGER_READ)
                        && token.scopes().contains(McpPersonalTokenService.WORKTIME_READ) ? "both"
                        : token.scopes().iterator().next();
                providers.get(key).service(request, response);
            } catch (Exception exception) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.getWriter().write("{\"error\":\"invalid_token\",\"error_description\":\"MCP Token 无效、已过期或已撤销\"}");
            }
        }

        private boolean allow(String tokenId) {
            long minute = Instant.now().getEpochSecond() / 60;
            RateWindow window = rates.compute(tokenId, (ignored, current) ->
                    current == null || current.minute != minute ? new RateWindow(minute) : current);
            return window.count.incrementAndGet() <= 120;
        }

        private static final class RateWindow {
            private final long minute;
            private final AtomicInteger count = new AtomicInteger();
            private RateWindow(long minute) { this.minute = minute; }
        }
    }
}
