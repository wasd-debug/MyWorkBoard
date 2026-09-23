package com.salarytracker.ai.session;

import java.util.List;
import java.util.OptionalLong;
import java.util.Optional;

public interface AgentSessionRepository {
    OptionalLong findOwner(String sessionId);

    void create(String sessionId, long userId, String title);

    List<AgentConversationService.StoredMessage> recentMessages(String sessionId, long userId, int limit);

    void appendExchange(String sessionId, long userId, String userMessage, String assistantMessage);

    void appendExchange(String sessionId, long userId, String turnId, String userMessage,
                        String assistantMessage, String metadataJson);

    List<AgentConversationService.SessionSummary> listSessions(long userId, boolean archived);

    Optional<AgentConversationService.SessionSummary> findSession(String sessionId, long userId);

    List<AgentConversationService.MessageView> messages(String sessionId, long userId);

    void rename(String sessionId, long userId, String title);

    void archive(String sessionId, long userId);

    void delete(String sessionId, long userId);
}
