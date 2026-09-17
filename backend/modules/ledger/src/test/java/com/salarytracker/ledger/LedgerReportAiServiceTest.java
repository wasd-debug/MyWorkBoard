package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerReportAiServiceTest {
    private final LedgerReportAiService service =
            new LedgerReportAiService(new ObjectMapper(), null, null);

    @Test
    void parsesDeepSeekMonthlyAnalysisJson() {
        Map<String, Object> result = service.parseAnalysis("""
                ```json
                {"headline":"支出有所收敛","summary":"本月结余改善，但居住类支出仍占比较高。",
                 "suggestions":["继续控制非必要消费","为固定支出设置预算"],"risks":["收入来源较集中"]}
                ```
                """);

        assertThat(result.get("headline")).isEqualTo("支出有所收敛");
        assertThat(result.get("summary")).isEqualTo("本月结余改善，但居住类支出仍占比较高。");
        assertThat(result.get("suggestions")).asList().hasSize(2);
        assertThat(result.get("risks")).asList().containsExactly("收入来源较集中");
    }

    @Test
    void rejectsAnalysisWithoutSummary() {
        assertThrows(IllegalArgumentException.class,
                () -> service.parseAnalysis("{\"headline\":\"只有标题\"}"));
    }
}
