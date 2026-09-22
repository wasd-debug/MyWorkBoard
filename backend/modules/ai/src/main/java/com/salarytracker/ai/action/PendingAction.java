package com.salarytracker.ai.action;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PendingAction(String id, long userId, String toolName, int toolVersion,
                            String inputSnapshot, Long expectedRevision, ActionStatus status,
                            Instant expiresAt, Instant updatedAt) {
    public PendingAction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(toolName);
        Objects.requireNonNull(inputSnapshot);
        Objects.requireNonNull(status);
        Objects.requireNonNull(expiresAt);
        Objects.requireNonNull(updatedAt);
    }

    public static PendingAction create(long userId, String toolName, int toolVersion,
                                       String inputSnapshot, Long expectedRevision,
                                       Instant expiresAt) {
        return new PendingAction(UUID.randomUUID().toString(), userId, toolName, toolVersion,
                inputSnapshot, expectedRevision, ActionStatus.RECEIVED, expiresAt, Instant.now());
    }

    public PendingAction transition(ActionStatus next, Instant now) {
        if (now.isAfter(expiresAt) && !terminal(status)) {
            next = ActionStatus.EXPIRED;
        }
        if (!allowed(status, next)) {
            throw new IllegalStateException("不允许从 " + status + " 转换到 " + next);
        }
        return new PendingAction(id, userId, toolName, toolVersion, inputSnapshot, expectedRevision,
                next, expiresAt, now);
    }

    private static boolean allowed(ActionStatus from, ActionStatus to) {
        if (from == to) return true;
        if (to == ActionStatus.EXPIRED && !terminal(from)) return true;
        return switch (from) {
            case RECEIVED -> Set.of(ActionStatus.PLANNING, ActionStatus.CANCELLED).contains(to);
            case PLANNING -> Set.of(ActionStatus.WAITING_INPUT, ActionStatus.WAITING_CONFIRMATION,
                    ActionStatus.DENIED, ActionStatus.FAILED, ActionStatus.CANCELLED).contains(to);
            case WAITING_INPUT -> Set.of(ActionStatus.PLANNING, ActionStatus.CANCELLED, ActionStatus.EXPIRED).contains(to);
            case WAITING_CONFIRMATION -> Set.of(ActionStatus.APPROVED, ActionStatus.DENIED,
                    ActionStatus.CANCELLED, ActionStatus.EXPIRED).contains(to);
            case APPROVED -> Set.of(ActionStatus.EXECUTING, ActionStatus.CANCELLED, ActionStatus.EXPIRED).contains(to);
            case EXECUTING -> Set.of(ActionStatus.COMPLETED, ActionStatus.CONFLICT, ActionStatus.DENIED,
                    ActionStatus.FAILED).contains(to);
            default -> false;
        };
    }

    private static boolean terminal(ActionStatus status) {
        return Set.of(ActionStatus.COMPLETED, ActionStatus.CONFLICT, ActionStatus.DENIED,
                ActionStatus.FAILED, ActionStatus.CANCELLED, ActionStatus.EXPIRED).contains(status);
    }
}
