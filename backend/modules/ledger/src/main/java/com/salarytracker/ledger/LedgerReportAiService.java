package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ai.LlmGateway;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

import static com.salarytracker.ledger.LedgerModels.*;

@Service
public class LedgerReportAiService {
    private static final Pattern MONTH = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    private final ObjectMapper mapper;
    private final LlmGateway gateway;
    private final LedgerBookAccess access;

    public LedgerReportAiService(ObjectMapper mapper, LlmGateway gateway, LedgerBookAccess access) {
        this.mapper = mapper;
        this.gateway = gateway;
        this.access = access;
    }

    public MonthlyAnalysis analyzeMonth(String bookPublicId, MonthlyAnalysisCommand input) {
        access.resolve(bookPublicId);
        if (!gateway.configured()) {
            throw new IllegalArgumentException("DeepSeek API 尚未配置，请在后端设置 DEEPSEEK_API_KEY");
        }
        if (input == null || input.period() == null || !MONTH.matcher(input.period()).matches()) {
            throw new IllegalArgumentException("period 必须为 yyyy-MM");
        }
        String response = gateway.structured(
                """
                你是谨慎、务实的个人财务分析助手。根据用户提供的月度聚合数据给出中文建议。
                只返回 JSON 对象，结构必须为：
                {"headline":"一句话结论","summary":"2到4句月度分析","suggestions":["建议1","建议2"],"risks":["风险1"]}
                不得编造未提供的收入、支出、预算或账户信息；不得提供投资收益承诺。
                headline 不超过 30 个汉字，summary 不超过 240 个汉字，suggestions 和 risks 各最多 4 条。
                """,
                json(input), null, null);
        return parseAnalysis(response);
    }

    MonthlyAnalysis parseAnalysis(String response) {
        String json = text(response);
        int first = json.indexOf('{');
        int last = json.lastIndexOf('}');
        if (first >= 0 && last > first) json = json.substring(first, last + 1);
        try {
            MonthlyAnalysis parsed = mapper.readValue(json, MonthlyAnalysis.class);
            String summary = limited(parsed.summary(), 240);
            if (summary.isBlank()) throw new IllegalArgumentException("DeepSeek 返回的月报分析缺少 summary");
            return new MonthlyAnalysis(defaultText(limited(parsed.headline(), 30), "本月财务小结"),
                    summary, limitedList(parsed.suggestions(), 4, 120), limitedList(parsed.risks(), 4, 120));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("DeepSeek 返回的月报分析格式无效");
        }
    }

    private List<String> limitedList(List<String> source, int count, int length) {
        if (source == null) return List.of();
        return source.stream().map(value -> limited(value, length)).filter(value -> !value.isBlank()).limit(count).toList();
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成月报分析数据", exception);
        }
    }

    private String limited(String value, int limit) {
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
