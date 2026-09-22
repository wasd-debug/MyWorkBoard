package com.salarytracker.ai.session;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

@Repository
public class JdbcAgentSessionRepository implements AgentSessionRepository {
    private final JdbcTemplate jdbc;

    public JdbcAgentSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OptionalLong findOwner(String sessionId) {
        List<Long> owners = jdbc.query("SELECT user_id FROM agent_session WHERE id=?",
                (result, rowNum) -> result.getLong("user_id"), sessionId);
        return owners.isEmpty() ? OptionalLong.empty() : OptionalLong.of(owners.get(0));
    }

    @Override
    public void create(String sessionId, long userId, String title) {
        try {
            jdbc.update("INSERT INTO agent_session(id,user_id,title) VALUES(?,?,?)", sessionId, userId, title);
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("会话已存在", exception);
        }
    }

    @Override
    public List<AgentConversationService.StoredMessage> recentMessages(String sessionId, long userId, int limit) {
        List<AgentConversationService.StoredMessage> descending = jdbc.query(
                "SELECT role,content FROM agent_message WHERE session_id=? AND user_id=? ORDER BY id DESC LIMIT ?",
                (result, rowNum) -> new AgentConversationService.StoredMessage(
                        result.getString("role"), result.getString("content")),
                sessionId, userId, limit);
        List<AgentConversationService.StoredMessage> chronological = new ArrayList<>(descending);
        Collections.reverse(chronological);
        return List.copyOf(chronological);
    }

    @Override
    @Transactional
    public void appendExchange(String sessionId, long userId, String userMessage, String assistantMessage) {
        jdbc.batchUpdate("INSERT INTO agent_message(session_id,user_id,role,content) VALUES(?,?,?,?)",
                List.of(
                        new Object[]{sessionId, userId, "user", userMessage},
                        new Object[]{sessionId, userId, "assistant", assistantMessage}));
        jdbc.update("UPDATE agent_session SET updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND user_id=?",
                sessionId, userId);
    }
}
