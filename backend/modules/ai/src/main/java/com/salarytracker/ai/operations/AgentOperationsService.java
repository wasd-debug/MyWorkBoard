package com.salarytracker.ai.operations;

import com.salarytracker.identity.CurrentUserResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
public class AgentOperationsService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;

    public AgentOperationsService(JdbcTemplate jdbc, CurrentUserResolver currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    public Metrics metrics(String preset, String granularity, String from, String to) {
        long userId = currentUser.id();
        Window window = resolveWindow(preset, granularity, from, to);
        List<TurnRow> turns = jdbc.query("""
                SELECT status, TIMESTAMPDIFF(MICROSECOND, started_at, completed_at) / 1000 duration_ms,
                       created_at, error_message, provider_type, model_name
                FROM agent_turn WHERE user_id=? AND created_at>=? AND created_at<?
                """, (r, n) -> new TurnRow(r.getString("status"), nullableLong(r, "duration_ms"),
                instant(r, "created_at"), r.getString("error_message"), r.getString("provider_type"), r.getString("model_name")),
                userId, window.from(), window.to());
        List<UsageRow> usage = jdbc.query("""
                SELECT provider_type, model_name, currency, total_tokens, input_tokens, output_tokens,
                       cache_hit_tokens, cache_miss_tokens, reasoning_tokens, first_token_ms, duration_ms,
                       estimated_total_cost, created_at
                FROM ai_usage WHERE user_id=? AND created_at>=? AND created_at<?
                """, (r, n) -> new UsageRow(r.getString("provider_type"), r.getString("model_name"),
                r.getString("currency"), longValue(r, "total_tokens"), longValue(r, "input_tokens"), longValue(r, "output_tokens"),
                longValue(r, "cache_hit_tokens"), longValue(r, "cache_miss_tokens"), longValue(r, "reasoning_tokens"),
                longValue(r, "first_token_ms"), longValue(r, "duration_ms"), r.getBigDecimal("estimated_total_cost"), instant(r, "created_at")),
                userId, window.from(), window.to());
        List<ToolRow> tools = jdbc.query("""
                SELECT tool_name, status, duration_ms, created_at FROM agent_tool_call
                WHERE user_id=? AND created_at>=? AND created_at<?
                """, (r, n) -> new ToolRow(r.getString("tool_name"), r.getString("status"), longValue(r, "duration_ms"), instant(r, "created_at")),
                userId, window.from(), window.to());
        List<McpRow> mcp = jdbc.query("""
                SELECT event_type, status, created_at FROM mcp_protocol_event
                WHERE user_id=? AND created_at>=? AND created_at<?
                """, (r, n) -> new McpRow(r.getString("event_type"), r.getString("status"), instant(r, "created_at")),
                userId, window.from(), window.to());
        Budget budget = budget();
        String budgetCurrency = budget.currency();
        Summary summary = summarize(turns, usage, tools, mcp, budgetCurrency);
        List<TrendPoint> trend = trend(window, usage, turns, window.granularity(), budgetCurrency);
        return new Metrics(window.preset(), window.granularity(), window.from(), window.to(), summary, trend,
                modelCosts(usage), toolStats(tools), failures(turns, tools, mcp), budget,
                budgetProgress(userId, budget));
    }

    public Budget budget() {
        return jdbc.query("SELECT * FROM ai_usage_budget WHERE user_id=?", (r, n) -> new Budget(
                r.getString("currency"), r.getBigDecimal("daily_limit"), r.getBigDecimal("monthly_limit"),
                r.getBigDecimal("single_request_limit"), (Long) r.getObject("token_limit"), r.getString("enforcement_mode"),
                r.getBoolean("alert_at_50"), r.getBoolean("alert_at_80"), r.getBoolean("alert_at_100"), r.getLong("revision")),
                currentUser.id()).stream().findFirst().orElse(Budget.defaults());
    }

    @Transactional
    public Budget saveBudget(BudgetCommand command) {
        long userId = currentUser.id();
        if (command.revision() == null || command.revision() < 0) throw new IllegalArgumentException("预算修订号无效，请刷新后重试");
        String currency = command.currency() == null || command.currency().isBlank() ? "CNY" : command.currency().trim().toUpperCase(Locale.ROOT);
        // 本增量只提供预算观察和告警数据铺底；请求前硬拦截会在后续稳定化增量单独接入。
        String mode = "WARN";
        if (command.revision() == 0) {
            try {
                jdbc.update("""
                        INSERT INTO ai_usage_budget(user_id,currency,daily_limit,monthly_limit,enforcement_mode,revision)
                        VALUES(?,?,?,?,?,1)
                        """, userId, currency, positive(command.dailyLimit()), positive(command.monthlyLimit()), mode);
            } catch (DuplicateKeyException e) {
                throw new IllegalStateException("预算配置已被更新，请刷新后重试", e);
            }
        } else {
            int updated = jdbc.update("""
                    UPDATE ai_usage_budget SET currency=?,daily_limit=?,monthly_limit=?,enforcement_mode=?,revision=revision+1
                    WHERE user_id=? AND revision=?
                    """, currency, positive(command.dailyLimit()), positive(command.monthlyLimit()), mode,
                    userId, command.revision());
            if (updated != 1) throw new IllegalStateException("预算配置已被更新，请刷新后重试");
        }
        return budget();
    }

    private Window resolveWindow(String rawPreset, String rawGranularity, String from, String to) {
        String preset = normalizePreset(rawPreset);
        LocalDate today = LocalDate.now(ZONE);
        ZonedDateTime start;
        ZonedDateTime end;
        switch (preset) {
            case "THIS_WEEK" -> { start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZONE); end = start.plusWeeks(1); }
            case "THIS_MONTH" -> { start = today.withDayOfMonth(1).atStartOfDay(ZONE); end = start.plusMonths(1); }
            case "LAST_7_DAYS" -> { end = today.plusDays(1).atStartOfDay(ZONE); start = end.minusDays(7); }
            case "LAST_30_DAYS" -> { end = today.plusDays(1).atStartOfDay(ZONE); start = end.minusDays(30); }
            case "CUSTOM" -> {
                try { start = LocalDate.parse(from).atStartOfDay(ZONE); end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZONE); }
                catch (Exception e) { throw new IllegalArgumentException("自定义日期必须为 YYYY-MM-DD"); }
            }
            case "TODAY" -> { start = today.atStartOfDay(ZONE); end = start.plusDays(1); }
            default -> throw new IllegalArgumentException("不支持的统计范围");
        }
        if (!end.isAfter(start) || Duration.between(start, end).toDays() > 366) throw new IllegalArgumentException("统计范围必须在 1 到 366 天之间");
        String granularity = normalizeGranularity(rawGranularity, start, end);
        return new Window(preset, granularity, start.toInstant(), end.toInstant());
    }

    private String normalizePreset(String value) { return value == null || value.isBlank() ? "TODAY" : value; }
    private String normalizeGranularity(String value, ZonedDateTime start, ZonedDateTime end) {
        if (Set.of("HOUR", "DAY", "WEEK").contains(value)) return value;
        long days = Duration.between(start, end).toDays();
        return days <= 2 ? "HOUR" : days <= 62 ? "DAY" : "WEEK";
    }

    private Summary summarize(List<TurnRow> turns, List<UsageRow> usage, List<ToolRow> tools,
                              List<McpRow> mcp, String currency) {
        long success = turns.stream().filter(t -> "COMPLETED".equals(t.status())).count();
        long failed = turns.stream().filter(t -> "FAILED".equals(t.status())).count();
        long cancelled = turns.stream().filter(t -> "CANCELLED".equals(t.status())).count();
        List<Long> durations = turns.stream().map(TurnRow::duration).filter(Objects::nonNull).filter(v -> v >= 0).sorted().toList();
        List<Long> firstTokens = usage.stream().map(UsageRow::firstToken).filter(v -> v > 0).sorted().toList();
        BigDecimal cost = usage.stream()
                .filter(row -> sameCurrency(row.currency(), currency))
                .map(UsageRow::cost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long mcpFailures = mcp.stream().filter(v -> !Set.of("SUCCESS", "ACCEPTED").contains(v.status())).count();
        return new Summary(turns.size(), success, failed, cancelled, average(durations), percentile(durations, .95), average(firstTokens), usage.stream().mapToLong(UsageRow::tokens).sum(), cost, tools.size(), tools.stream().filter(t -> !"SUCCESS".equals(t.status())).count(), mcp.size(), mcpFailures);
    }

    private List<TrendPoint> trend(Window window, List<UsageRow> usage, List<TurnRow> turns,
                                   String granularity, String currency) {
        Map<String, TrendAccumulator> points = new TreeMap<>();
        for (UsageRow row : usage) {
            BigDecimal cost = sameCurrency(row.currency(), currency) ? row.cost() : null;
            points.computeIfAbsent(bucket(row.createdAt(), granularity), key -> new TrendAccumulator())
                    .add(row.tokens(), cost);
        }
        for (TurnRow row : turns) points.computeIfAbsent(bucket(row.createdAt(), granularity), k -> new TrendAccumulator()).turns++;
        return points.entrySet().stream().map(e -> new TrendPoint(e.getKey(), e.getValue().turns, e.getValue().tokens, e.getValue().cost)).toList();
    }
    private String bucket(Instant instant, String granularity) { ZonedDateTime value = instant.atZone(ZONE); return switch (granularity) { case "HOUR" -> value.withMinute(0).withSecond(0).withNano(0).toLocalDateTime().toString(); case "WEEK" -> value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate().toString(); default -> value.toLocalDate().toString(); }; }
    private List<ModelCost> modelCosts(List<UsageRow> usage) {
        Map<String, ModelAccumulator> map = new LinkedHashMap<>();
        for (UsageRow row : usage) {
            String key = String.join(":", Objects.toString(row.provider(), ""),
                    Objects.toString(row.model(), "未知模型"), normalizeCurrency(row.currency()));
            map.computeIfAbsent(key,
                    ignored -> new ModelAccumulator(row.provider(), row.model(), normalizeCurrency(row.currency())))
                    .add(row);
        }
        return map.values().stream()
                .map(ModelAccumulator::view)
                .sorted(Comparator.comparing(ModelCost::cost, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
    private List<ToolStat> toolStats(List<ToolRow> tools) { Map<String, ToolAccumulator> map = new LinkedHashMap<>(); for (ToolRow row : tools) map.computeIfAbsent(row.name(), ToolAccumulator::new).add(row); return map.values().stream().map(ToolAccumulator::view).sorted(Comparator.comparing(ToolStat::totalDurationMs).reversed()).limit(20).toList(); }
    private List<Failure> failures(List<TurnRow> turns, List<ToolRow> tools, List<McpRow> mcp) { List<Failure> result = new ArrayList<>(); turns.stream().filter(t -> "FAILED".equals(t.status()) || "CANCELLED".equals(t.status())).forEach(t -> result.add(new Failure("agent.turn", t.status(), t.error(), t.createdAt()))); tools.stream().filter(t -> !"SUCCESS".equals(t.status())).forEach(t -> result.add(new Failure(t.name(), t.status(), null, t.createdAt()))); mcp.stream().filter(t -> !Set.of("SUCCESS", "ACCEPTED").contains(t.status())).forEach(t -> result.add(new Failure("mcp." + t.type(), t.status(), null, t.createdAt()))); return result.stream().sorted(Comparator.comparing(Failure::createdAt, Comparator.nullsLast(Comparator.reverseOrder()))).limit(30).toList(); }
    private Progress budgetProgress(long userId, Budget budget) {
        LocalDate today = LocalDate.now(ZONE);
        Instant dayStart = today.atStartOfDay(ZONE).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZONE).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
        Instant monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(ZONE).toInstant();
        BigDecimal dailyCost = usageCost(userId, budget.currency(), dayStart, dayEnd);
        BigDecimal monthlyCost = usageCost(userId, budget.currency(), monthStart, monthEnd);
        return new Progress(dailyCost, monthlyCost,
                percentage(dailyCost, budget.dailyLimit()),
                percentage(monthlyCost, budget.monthlyLimit()), budget.currency());
    }

    private BigDecimal usageCost(long userId, String currency, Instant from, Instant to) {
        BigDecimal value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(estimated_total_cost), 0)
                FROM ai_usage
                WHERE user_id=? AND UPPER(COALESCE(currency, ''))=? AND created_at>=? AND created_at<?
                """, BigDecimal.class, userId, normalizeCurrency(currency), from, to);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static BigDecimal percentage(BigDecimal current, BigDecimal limit) {
        if (limit == null || limit.signum() <= 0) return null;
        return current.divide(limit, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    private static boolean sameCurrency(String left, String right) {
        return normalizeCurrency(left).equals(normalizeCurrency(right));
    }

    private static String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? "UNPRICED" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static Long nullableLong(ResultSet r, String name) throws java.sql.SQLException { Object value = r.getObject(name); return value == null ? null : ((Number) value).longValue(); }
    private static long longValue(ResultSet r, String name) throws java.sql.SQLException { Long value = nullableLong(r, name); return value == null ? 0 : value; }
    private static Instant instant(ResultSet r, String name) throws java.sql.SQLException { java.sql.Timestamp value = r.getTimestamp(name); return value == null ? null : value.toInstant(); }
    private static Long positiveLong(Long value) { return value == null || value < 0 ? null : value; }
    private static BigDecimal positive(BigDecimal value) { return value == null || value.signum() < 0 ? null : value; }
    private static long average(List<Long> values) { return values.isEmpty() ? 0 : values.stream().mapToLong(Long::longValue).sum() / values.size(); }
    private static long percentile(List<Long> values, double fraction) { return values.isEmpty() ? 0 : values.get(Math.min(values.size() - 1, (int) Math.ceil(values.size() * fraction) - 1)); }

    public record Budget(String currency, BigDecimal dailyLimit, BigDecimal monthlyLimit, BigDecimal singleRequestLimit, Long tokenLimit, String enforcementMode, boolean alertAt50, boolean alertAt80, boolean alertAt100, long revision) { static Budget defaults() { return new Budget("CNY", null, null, null, null, "WARN", true, true, true, 0); } }
    public record BudgetCommand(String currency, BigDecimal dailyLimit, BigDecimal monthlyLimit, BigDecimal singleRequestLimit, Long tokenLimit, String enforcementMode, Boolean alertAt50, Boolean alertAt80, Boolean alertAt100, Long revision) { public boolean alertAt50Enabled() { return alertAt50 == null || alertAt50; } public boolean alertAt80Enabled() { return alertAt80 == null || alertAt80; } public boolean alertAt100Enabled() { return alertAt100 == null || alertAt100; } }
    public record Metrics(String preset, String granularity, Instant from, Instant to, Summary summary, List<TrendPoint> trend, List<ModelCost> models, List<ToolStat> tools, List<Failure> failures, Budget budget, Progress budgetProgress) { }
    public record Summary(long turns, long completed, long failed, long cancelled, long averageDurationMs, long p95DurationMs, long averageFirstTokenMs, long totalTokens, BigDecimal totalCost, long toolCalls, long toolFailures, long mcpCalls, long mcpFailures) { }
    public record TrendPoint(String bucket, long turns, long tokens, BigDecimal cost) { }
    public record ModelCost(String provider, String model, String currency, long calls, long tokens, BigDecimal cost) { }
    public record ToolStat(String name, long calls, long failures, long totalDurationMs, long averageDurationMs) { }
    public record Failure(String source, String status, String detail, Instant createdAt) { }
    public record Progress(BigDecimal dailyCost, BigDecimal monthlyCost, BigDecimal dailyPercent,
                           BigDecimal monthlyPercent, String currency) { }
    private record Window(String preset, String granularity, Instant from, Instant to) { }
    private record TurnRow(String status, Long duration, Instant createdAt, String error, String provider, String model) { }
    private record UsageRow(String provider, String model, String currency, long tokens, long input, long output, long cacheHit, long cacheMiss, long reasoning, long firstToken, long duration, BigDecimal cost, Instant createdAt) { }
    private record ToolRow(String name, String status, long duration, Instant createdAt) { }
    private record McpRow(String type, String status, Instant createdAt) { }
    private static final class TrendAccumulator { long turns; long tokens; BigDecimal cost = BigDecimal.ZERO; void add(long t, BigDecimal c) { tokens += t; if (c != null) cost = cost.add(c); } }
    private static final class ModelAccumulator { final String provider, model, currency; long calls, tokens; BigDecimal cost = BigDecimal.ZERO; ModelAccumulator(String p, String m, String c) { provider=p; model=m; currency=c; } void add(UsageRow r) { calls++; tokens += r.tokens(); if (r.cost()!=null) cost=cost.add(r.cost()); } ModelCost view() { return new ModelCost(provider, model, currency, calls, tokens, cost); } }
    private static final class ToolAccumulator { final String name; long calls, failures, duration; ToolAccumulator(String n) { name=n; } void add(ToolRow r) { calls++; duration+=r.duration(); if (!"SUCCESS".equals(r.status())) failures++; } ToolStat view() { return new ToolStat(name, calls, failures, duration, calls == 0 ? 0 : duration/calls); } }
}
