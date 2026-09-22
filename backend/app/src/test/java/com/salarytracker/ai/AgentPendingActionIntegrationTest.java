package com.salarytracker.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.JdbcPendingActionRepository;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPendingActionIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void persistsUserScopedActionAndAllowsOnlyOneCommitStart() {
        String username = "agent-action-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'agent')", username);
        long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
        ObjectMapper mapper = new ObjectMapper();
        PendingActionService actions = new PendingActionService(new JdbcPendingActionRepository(jdbc), mapper,
                new InteractionPolicy());
        ToolDefinition tool = new ToolDefinition("worktime.record.create.prepare", 1, "prepare worktime",
                ToolRisk.R2, Set.of("worktime:write"), ToolSchemas.object(mapper));

        PendingAction prepared = actions.prepare(userId, tool,
                mapper.createObjectNode().put("date", "2026-09-22").put("start", "09:00"),
                null, false, Duration.ofMinutes(5));

        assertEquals(ActionStatus.WAITING_CONFIRMATION, actions.getForUser(prepared.id(), userId).status());
        assertThrows(IllegalArgumentException.class, () -> actions.getForUser(prepared.id(), userId + 1));
        actions.approve(prepared.id(), userId);
        assertEquals(ActionStatus.EXECUTING, actions.beginCommit(prepared.id(), userId).status());
        assertThrows(IllegalStateException.class, () -> actions.beginCommit(prepared.id(), userId));
    }
}
