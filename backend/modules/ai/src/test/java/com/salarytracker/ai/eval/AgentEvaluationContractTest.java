package com.salarytracker.ai.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.AgentPromptPolicy;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEvaluationContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AgentPromptPolicy policy = new AgentPromptPolicy();

    @Test
    void datasetHasStableUniqueCasesAndAbsoluteExpectedDates() {
        var dataset = AgentEvaluationDataset.load(mapper);
        Set<String> ids = new HashSet<>();

        assertEquals("zh-cn-v1", dataset.version());
        assertEquals("2026-09-24", dataset.today().toString());
        assertTrue(dataset.cases().size() >= 12);
        dataset.cases().forEach(item -> {
            assertTrue(ids.add(item.id()), "重复评测编号: " + item.id());
            assertFalse(item.message().isBlank(), item.id());
            assertTrue(item.allowNoTool() || !item.expectedTools().isEmpty(), item.id());
            item.expectedArguments().forEach((name, value) -> {
                if (Set.of("from", "to", "date", "occurredOn").contains(name)) {
                    assertTrue(value.asText().matches("\\d{4}-\\d{2}-\\d{2}"), item.id() + ": " + name);
                }
            });
        });
    }

    @Test
    void modelPolicyExposesReadAndPrepareButNeverCommitOrR4() {
        assertTrue(policy.modelVisible(definition("ledger.books.list", ToolRisk.R1)));
        assertTrue(policy.modelVisible(definition("ledger.members.list", ToolRisk.R1)));
        assertTrue(policy.modelVisible(definition("ledger.roles.list", ToolRisk.R1)));
        assertTrue(policy.modelVisible(definition("ledger.transaction.delete.prepare", ToolRisk.R3)));
        assertFalse(policy.modelVisible(definition("ledger.transaction.delete.commit", ToolRisk.R3)));
        assertFalse(policy.modelVisible(definition("ledger.book.delete.prepare", ToolRisk.R4)));
        assertFalse(policy.modelVisible(definition("ledger.import.confirm.prepare", ToolRisk.R4)));
        assertFalse(policy.modelVisible(definition("ledger.import.confirm.commit", ToolRisk.R4)));
        assertFalse(policy.modelVisible(definition("ledger.member.update.prepare", ToolRisk.R4)));
        assertFalse(policy.modelVisible(definition("ledger.role.delete.prepare", ToolRisk.R4)));
        assertTrue(policy.systemPrompt(java.time.LocalDate.of(2026, 9, 24)).contains("今天是 2026-09-24"));
        assertTrue(policy.systemPrompt(java.time.LocalDate.of(2026, 9, 24)).contains("你不得调用 commit"));
    }

    @Test
    void everyDatasetToolRespectsThePrepareCommitBoundary() {
        var dataset = AgentEvaluationDataset.load(mapper);
        dataset.cases().forEach(item -> item.expectedTools().forEach(tool ->
                assertFalse(tool.endsWith(".commit"), item.id() + " 不能期望模型调用 commit")));
        dataset.cases().forEach(item -> item.forbiddenTools().forEach(tool -> {
            if (tool.endsWith(".commit")) {
                assertFalse(policy.modelVisible(definition(tool, ToolRisk.R3)), item.id() + ": " + tool);
            }
        }));
    }

    private ToolDefinition definition(String name, ToolRisk risk) {
        return new ToolDefinition(name, 1, "评测工具", risk, Set.of(), ToolSchemas.object(mapper));
    }
}
