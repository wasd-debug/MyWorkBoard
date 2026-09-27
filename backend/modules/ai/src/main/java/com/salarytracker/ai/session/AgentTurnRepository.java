package com.salarytracker.ai.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentTurnRepository {
    default AgentTurnView enqueue(String turnId, String sessionId, long userId, String clientRequestId,
                                  String userMessage, String retryOfTurnId) {
        return enqueue(turnId, sessionId, userId, clientRequestId, userMessage, false, retryOfTurnId);
    }

    AgentTurnView enqueue(String turnId, String sessionId, long userId, String clientRequestId,
                          String userMessage, boolean deepThinking, String retryOfTurnId);

    Optional<AgentTurnView> find(String turnId, long userId);

    List<AgentTurnView> listQueue(String sessionId, long userId);

    long queueRevision(String sessionId, long userId);

    long reorder(String sessionId, long userId, long expectedRevision, List<String> turnIds);

    Optional<AgentTurnView> claimNext(String sessionId, long userId);

    void markPlanning(String turnId, long userId);

    List<PendingQueue> pendingQueues(int limit);

    int requeueInterrupted();

    boolean complete(String turnId, long userId, String assistantContent, String reasoningContent, String responseJson);

    default boolean complete(String turnId, long userId, String assistantContent, String responseJson) {
        return complete(turnId, userId, assistantContent, null, responseJson);
    }

    boolean fail(String turnId, long userId, String errorMessage);

    boolean cancel(String turnId, long userId);

    boolean cancelQueued(String turnId, long userId);

    record AgentTurnView(String id, String sessionId, long userId, String clientRequestId, String retryOfTurnId,
                         AgentTurnStatus status, long queuePosition, String userMessage,
                         boolean deepThinking, String assistantContent, String reasoningContent,
                         String responseJson, String errorMessage,
                         Instant createdAt, Instant startedAt, Instant completedAt) {
    }

    record QueueView(long revision, List<AgentTurnView> items) {
    }

    record PendingQueue(String sessionId, long userId) {
    }
}
