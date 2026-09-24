package com.salarytracker.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.salarytracker.ai.AgentPromptPolicy;
import com.salarytracker.platform.ai.LlmGateway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "AGENT_LIVE_EVAL", matches = "true")
class AgentLiveModelEvaluationTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void evaluatesFirstToolSelectionAndArgumentsWithoutExecutingBusinessTools() throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        assertFalse(apiKey == null || apiKey.isBlank(), "AGENT_LIVE_EVAL=true 时必须提供 DEEPSEEK_API_KEY");
        String endpoint = value("LLM_ENDPOINT", "https://api.deepseek.com/chat/completions");
        String modelName = value("LLM_MODEL", "deepseek-chat");
        LlmGateway gateway = new LlmGateway(mapper, RestClient.builder(), endpoint, modelName, apiKey);
        AgentPromptPolicy policy = new AgentPromptPolicy();
        var dataset = AgentEvaluationDataset.load(mapper);
        List<LlmGateway.AgentTool> tools = toolCatalog();
        List<String> failures = new ArrayList<>();
        int passed = 0;

        for (var item : dataset.cases()) {
            var turn = gateway.agentTurn(List.of(
                    LlmGateway.AgentMessage.system(policy.systemPrompt(dataset.today())),
                    LlmGateway.AgentMessage.user(item.message())), tools);
            String actualTool = turn.toolCalls().isEmpty() ? "" : decode(turn.toolCalls().get(0).name());
            JsonNode arguments = turn.toolCalls().isEmpty() ? mapper.createObjectNode()
                    : mapper.readTree(turn.toolCalls().get(0).arguments());
            List<String> reasons = validate(item, actualTool, arguments);
            if (reasons.isEmpty()) passed++;
            else failures.add(item.id() + ": " + String.join("; ", reasons));
        }

        double accuracy = dataset.cases().isEmpty() ? 0 : (double) passed / dataset.cases().size();
        assertTrue(accuracy >= 0.90, "工具选择/参数准确率 " + Math.round(accuracy * 100) + "%\n" + String.join("\n", failures));
    }

    private List<String> validate(AgentEvaluationDataset.Case item, String tool, JsonNode arguments) {
        List<String> reasons = new ArrayList<>();
        if (!(item.allowNoTool() && tool.isBlank()) && !item.expectedTools().contains(tool)) {
            reasons.add("工具=" + tool + "，预期=" + item.expectedTools());
        }
        if (item.forbiddenTools().contains(tool) || tool.endsWith(".commit")) reasons.add("调用了禁止工具=" + tool);
        item.expectedArguments().forEach((name, expected) -> {
            JsonNode actual = arguments.path(name);
            if (!actual.equals(expected)) reasons.add(name + "=" + actual + "，预期=" + expected);
        });
        item.forbiddenArgumentNames().forEach(name -> {
            if (arguments.has(name)) reasons.add("包含禁止参数=" + name);
        });
        return reasons;
    }

    private List<LlmGateway.AgentTool> toolCatalog() {
        Map<String, Spec> catalog = new LinkedHashMap<>();
        catalog.put("worktime.settings.get", spec("读取当前登录用户的工时制度与薪资设置；不能用于查询或定位工时记录。"));
        catalog.put("worktime.records.search", spec("按绝对日期范围查询当前用户真实工时记录；修改或删除前没有 recordId 时必须先调用。", "from", "to", "limit", "offset"));
        catalog.put("worktime.record.create.prepare", spec("准备新增一条工时；即使时间缺失也应调用并由站内表单补充，不写业务数据。", "date", "start", "end", "rest", "note"));
        catalog.put("worktime.record.update.prepare", spec("已有唯一 recordId 后准备修改工时，不写业务数据。", "recordId", "date", "start", "end", "rest", "note"));
        catalog.put("worktime.record.delete.prepare", spec("已有唯一 recordId 后准备删除工时，不写业务数据。", "recordId"));
        catalog.put("ledger.books.list", spec("列出当前登录用户可见账本；没有 bookId 时先调用。"));
        catalog.put("ledger.overview", spec("查询指定账本在绝对日期范围内的概览。", "bookId", "from", "to"));
        catalog.put("ledger.transactions.search", spec("搜索指定账本的真实流水；修改或删除前用它定位 transactionId。", "bookId", "from", "to", "kind", "accountId", "categoryId", "limit", "offset"));
        catalog.put("ledger.reports.summary", spec("汇总指定账本绝对日期范围的收入支出报表。", "bookId", "from", "to"));
        catalog.put("ledger.budgets.list", spec("查询指定账本 yyyy-MM 月份预算。", "bookId", "month"));
        catalog.put("ledger.transaction.create.prepare", spec("准备新增普通收入或支出；缺字段时仍可调用并由站内表单补充，不写业务数据。", "bookId", "kind", "amount", "accountName", "categoryName", "merchantName", "memberName", "projectName", "occurredOn", "note"));
        catalog.put("ledger.transaction.update.prepare", spec("已有唯一 transactionId 后准备修改普通流水，不写业务数据。", "bookId", "transactionId", "kind", "amount", "accountId", "categoryId", "merchantId", "memberId", "projectId", "occurredOn", "note"));
        catalog.put("ledger.transaction.delete.prepare", spec("已有唯一 transactionId 后准备删除普通流水，不写业务数据。", "bookId", "transactionId"));
        return catalog.entrySet().stream().map(entry -> LlmGateway.AgentTool.function(
                encode(entry.getKey()), entry.getValue().description(), schema(entry.getValue().fields()))).toList();
    }

    private ObjectNode schema(List<String> fields) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        fields.forEach(field -> properties.putObject(field).put("type",
                List.of("limit", "offset", "rest", "recordId").contains(field) ? "integer" :
                        "amount".equals(field) ? "number" : "string"));
        schema.put("additionalProperties", false);
        return schema;
    }

    private String encode(String tool) { return tool.replace(".", "__"); }
    private String decode(String tool) { return tool.replace("__", "."); }
    private Spec spec(String description, String... fields) { return new Spec(description, List.of(fields)); }
    private String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record Spec(String description, List<String> fields) { }
}
