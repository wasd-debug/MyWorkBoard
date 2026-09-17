package com.salarytracker.ledger;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ai.LlmGateway;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class LedgerReportAiService {
    private static final Pattern MONTH = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");
    private final ObjectMapper mapper;
    private final LlmGateway gateway;
    private final LedgerBookAccess access;

    public LedgerReportAiService(ObjectMapper mapper,
                                 LlmGateway gateway,
                                 LedgerBookAccess access) {
        this.mapper = mapper;
        this.gateway = gateway;
        this.access = access;
    }

    public Map<String, Object> analyzeMonth(String bookPublicId, Map<String, Object> input) {
        access.resolve(bookPublicId);
        if (!gateway.configured()) {
            throw new IllegalArgumentException("DeepSeek API 尚未配置，请在后端设置 DEEPSEEK_API_KEY");
        }
        input = input == null ? Map.of() : input;
        Map<String, Object> payload = sanitize(input);
        String response = gateway.structured(
                """
                你是谨慎、务实的个人财务分析助手。根据用户提供的月度聚合数据给出中文建议。
                只返回 JSON 对象，结构必须为：
                {"headline":"一句话结论","summary":"2到4句月度分析","suggestions":["建议1","建议2"],"risks":["风险1"]}
                不得编造未提供的收入、支出、预算或账户信息；不得提供投资收益承诺。
                headline 不超过 30 个汉字，summary 不超过 240 个汉字，suggestions 和 risks 各最多 4 条。
                """,
                json(payload),
                null,
                null);
        return parseAnalysis(response);
    }

    Map<String, Object> parseAnalysis(String response) {
        String json = text(response);
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first >= 0 && last > first) json = json.substring(first, last + 1);
        try {
            Map<String, Object> parsed = mapper.readValue(json, new TypeReference<>() {});
            String summary = limitedText(parsed.get("summary"), 240);
            if (summary.isBlank()) throw new IllegalArgumentException("DeepSeek 返回的月报分析缺少 summary");
            return Map.of(
                    "headline", defaultText(limitedText(parsed.get("headline"), 30), "本月财务小结"),
                    "summary", summary,
                    "suggestions", stringList(parsed.get("suggestions"), 4, 120),
                    "risks", stringList(parsed.get("risks"), 4, 120));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("DeepSeek 返回的月报分析格式无效");
        }
    }

    private Map<String, Object> sanitize(Map<String, Object> input) {
        String period = limitedText(input == null ? null : input.get("period"), 7);
        if (!MONTH.matcher(period).matches()) throw new IllegalArgumentException("period 必须为 yyyy-MM");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", period);
        result.put("summary", numericMap(input.get("summary"), List.of("income", "expense", "net", "incomeCount", "expenseCount")));
        result.put("previousMonth", numericMap(input.get("previousMonth"), List.of("income", "expense", "net")));
        result.put("expenseCategories", ranking(input.get("expenseCategories")));
        result.put("incomeCategories", ranking(input.get("incomeCategories")));
        result.put("merchants", ranking(input.get("merchants")));
        result.put("topExpenses", transactions(input.get("topExpenses")));
        result.put("topIncomes", transactions(input.get("topIncomes")));
        result.put("budget", numericMap(input.get("budget"), List.of("total", "spent")));
        return result;
    }

    private Map<String, Object> numericMap(Object value, List<String> keys) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : keys) {
            if (source.containsKey(key)) result.put(key, decimal(source.get(key)));
        }
        return result;
    }

    private List<Map<String, Object>> ranking(Object value) {
        if (!(value instanceof List<?> rows)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows.stream().limit(10).toList()) {
            if (!(row instanceof Map<?, ?> item)) continue;
            result.add(Map.of(
                    "name", limitedText(item.get("name"), 80),
                    "amount", decimal(item.get("amount")),
                    "count", decimal(item.get("count")),
                    "share", decimal(item.get("share"))));
        }
        return result;
    }

    private List<Map<String, Object>> transactions(Object value) {
        if (!(value instanceof List<?> rows)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : rows.stream().limit(3).toList()) {
            if (!(row instanceof Map<?, ?> item)) continue;
            Map<String, Object> safe = new LinkedHashMap<>();
            safe.put("amount", decimal(item.get("amount")));
            safe.put("date", limitedText(item.get("date"), 10));
            safe.put("category", limitedText(item.get("category"), 80));
            safe.put("account", limitedText(item.get("account"), 80));
            safe.put("merchant", limitedText(item.get("merchant"), 80));
            result.add(safe);
        }
        return result;
    }

    private List<String> stringList(Object value, int limit, int length) {
        if (!(value instanceof List<?> items)) return List.of();
        return items.stream()
                .map(item -> limitedText(item, length))
                .filter(item -> !item.isBlank())
                .limit(limit)
                .toList();
    }

    private BigDecimal decimal(Object value) {
        try {
            return new BigDecimal(text(value)).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return BigDecimal.ZERO.setScale(2);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成月报分析数据", exception);
        }
    }

    private String limitedText(Object value, int limit) {
        String result = text(value);
        return result.length() <= limit ? result : result.substring(0, limit);
    }

    private String defaultText(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
