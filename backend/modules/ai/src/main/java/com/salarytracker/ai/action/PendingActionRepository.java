package com.salarytracker.ai.action;

import java.time.Instant;
import java.util.Optional;

public interface PendingActionRepository {
    void insert(PendingAction action);

    Optional<PendingAction> find(String id, long userId);

    boolean transition(String id, long userId, ActionStatus expected, ActionStatus next, Instant updatedAt);
}
