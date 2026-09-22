package com.salarytracker.ai.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PendingActionTest {
    @Test
    void supportsPrepareAndApprovalFlow() {
        Instant expiry = Instant.now().plus(Duration.ofMinutes(5));
        PendingAction action = PendingAction.create(7L, "ledger.transaction.create", 1, "{}", 3L, expiry);
        action = action.transition(ActionStatus.PLANNING, Instant.now());
        action = action.transition(ActionStatus.WAITING_CONFIRMATION, Instant.now());
        action = action.transition(ActionStatus.APPROVED, Instant.now());
        action = action.transition(ActionStatus.EXECUTING, Instant.now());
        assertEquals(ActionStatus.COMPLETED,
                action.transition(ActionStatus.COMPLETED, Instant.now()).status());
    }

    @Test
    void rejectsSkippingConfirmation() {
        PendingAction action = PendingAction.create(7L, "ledger.transaction.create", 1, "{}", null,
                Instant.now().plusSeconds(60));
        assertThrows(IllegalStateException.class,
                () -> action.transition(ActionStatus.COMPLETED, Instant.now()));
    }

    @Test
    void expiresBeforeAnyFurtherTransition() {
        PendingAction action = PendingAction.create(7L, "ledger.transaction.create", 1, "{}", null,
                Instant.now().minusSeconds(1));
        assertEquals(ActionStatus.EXPIRED, action.transition(ActionStatus.PLANNING, Instant.now()).status());
    }

    @Test
    void serviceDoesNotExposeAnotherUsersAction() {
        PendingActionService service = service();
        PendingAction action = PendingAction.create(7L, "ledger.transaction.create", 1, "{}", null,
                Instant.now().plusSeconds(60));
        service.create(action);

        assertThrows(IllegalArgumentException.class, () -> service.getForUser(action.id(), 8L));
    }

    @Test
    void prepareRequiresApprovalAndCommitCanStartOnlyOnce() {
        PendingActionService service = service();
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition tool = new ToolDefinition("ledger.transaction.create", 1, "创建流水",
                ToolRisk.R2, Set.of("ledger:write"), ToolSchemas.object(mapper));
        PendingAction action = service.prepare(7L, tool, mapper.createObjectNode().put("amount", 10),
                null, false, Duration.ofMinutes(5));

        assertEquals(ActionStatus.WAITING_CONFIRMATION, action.status());
        assertThrows(IllegalStateException.class, () -> service.beginCommit(action.id(), 7L));
        service.approve(action.id(), 7L);
        assertEquals(ActionStatus.EXECUTING, service.beginCommit(action.id(), 7L).status());
        assertThrows(IllegalStateException.class, () -> service.beginCommit(action.id(), 7L));
    }

    private PendingActionService service() {
        return new PendingActionService(new ObjectMapper(), new InteractionPolicy());
    }
}
