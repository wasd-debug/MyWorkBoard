package com.salarytracker.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

final class AgentEvaluationDataset {
    private AgentEvaluationDataset() { }

    static Dataset load(ObjectMapper mapper) {
        try (InputStream input = AgentEvaluationDataset.class.getResourceAsStream("/agent-eval/zh-cn-v1.json")) {
            if (input == null) throw new IllegalStateException("缺少中文 Agent 评测集");
            return mapper.readValue(input, Dataset.class);
        } catch (Exception exception) {
            throw new IllegalStateException("无法读取中文 Agent 评测集", exception);
        }
    }

    record Dataset(String version, LocalDate today, List<Case> cases) { }

    record Case(String id, String message, List<String> expectedTools, boolean allowNoTool,
                Map<String, JsonNode> expectedArguments, List<String> forbiddenTools,
                List<String> forbiddenArgumentNames) {
        Case {
            expectedTools = expectedTools == null ? List.of() : List.copyOf(expectedTools);
            expectedArguments = expectedArguments == null ? Map.of() : Map.copyOf(expectedArguments);
            forbiddenTools = forbiddenTools == null ? List.of() : List.copyOf(forbiddenTools);
            forbiddenArgumentNames = forbiddenArgumentNames == null ? List.of() : List.copyOf(forbiddenArgumentNames);
        }
    }
}
