package com.salarytracker.ai.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolSchemas;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    void serviceKeepsExpiredTerminalActionsInTheirFinalState() {
        MemoryRepository repository = new MemoryRepository();
        PendingActionService service = new PendingActionService(repository, new ObjectMapper(), new InteractionPolicy());
        for (ActionStatus status : Set.of(ActionStatus.COMPLETED, ActionStatus.CONFLICT,
                ActionStatus.DENIED, ActionStatus.FAILED, ActionStatus.CANCELLED, ActionStatus.EXPIRED)) {
            PendingAction action = new PendingAction(status.name(), 7L, "ledger.transaction.create", 1,
                    "{}", null, status, Instant.now().minusSeconds(1), Instant.now().minusSeconds(2));
            repository.insert(action);
            assertEquals(status, service.getForUser(action.id(), 7L).status());
        }
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
        return new PendingActionService(new MemoryRepository(), new ObjectMapper(), new InteractionPolicy());
    }

    private static final class MemoryRepository implements PendingActionRepository {
        private final Map<String, PendingAction> actions = new ConcurrentHashMap<>();

        @Override
        public void insert(PendingAction action) {
            if (actions.putIfAbsent(action.id(), action) != null) throw new IllegalStateException("actionId 已存在");
        }

        @Override
        public Optional<PendingAction> find(String id, long userId) {
            return Optional.ofNullable(actions.get(id)).filter(action -> action.userId() == userId);
        }

        @Override
        public boolean transition(String id, long userId, ActionStatus expected, ActionStatus next, Instant updatedAt) {
            final boolean[] changed = {false};
            actions.computeIfPresent(id, (key, current) -> {
                if (current.userId() != userId || current.status() != expected) return current;
                changed[0] = true;
                return current.transition(next, updatedAt);
            });
            return changed[0];
        }
    }
}
