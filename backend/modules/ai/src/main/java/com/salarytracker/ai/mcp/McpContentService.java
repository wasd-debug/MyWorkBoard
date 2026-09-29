package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ToolResult;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class McpContentService {
    private static final String MIME_MARKDOWN = "text/markdown;charset=UTF-8";
    private static final String MIME_JSON = "application/json;charset=UTF-8";
    private static final String LEDGER_OVERVIEW_TEMPLATE = "workbench://ledger/{bookId}/overview";
    private static final String LEDGER_REPORT_TEMPLATE = "workbench://ledger/{bookId}/reports/{period}";
    private static final String WORKTIME_RECORDS_TEMPLATE = "workbench://worktime/records/{from}/{to}";

    private final DomainToolRegistry registry;
    private final McpPersonalTokenService tokens;
    private final ObjectMapper mapper;

    public McpContentService(DomainToolRegistry registry, McpPersonalTokenService tokens, ObjectMapper mapper) {
        this.registry = registry;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    List<McpServerFeatures.SyncResourceSpecification> resources(Set<String> scopes) {
        List<McpServerFeatures.SyncResourceSpecification> resources = new ArrayList<>();
        resources.add(staticResource("workbench://help/mcp", "mcp-help", "MCP 使用说明",
                "连接、认证、只读资源和受控写入的基本说明。", this::mcpHelp));
        resources.add(staticResource("workbench://help/tools", "tool-help", "工具使用说明",
                "当前 Token 可使用的工具类型与安全边界。", context -> toolHelp(scopes)));
        resources.add(staticResource("workbench://help/scopes", "scope-help", "Scope 说明",
                "当前 Token scope 与能力映射。", context -> scopeHelp(scopes)));
        if (scopes.contains(McpPersonalTokenService.LEDGER_READ)) {
            resources.add(dynamicResource("workbench://ledger/books", "ledger-books", "可访问账本",
                    "列出当前 Token 可访问的账本，不暴露未授权账本。",
                    (context, uri) -> invoke(context, "ledger.books.list", mapper.createObjectNode(), uri)));
        }
        if (scopes.contains(McpPersonalTokenService.WORKTIME_READ)) {
            resources.add(dynamicResource("workbench://worktime/settings", "worktime-settings", "工时设置",
                    "读取当前用户的工时与薪资计算设置。",
                    (context, uri) -> invoke(context, "worktime.settings.get", mapper.createObjectNode(), uri)));
        }
        return resources;
    }

    List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplates(Set<String> scopes) {
        List<McpServerFeatures.SyncResourceTemplateSpecification> templates = new ArrayList<>();
        if (scopes.contains(McpPersonalTokenService.LEDGER_READ)) {
            templates.add(template(LEDGER_OVERVIEW_TEMPLATE, "ledger-overview", "账本概览",
                    "读取指定账本最近 30 天的概览。", this::readLedgerOverview));
            templates.add(template(LEDGER_REPORT_TEMPLATE, "ledger-period-report", "账本月度报表",
                    "读取指定账本和月份（YYYY-MM）的收支、分类与预算汇总。", this::readLedgerReport));
        }
        if (scopes.contains(McpPersonalTokenService.WORKTIME_READ)) {
            templates.add(template(WORKTIME_RECORDS_TEMPLATE, "worktime-records", "工时记录",
                    "按起止日期读取当前用户工时，单次跨度最多 366 天。", this::readWorktimeRecords));
        }
        return templates;
    }

    List<McpServerFeatures.SyncPromptSpecification> prompts(Set<String> scopes) {
        List<McpServerFeatures.SyncPromptSpecification> prompts = new ArrayList<>();
        if (scopes.contains(McpPersonalTokenService.LEDGER_READ)) {
            prompts.add(prompt("monthly-review", "月度复盘", "基于账本资源生成月度收支复盘。",
                    List.of(argument("bookId", "账本 ID", true), argument("period", "月份 YYYY-MM", true)),
                    (context, request) -> promptResult("月度账本复盘模板", "请读取资源 workbench://ledger/"
                            + authorizedBook(context, request) + "/reports/"
                            + validPeriod(requiredArgument(request, "period"))
                            + "，总结收入、支出、结余、主要分类、预算偏差和可执行建议。请标明数据周期，不要虚构缺失数据。")));
            prompts.add(prompt("ledger-summary", "账本摘要", "生成指定账本最近 30 天摘要。",
                    List.of(argument("bookId", "账本 ID", true)),
                    (context, request) -> promptResult("账本摘要模板", "请读取资源 workbench://ledger/"
                            + authorizedBook(context, request)
                            + "/overview，概括最近 30 天收支、趋势和异常变化，并列出需要用户确认的问题。")));
        }
        if (scopes.contains(McpPersonalTokenService.WORKTIME_READ)) {
            prompts.add(prompt("worktime-makeup", "工时补录检查", "检查日期范围内可能缺失的工时记录。",
                    List.of(argument("from", "起始日期 YYYY-MM-DD", true), argument("to", "结束日期 YYYY-MM-DD", true)),
                    (context, request) -> {
                        requireScope(requireToken(context), McpPersonalTokenService.WORKTIME_READ);
                        DateRange range = validRange(requiredArgument(request, "from"), requiredArgument(request, "to"));
                        return promptResult("工时补录模板", "请读取资源 workbench://worktime/records/"
                                + range.from() + "/" + range.to()
                                + "，识别工作日缺失、时间异常或重复记录。只提出补录建议；如需写入，必须使用 prepare/确认/commit 流程。");
                    }));
        }
        return prompts;
    }

    private McpServerFeatures.SyncResourceSpecification staticResource(
            String uri, String name, String title, String description, StaticContent content) {
        McpSchema.Resource resource = McpSchema.Resource.builder(uri, name).title(title)
                .description(description).mimeType(MIME_MARKDOWN).build();
        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) ->
                textResource(request.uri(), MIME_MARKDOWN, readStatic(
                        exchange.transportContext(), content)));
    }

    private McpServerFeatures.SyncResourceSpecification dynamicResource(
            String uri, String name, String title, String description, ResourceReader reader) {
        McpSchema.Resource resource = McpSchema.Resource.builder(uri, name).title(title)
                .description(description).mimeType(MIME_JSON).build();
        return new McpServerFeatures.SyncResourceSpecification(resource, (exchange, request) ->
                textResource(request.uri(), MIME_JSON, reader.read(exchange.transportContext(), request.uri())));
    }

    private McpServerFeatures.SyncResourceTemplateSpecification template(
            String uri, String name, String title, String description, ResourceReader reader) {
        McpSchema.ResourceTemplate template = McpSchema.ResourceTemplate.builder(uri, name).title(title)
                .description(description).mimeType(MIME_JSON).build();
        return new McpServerFeatures.SyncResourceTemplateSpecification(template, (exchange, request) ->
                textResource(request.uri(), MIME_JSON, reader.read(exchange.transportContext(), request.uri())));
    }

    private McpServerFeatures.SyncPromptSpecification prompt(
            String name, String title, String description, List<McpSchema.PromptArgument> arguments, PromptReader reader) {
        McpSchema.Prompt prompt = McpSchema.Prompt.builder(name).title(title).description(description)
                .arguments(arguments).build();
        return new McpServerFeatures.SyncPromptSpecification(prompt, (exchange, request) -> {
            requireToken(exchange.transportContext());
            return reader.read(exchange.transportContext(), request);
        });
    }

    private String readLedgerOverview(McpTransportContext context, String uri) {
        String prefix = "workbench://ledger/";
        String suffix = "/overview";
        if (!uri.startsWith(prefix) || !uri.endsWith(suffix)) throw new IllegalArgumentException("无效的账本概览资源 URI");
        String bookId = uri.substring(prefix.length(), uri.length() - suffix.length());
        requireSegment(bookId, "bookId");
        LocalDate to = LocalDate.now();
        ObjectNode input = mapper.createObjectNode().put("bookId", bookId)
                .put("from", to.minusDays(29).toString()).put("to", to.toString());
        return invoke(context, "ledger.overview", input, uri);
    }

    private String readLedgerReport(McpTransportContext context, String uri) {
        String prefix = "workbench://ledger/";
        String marker = "/reports/";
        int markerIndex = uri.indexOf(marker, prefix.length());
        if (!uri.startsWith(prefix) || markerIndex < 0) throw new IllegalArgumentException("无效的账本报表资源 URI");
        String bookId = uri.substring(prefix.length(), markerIndex);
        String period = validPeriod(uri.substring(markerIndex + marker.length()));
        requireSegment(bookId, "bookId");
        YearMonth month = YearMonth.parse(period);
        ObjectNode input = mapper.createObjectNode().put("bookId", bookId)
                .put("from", month.atDay(1).toString()).put("to", month.atEndOfMonth().toString());
        return invoke(context, "ledger.reports.summary", input, uri);
    }

    private String readWorktimeRecords(McpTransportContext context, String uri) {
        String prefix = "workbench://worktime/records/";
        if (!uri.startsWith(prefix)) throw new IllegalArgumentException("无效的工时资源 URI");
        String[] values = uri.substring(prefix.length()).split("/", -1);
        if (values.length != 2) throw new IllegalArgumentException("工时资源 URI 必须包含 from 和 to");
        DateRange range = validRange(values[0], values[1]);
        ObjectNode input = mapper.createObjectNode().put("from", range.from().toString())
                .put("to", range.to().toString()).put("limit", 100).put("offset", 0);
        return invoke(context, "worktime.records.search", input, uri);
    }

    private String invoke(McpTransportContext context, String toolName, JsonNode input, String uri) {
        long startedAt = System.nanoTime();
        McpPersonalTokenService.AuthenticatedToken token = requireToken(context);
        String status = "FAILED";
        String summary = null;
        try {
            if (toolName.startsWith("ledger.")) {
                requireScope(token, McpPersonalTokenService.LEDGER_READ);
                if (input.hasNonNull("bookId")) tokens.requireBook(token, input.path("bookId").asText());
            } else {
                requireScope(token, McpPersonalTokenService.WORKTIME_READ);
            }
            var authorities = token.user().authorities().stream().map(SimpleGrantedAuthority::new).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(token.user(), null, authorities));
            ToolResult result = registry.invoke(toolName, input);
            JsonNode content = result.structuredContent() == null ? mapper.createObjectNode() : result.structuredContent();
            if ("ledger.books.list".equals(toolName) && content.isArray() && !token.bookIds().isEmpty()) {
                var filtered = mapper.createArrayNode();
                content.forEach(item -> {
                    if (token.bookIds().contains(item.path("id").asText())
                            || token.bookIds().contains(item.path("publicId").asText())) filtered.add(item);
                });
                content = filtered;
            }
            status = "COMPLETED";
            summary = result.summary();
            return pretty(content);
        } catch (SecurityException exception) {
            status = "DENIED";
            summary = exception.getMessage();
            throw exception;
        } catch (RuntimeException exception) {
            summary = exception.getMessage();
            throw exception;
        } finally {
            SecurityContextHolder.clearContext();
            try {
                tokens.audit(token, "resources/read", status, elapsed(startedAt),
                        mapper.createObjectNode().put("uri", uri).toString(), summary, null);
            } catch (Exception ignored) { }
        }
    }

    private McpPersonalTokenService.AuthenticatedToken requireToken(McpTransportContext context) {
        McpPersonalTokenService.AuthenticatedToken token =
                (McpPersonalTokenService.AuthenticatedToken) context.get(McpServerConfiguration.TOKEN_CONTEXT_KEY);
        if (token == null) throw new SecurityException("MCP Token 上下文缺失");
        return token;
    }

    private void requireScope(McpPersonalTokenService.AuthenticatedToken token, String scope) {
        if (!token.scopes().contains(scope)) throw new SecurityException("Token 缺少资源所需 scope");
    }

    private McpSchema.ReadResourceResult textResource(String uri, String mimeType, String text) {
        return McpSchema.ReadResourceResult.builder(List.of(
                McpSchema.TextResourceContents.builder(uri, text).mimeType(mimeType).build())).build();
    }

    private McpSchema.PromptArgument argument(String name, String description, boolean required) {
        return McpSchema.PromptArgument.builder(name).description(description).required(required).build();
    }

    private McpSchema.GetPromptResult promptResult(String description, String text) {
        McpSchema.PromptMessage message = McpSchema.PromptMessage.builder(
                McpSchema.Role.USER, new McpSchema.TextContent(text)).build();
        return McpSchema.GetPromptResult.builder(List.of(message)).description(description).build();
    }

    private String requiredArgument(McpSchema.GetPromptRequest request, String name) {
        Object value = request.arguments() == null ? null : request.arguments().get(name);
        if (!(value instanceof String text) || text.isBlank()) throw new IllegalArgumentException(name + " 不能为空");
        requireSegment(text, name);
        return text.trim();
    }

    private String authorizedBook(McpTransportContext context, McpSchema.GetPromptRequest request) {
        McpPersonalTokenService.AuthenticatedToken token = requireToken(context);
        requireScope(token, McpPersonalTokenService.LEDGER_READ);
        String bookId = requiredArgument(request, "bookId");
        tokens.requireBook(token, bookId);
        return bookId;
    }

    private String readStatic(McpTransportContext context, StaticContent content) {
        requireToken(context);
        return content.read(context);
    }

    private String validPeriod(String value) {
        try {
            return YearMonth.parse(value).toString();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("period 必须是 YYYY-MM");
        }
    }

    private DateRange validRange(String fromValue, String toValue) {
        try {
            LocalDate from = LocalDate.parse(fromValue);
            LocalDate to = LocalDate.parse(toValue);
            long days = ChronoUnit.DAYS.between(from, to);
            if (days < 0) throw new IllegalArgumentException("from 不能晚于 to");
            if (days > 366) throw new IllegalArgumentException("MCP 单次查询日期跨度不能超过 366 天");
            return new DateRange(from, to);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("日期必须是 YYYY-MM-DD");
        }
    }

    private void requireSegment(String value, String name) {
        if (value == null || value.isBlank() || value.contains("/") || value.contains("..")) {
            throw new IllegalArgumentException(name + " 格式无效");
        }
    }

    private String pretty(JsonNode content) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(content);
        } catch (Exception exception) {
            throw new IllegalStateException("资源序列化失败", exception);
        }
    }

    private String mcpHelp(McpTransportContext context) {
        requireToken(context);
        return "# 工作台 MCP\n\n"
                + "- Resources 只提供帮助文档与经过权限过滤的只读业务摘要。\n"
                + "- 写入仍使用 prepare → 网站确认 → commit，不可通过资源或 Prompt 绕过。\n"
                + "- PAT/OAuth Token、密钥、数据库实体全集和内部审计参数不会作为资源暴露。\n"
                + "- 动态日期资源单次跨度最多 366 天。\n";
    }

    private String toolHelp(Set<String> scopes) {
        return "# 可用能力\n\n"
                + (scopes.contains(McpPersonalTokenService.LEDGER_READ) ? "- 账本查询、账本概览与月度报表。\n" : "")
                + (scopes.contains(McpPersonalTokenService.WORKTIME_READ) ? "- 工时设置与日期范围记录查询。\n" : "")
                + (scopes.stream().anyMatch(scope -> scope.endsWith(":prepare")) ? "- 可创建待确认 action，但不会直接写入。\n" : "")
                + (scopes.stream().anyMatch(scope -> scope.endsWith(":commit")) ? "- 可提交已在网站批准的低风险 action。\n" : "")
                + "- 使用 tools/list 获取精确工具清单。\n";
    }

    private String scopeHelp(Set<String> scopes) {
        return "# 当前 Token Scopes\n\n" + (scopes.isEmpty() ? "- 无领域 scope\n"
                : scopes.stream().sorted().map(scope -> "- `" + scope + "`").reduce((a, b) -> a + "\n" + b).orElse("") + "\n")
                + "\nScope 只决定可见能力，实际调用仍会复检用户、账本范围与领域权限。\n";
    }

    private long elapsed(long startedAt) {
        return Math.max(0, (System.nanoTime() - startedAt) / 1_000_000);
    }

    private record DateRange(LocalDate from, LocalDate to) { }

    @FunctionalInterface
    private interface StaticContent {
        String read(McpTransportContext context);
    }

    @FunctionalInterface
    private interface ResourceReader {
        String read(McpTransportContext context, String uri);
    }

    @FunctionalInterface
    private interface PromptReader {
        McpSchema.GetPromptResult read(McpTransportContext context, McpSchema.GetPromptRequest request);
    }
}
