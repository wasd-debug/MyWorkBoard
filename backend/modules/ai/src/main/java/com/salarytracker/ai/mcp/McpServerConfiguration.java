package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.platform.UnauthorizedException;
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
    /** Keep the routing guard aligned with the protocol versions supported by MCP SDK 2.0.1. */
    private static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of(
            "2024-11-05", "2025-03-26", "2025-06-18", "2025-11-25");
    static final String TOKEN_CONTEXT_KEY = "salary.mcp.token";
    private static final String BASE_URL_CONTEXT_KEY = "salary.mcp.base-url";
    private static final String CLIENT_CONTEXT_KEY = "salary.mcp.client";
    private static final String LEDGER_PREFIX = "ledger.";
    private static final String WORKTIME_PREFIX = "worktime.";
    private final List<McpSyncServer> servers = new ArrayList<>();

    @Bean
    ServletRegistrationBean<HttpServlet> mcpServletRegistration(
            DomainToolRegistry registry, McpPersonalTokenService tokens, ObjectMapper mapper,
            McpContentService contentService,
            McpExternalActionService externalActions, McpOperationsService operations,
            @Value("${app.mcp.enabled:true}") boolean enabled,
            @Value("${app.mcp.write-enabled:false}") boolean writeEnabled,
            @Value("${app.mcp.oauth-enabled:false}") boolean oauthEnabled) {
        Map<String, HttpServletStreamableServerTransportProvider> providers = new LinkedHashMap<>();
        List<String> enabledScopes = new ArrayList<>(List.of(
                McpPersonalTokenService.LEDGER_READ, McpPersonalTokenService.WORKTIME_READ));
        if (writeEnabled) enabledScopes.addAll(List.of(
                McpPersonalTokenService.LEDGER_PREPARE, McpPersonalTokenService.WORKTIME_PREPARE,
                McpPersonalTokenService.LEDGER_COMMIT, McpPersonalTokenService.WORKTIME_COMMIT));
        int combinations = 1 << enabledScopes.size();
        for (int mask = 0; mask < combinations; mask++) {
            Set<String> scopes = new java.util.LinkedHashSet<>();
            for (int index = 0; index < enabledScopes.size(); index++) {
                if ((mask & (1 << index)) != 0) scopes.add(enabledScopes.get(index));
            }
            providers.put(scopeKey(scopes), provider(registry, tokens, mapper, contentService,
                    externalActions, Set.copyOf(scopes)));
        }
        McpRoutingServlet servlet = new McpRoutingServlet(tokens, operations, providers, enabled, writeEnabled, oauthEnabled);
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(servlet, "/mcp", "/mcp/*");
        registration.setName("mcpStreamableHttp");
        registration.setAsyncSupported(true);
        registration.setLoadOnStartup(1);
        return registration;
    }

    private HttpServletStreamableServerTransportProvider provider(
            DomainToolRegistry registry, McpPersonalTokenService tokens, ObjectMapper mapper,
            McpContentService contentService,
            McpExternalActionService externalActions, Set<String> scopes) {
        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint("/mcp")
                .keepAliveInterval(Duration.ofSeconds(30))
                .maxRequestSize(1024 * 1024)
                .contextExtractor(request -> McpTransportContext.create(Map.of(
                        TOKEN_CONTEXT_KEY, request.getAttribute(TOKEN_CONTEXT_KEY),
                        BASE_URL_CONTEXT_KEY, request.getAttribute(BASE_URL_CONTEXT_KEY),
                        CLIENT_CONTEXT_KEY, request.getAttribute(CLIENT_CONTEXT_KEY))))
                .build();
        List<McpServerFeatures.SyncToolSpecification> specifications = new ArrayList<>(domainSpecifications(
                registry, tokens, mapper, externalActions, scopes));
        if (hasActionScope(scopes)) {
            specifications.addAll(actionSpecifications(registry, tokens, mapper, externalActions, scopes));
        }
        var resourceSpecifications = contentService.resources(scopes);
        var resourceTemplateSpecifications = contentService.resourceTemplates(scopes);
        var promptSpecifications = contentService.prompts(scopes);
        McpSyncServer server = McpServer.sync(transport)
                .serverInfo("salary-sync", "1.0.0")
                .instructions((hasCommitScope(scopes)
                        ? "个人工作台 MCP。R2 prepare 经网站批准后可使用 agent.action.commit 单次提交；R3/R4 不允许 MCP commit。所有工具按 Token 用户、scope 和账本范围执行。"
                        : hasPrepareScope(scopes)
                        ? "个人工作台 MCP。prepare 只生成待确认 action；批准后仍需具备 commit scope 才能提交。所有工具按 Token 用户、scope 和账本范围执行。"
                        : "个人工作台只读 MCP。所有工具均按 Token 用户、scope 和账本范围执行。")
                        + "记账前先用 ledger.books.list 获取账本 ID，再用 ledger.account.list、ledger.category.list 查询该账本已有账户和分类；"
                        + "提及商户时用 ledger.merchant.list 查询已有商户。只能使用返回的真实 ID，收入和支出必须匹配对应类型的二级分类，"
                        + "不得编造名称或 ID；找不到或存在多个候选时向用户询问。目录工具需要 mcp:ledger:read scope。")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(false).resources(false, false).prompts(false).build())
                .strictToolNameValidation(false)
                .requestTimeout(Duration.ofSeconds(30))
                .tools(specifications)
                .resources(resourceSpecifications)
                .resourceTemplates(resourceTemplateSpecifications)
                .prompts(promptSpecifications)
                .build();
        servers.add(server);
        return transport;
    }

    List<McpServerFeatures.SyncToolSpecification> domainSpecifications(
            DomainToolRegistry registry, McpPersonalTokenService tokens, ObjectMapper mapper,
            McpExternalActionService externalActions, Set<String> scopes) {
        return registry.definitions().stream()
                .filter(definition -> visible(definition.name(), scopes))
                .filter(definition -> !definition.name().endsWith(".commit"))
                .map(definition -> specification(definition, registry, tokens, mapper, externalActions))
                .toList();
    }

    private McpServerFeatures.SyncToolSpecification specification(
            ToolDefinition definition, DomainToolRegistry registry, McpPersonalTokenService tokens,
            ObjectMapper mapper, McpExternalActionService externalActions) {
        Map<String, Object> schema = mapper.convertValue(definition.inputSchema(), new TypeReference<>() { });
        McpSchema.Tool tool = McpSchema.Tool.builder(definition.name(), schema)
                .description(definition.description())
                .build();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler((exchange, request) -> invoke(exchange.transportContext(), request.arguments(),
                        definition, registry, tokens, mapper, externalActions))
                .build();
    }

    private McpSchema.CallToolResult invoke(McpTransportContext context, Map<String, Object> arguments,
                                             ToolDefinition definition, DomainToolRegistry registry,
                                             McpPersonalTokenService tokens, ObjectMapper mapper,
                                             McpExternalActionService externalActions) {
        long startedAt = System.nanoTime();
        McpPersonalTokenService.AuthenticatedToken token =
                (McpPersonalTokenService.AuthenticatedToken) context.get(TOKEN_CONTEXT_KEY);
        String status = "FAILED";
        String summary = null;
        String errorCode = null;
        try {
            requireScope(token, definition.name());
            if ("DISABLED".equals(token.highRiskPolicy())
                    && (definition.riskLevel() == com.salarytracker.ai.tool.ToolRisk.R3
                    || definition.riskLevel() == com.salarytracker.ai.tool.ToolRisk.R4)) {
                throw new SecurityException("该 Token 已禁用高风险操作");
            }
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
            if (definition.name().startsWith(LEDGER_PREFIX) && result.actionId() != null) {
                externalActions.requireActionBook(token, result.actionId(), tokens);
            }
            McpExternalActionService.Binding binding = definition.name().endsWith(".prepare")
                    ? externalActions.bind(token, definition,
                    new ToolResult(result.status(), result.summary(), structured, result.actionId(),
                            result.confirmationUrl(), result.expiresAt(), result.traceId()),
                    (String) context.get(CLIENT_CONTEXT_KEY), (String) context.get(BASE_URL_CONTEXT_KEY))
                    : new McpExternalActionService.Binding(result.actionId(), result.confirmationUrl());
            status = result.status().name();
            summary = result.summary();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", result.status().name().toLowerCase());
            payload.put("summary", result.summary());
            payload.put("structuredContent", mapper.convertValue(structured, Object.class));
            payload.put("actionId", binding.actionId());
            payload.put("confirmationUrl", binding.confirmationUrl());
            payload.put("expiresAt", result.expiresAt());
            return McpSchema.CallToolResult.builder()
                    // Text-only MCP hosts must receive the same scoped data and action metadata.
                    .addTextContent(result.summary() + "\n" + mapper.valueToTree(payload))
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
        boolean prepare = toolName.endsWith(".prepare");
        String required = toolName.startsWith(LEDGER_PREFIX)
                ? prepare ? McpPersonalTokenService.LEDGER_PREPARE : McpPersonalTokenService.LEDGER_READ
                : prepare ? McpPersonalTokenService.WORKTIME_PREPARE : McpPersonalTokenService.WORKTIME_READ;
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

    /**
     * MCP visibility is derived from the domain tool namespace and scope, rather than
     * from a hand-maintained allow-list. This keeps newly registered ledger/worktime
     * tools available to MCP automatically while preserving the read/prepare boundary.
     */
    static boolean visible(String name, Set<String> scopes) {
        // Domain commit tools are intentionally never advertised through MCP.
        // External clients use agent.action.commit after the website approval flow.
        if (name.endsWith(".commit")) return false;
        boolean prepare = name.endsWith(".prepare");
        return name.startsWith(LEDGER_PREFIX) && scopes.contains(prepare
                ? McpPersonalTokenService.LEDGER_PREPARE : McpPersonalTokenService.LEDGER_READ)
                || name.startsWith(WORKTIME_PREFIX) && scopes.contains(prepare
                ? McpPersonalTokenService.WORKTIME_PREPARE : McpPersonalTokenService.WORKTIME_READ);
    }

    private List<McpServerFeatures.SyncToolSpecification> actionSpecifications(
            DomainToolRegistry registry, McpPersonalTokenService tokens, ObjectMapper mapper,
            McpExternalActionService externalActions, Set<String> scopes) {
        List<McpServerFeatures.SyncToolSpecification> result = new ArrayList<>();
        result.add(actionSpecification("agent.action.get", "查询当前 PAT 创建的外部 action 状态。",
                objectSchema(mapper, Map.of("actionId", stringSchema(mapper, "action ID")), List.of("actionId")),
                registry, tokens, mapper, externalActions));
        result.add(actionSpecification("agent.actions.list", "列出当前 PAT 最近创建的外部 actions。",
                objectSchema(mapper, Map.of("limit", integerSchema(mapper, "返回数量，1-50")), List.of()),
                registry, tokens, mapper, externalActions));
        result.add(actionSpecification("agent.action.cancel", "取消当前 PAT 创建且尚未执行的外部 action。",
                objectSchema(mapper, Map.of("actionId", stringSchema(mapper, "action ID")), List.of("actionId")),
                registry, tokens, mapper, externalActions));
        if (hasCommitScope(scopes)) {
            result.add(actionSpecification("agent.action.commit",
                    "提交当前 PAT 创建、已在网站批准且风险等级为 R2 的 action；重复调用返回首次结果。",
                    objectSchema(mapper, Map.of("actionId", stringSchema(mapper, "已批准的 action ID")),
                            List.of("actionId")), registry, tokens, mapper, externalActions));
        }
        return result;
    }

    private McpServerFeatures.SyncToolSpecification actionSpecification(
            String name, String description, Map<String, Object> schema,
            DomainToolRegistry registry, McpPersonalTokenService tokens, ObjectMapper mapper,
            McpExternalActionService externalActions) {
        McpSchema.Tool tool = McpSchema.Tool.builder(name, schema).description(description).build();
        return McpServerFeatures.SyncToolSpecification.builder().tool(tool)
                .callHandler((exchange, request) -> invokeAction(exchange.transportContext(), name,
                        request.arguments(), registry, tokens, mapper, externalActions)).build();
    }

    private McpSchema.CallToolResult invokeAction(McpTransportContext context, String name,
                                                   Map<String, Object> arguments,
                                                   DomainToolRegistry registry,
                                                   McpPersonalTokenService tokens, ObjectMapper mapper,
                                                   McpExternalActionService externalActions) {
        long startedAt = System.nanoTime();
        McpPersonalTokenService.AuthenticatedToken token =
                (McpPersonalTokenService.AuthenticatedToken) context.get(TOKEN_CONTEXT_KEY);
        String status = "FAILED";
        String summary = null;
        String errorCode = null;
        try {
            if (token == null || !hasActionScope(token.scopes())) {
                throw new SecurityException("Token 缺少 prepare 或 commit scope");
            }
            Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
            Object content;
            if ("agent.action.commit".equals(name)) {
                var authorities = token.user().authorities().stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(token.user(), null, authorities));
                McpExternalActionService.CommitResult result = externalActions.commitForToken(
                        token, requiredActionId(safeArguments), registry, tokens);
                content = result;
                status = result.status().name();
                summary = result.summary();
            } else if ("agent.action.get".equals(name)) {
                content = externalActions.getForToken(token, requiredActionId(safeArguments));
                summary = "已查询外部 action";
            } else if ("agent.action.cancel".equals(name)) {
                content = externalActions.cancelForToken(token, requiredActionId(safeArguments));
                summary = "外部 action 已取消";
            } else {
                int limit = safeArguments.get("limit") instanceof Number number ? number.intValue() : 20;
                content = externalActions.listForToken(token, limit);
                summary = "已返回外部 action 列表";
            }
            if (!"agent.action.commit".equals(name)) status = "COMPLETED";
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("status", status.toLowerCase());
            payload.put("summary", summary);
            payload.put("structuredContent", mapper.convertValue(content, Object.class));
            payload.put("actionId", "agent.action.commit".equals(name) ? requiredActionId(safeArguments) : null);
            payload.put("confirmationUrl", null);
            payload.put("expiresAt", null);
            return McpSchema.CallToolResult.builder().addTextContent(summary)
                    .structuredContent(payload).isError(false).build();
        } catch (SecurityException exception) {
            status = "DENIED"; summary = exception.getMessage(); errorCode = "MCP_SCOPE_DENIED";
            return error(summary, errorCode);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            summary = exception.getMessage(); errorCode = "INVALID_ACTION";
            return error(summary, errorCode);
        } finally {
            SecurityContextHolder.clearContext();
            if (token != null) {
                try { tokens.audit(token, name, status, elapsed(startedAt), safeArguments(arguments, mapper), summary, errorCode); }
                catch (Exception ignored) { }
            }
        }
    }

    private String requiredActionId(Map<String, Object> arguments) {
        Object value = arguments.get("actionId");
        if (!(value instanceof String actionId) || actionId.isBlank()) throw new IllegalArgumentException("actionId 不能为空");
        return actionId;
    }

    private Map<String, Object> objectSchema(ObjectMapper mapper, Map<String, JsonNode> properties, List<String> required) {
        var schema = mapper.createObjectNode().put("type", "object");
        var propertyNode = schema.putObject("properties");
        properties.forEach(propertyNode::set);
        var requiredNode = schema.putArray("required");
        required.forEach(requiredNode::add);
        schema.put("additionalProperties", false);
        return mapper.convertValue(schema, new TypeReference<>() { });
    }

    private JsonNode stringSchema(ObjectMapper mapper, String description) {
        return mapper.createObjectNode().put("type", "string").put("description", description);
    }

    private JsonNode integerSchema(ObjectMapper mapper, String description) {
        return mapper.createObjectNode().put("type", "integer").put("minimum", 1).put("maximum", 50)
                .put("description", description);
    }

    private static boolean hasPrepareScope(Set<String> scopes) {
        return scopes.contains(McpPersonalTokenService.LEDGER_PREPARE)
                || scopes.contains(McpPersonalTokenService.WORKTIME_PREPARE);
    }

    private static boolean hasCommitScope(Set<String> scopes) {
        return scopes.contains(McpPersonalTokenService.LEDGER_COMMIT)
                || scopes.contains(McpPersonalTokenService.WORKTIME_COMMIT);
    }

    private static boolean hasActionScope(Set<String> scopes) {
        return hasPrepareScope(scopes) || hasCommitScope(scopes);
    }

    private static String scopeKey(Set<String> scopes) {
        return scopes.stream().sorted().reduce((left, right) -> left + "|" + right).orElse("none");
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
        private final McpOperationsService operations;
        private final Map<String, HttpServletStreamableServerTransportProvider> providers;
        private final boolean enabled;
        private final boolean writeEnabled;
        private final boolean oauthEnabled;
        private final Map<String, RateWindow> rates = new ConcurrentHashMap<>();

        McpRoutingServlet(McpPersonalTokenService tokens, McpOperationsService operations,
                          Map<String, HttpServletStreamableServerTransportProvider> providers,
                          boolean enabled, boolean writeEnabled, boolean oauthEnabled) {
            this.tokens = tokens;
            this.operations = operations;
            this.providers = providers;
            this.enabled = enabled;
            this.writeEnabled = writeEnabled;
            this.oauthEnabled = oauthEnabled;
        }

        @Override
        public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            if (!enabled) { httpResponse.sendError(404); return; }
            String protocolVersion = httpRequest.getHeader("MCP-Protocol-Version");
            if (protocolVersion != null && !protocolVersion.isBlank()
                    && !SUPPORTED_PROTOCOL_VERSIONS.contains(protocolVersion.trim())) {
                httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.getWriter().write("{\"error\":\"unsupported_protocol_version\",\"error_description\":\"支持 MCP 2024-11-05、2025-03-26、2025-06-18、2025-11-25\"}");
                return;
            }
            try {
                String authorization = httpRequest.getHeader("Authorization");
                String raw = authorization != null && authorization.startsWith("Bearer ")
                        ? authorization.substring(7).trim() : null;
                String requestIp = requestIp(httpRequest);
                String userAgent = httpRequest.getHeader("User-Agent");
                McpPersonalTokenService.AuthenticatedToken token = tokens.authenticate(raw, oauthEnabled, requestIp, userAgent);
                if (!allow(token.id(), token.rateLimitPerMinute())) {
                    operations.event(token.user().id(), token.oauthClientId(), token.id(), "mcp.request", "RATE_LIMITED",
                            new McpOperationsService.RequestContext(requestIp, userAgent), Map.of());
                    httpResponse.setStatus(429);
                    httpResponse.setHeader("Retry-After", "60");
                    httpResponse.setContentType("application/json;charset=UTF-8");
                    httpResponse.getWriter().write("{\"error\":\"rate_limited\",\"error_description\":\"调用过于频繁，请稍后重试\"}");
                    return;
                }
                httpRequest.setAttribute(TOKEN_CONTEXT_KEY, token);
                Set<String> effectiveScopes = new java.util.LinkedHashSet<>(token.scopes());
                if (!writeEnabled) {
                    effectiveScopes.remove(McpPersonalTokenService.LEDGER_PREPARE);
                    effectiveScopes.remove(McpPersonalTokenService.WORKTIME_PREPARE);
                    effectiveScopes.remove(McpPersonalTokenService.LEDGER_COMMIT);
                    effectiveScopes.remove(McpPersonalTokenService.WORKTIME_COMMIT);
                }
                String key = scopeKey(effectiveScopes);
                httpRequest.setAttribute(BASE_URL_CONTEXT_KEY, baseUrl(httpRequest));
                httpRequest.setAttribute(CLIENT_CONTEXT_KEY, httpRequest.getHeader("User-Agent") == null
                        ? "MCP Client" : httpRequest.getHeader("User-Agent"));
                providers.get(key).service(request, response);
                operations.event(token.user().id(), token.oauthClientId(), token.id(), "mcp.request", "ACCEPTED",
                        new McpOperationsService.RequestContext(requestIp, userAgent),
                        Map.of("protocolVersion", protocolVersion == null || protocolVersion.isBlank()
                                ? "negotiation" : protocolVersion));
            } catch (UnauthorizedException exception) {
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                String base = baseUrl(httpRequest);
                httpResponse.setHeader("WWW-Authenticate", "Bearer resource_metadata=\"" + base
                        + "/.well-known/oauth-protected-resource/mcp\", error=\"invalid_token\"");
                httpResponse.setContentType("application/json;charset=UTF-8");
                httpResponse.getWriter().write("{\"error\":\"invalid_token\",\"error_description\":\"MCP Token 无效、已过期或已撤销\"}");
            }
        }

        private String requestIp(HttpServletRequest request) {
            String forwarded = request.getHeader("X-Forwarded-For");
            return forwarded == null || forwarded.isBlank()
                    ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
        }

        private String baseUrl(HttpServletRequest request) {
            String scheme = request.getHeader("X-Forwarded-Proto");
            if (scheme == null || scheme.isBlank()) scheme = request.getScheme();
            String host = request.getHeader("X-Forwarded-Host");
            if (host == null || host.isBlank()) host = request.getHeader("Host");
            return scheme + "://" + host;
        }

        private boolean allow(String tokenId, int limit) {
            long minute = Instant.now().getEpochSecond() / 60;
            RateWindow window = rates.compute(tokenId, (ignored, current) ->
                    current == null || current.minute != minute ? new RateWindow(minute) : current);
            return window.count.incrementAndGet() <= Math.max(1, Math.min(limit, 600));
        }

        private static final class RateWindow {
            private final long minute;
            private final AtomicInteger count = new AtomicInteger();
            private RateWindow(long minute) { this.minute = minute; }
        }
    }
}
