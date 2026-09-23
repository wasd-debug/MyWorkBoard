package com.salarytracker.ai.session;

import java.time.Instant;
import java.util.Optional;

public interface AgentTurnRepository {
    AgentTurnView createOrFind(String turnId, String sessionId, long userId, String clientRequestId,
                               String userMessage);

    Optional<AgentTurnView> find(String turnId, long userId);

    void markPlanning(String turnId, long userId);

    boolean complete(String turnId, long userId, String assistantContent, String responseJson);

    boolean fail(String turnId, long userId, String errorMessage);

    boolean cancel(String turnId, long userId);

    record AgentTurnView(String id, String sessionId, String clientRequestId, AgentTurnStatus status,
                         String userMessage, String assistantContent, String responseJson,
                         String errorMessage, Instant createdAt, Instant startedAt, Instant completedAt) {
    }
}
