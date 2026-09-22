package com.salarytracker.ai.session;

import java.util.List;
import java.util.OptionalLong;

public interface AgentSessionRepository {
    OptionalLong findOwner(String sessionId);

    void create(String sessionId, long userId, String title);

    List<AgentConversationService.StoredMessage> recentMessages(String sessionId, long userId, int limit);

    void appendExchange(String sessionId, long userId, String userMessage, String assistantMessage);
}
