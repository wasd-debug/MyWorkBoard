package com.salarytracker.ai;

import com.salarytracker.ai.session.AgentConversationService;
import com.salarytracker.ai.session.JdbcAgentSessionRepository;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentConversationIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void persistsConversationInOrderAndKeepsItUserScoped() {
        String username = "agent-context-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'agent')", username);
        long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
        String sessionId = UUID.randomUUID().toString();
        JdbcAgentSessionRepository repository = new JdbcAgentSessionRepository(jdbc);

        repository.create(sessionId, userId, "我有哪些账本？");
        repository.appendExchange(sessionId, userId, "我有哪些账本？", "你有一个默认账本。");
        repository.appendExchange(sessionId, userId, "它有几条流水？", "目前没有流水。");

        List<AgentConversationService.StoredMessage> messages =
                repository.recentMessages(sessionId, userId, 20);
        assertEquals(List.of("user", "assistant", "user", "assistant"),
                messages.stream().map(AgentConversationService.StoredMessage::role).toList());
        assertEquals("它有几条流水？", messages.get(2).content());
        assertEquals(0, repository.recentMessages(sessionId, userId + 1, 20).size());
    }
}
