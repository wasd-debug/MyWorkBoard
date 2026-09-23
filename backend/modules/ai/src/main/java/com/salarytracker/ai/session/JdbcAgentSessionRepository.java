package com.salarytracker.ai.session;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;
import java.util.Optional;

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
        appendExchange(sessionId, userId, null, userMessage, assistantMessage, null);
    }

    @Override
    @Transactional
    public void appendExchange(String sessionId, long userId, String turnId, String userMessage,
                               String assistantMessage, String metadataJson) {
        jdbc.batchUpdate("INSERT INTO agent_message(session_id,user_id,turn_id,role,content,metadata_json) VALUES(?,?,?,?,?,?)",
                List.of(
                        new Object[]{sessionId, userId, turnId, "user", userMessage, null},
                        new Object[]{sessionId, userId, turnId, "assistant", assistantMessage, metadataJson}));
        jdbc.update("UPDATE agent_session SET updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND user_id=?",
                sessionId, userId);
    }

    @Override
    public List<AgentConversationService.SessionSummary> listSessions(long userId, boolean archived) {
        return jdbc.query("""
                        SELECT id,title,created_at,updated_at,archived_at
                        FROM agent_session WHERE user_id=? AND (archived_at IS NOT NULL)=?
                        ORDER BY updated_at DESC LIMIT 100
                        """,
                (result, rowNum) -> session(result), userId, archived);
    }

    @Override
    public Optional<AgentConversationService.SessionSummary> findSession(String sessionId, long userId) {
        List<AgentConversationService.SessionSummary> rows = jdbc.query("""
                        SELECT id,title,created_at,updated_at,archived_at
                        FROM agent_session WHERE id=? AND user_id=?
                        """, (result, rowNum) -> session(result), sessionId, userId);
        return rows.stream().findFirst();
    }

    @Override
    public List<AgentConversationService.MessageView> messages(String sessionId, long userId) {
        return jdbc.query("""
                        SELECT id,turn_id,role,content,metadata_json,created_at
                        FROM agent_message WHERE session_id=? AND user_id=? ORDER BY id
                        """, (result, rowNum) -> new AgentConversationService.MessageView(
                        result.getLong("id"), result.getString("turn_id"), result.getString("role"),
                        result.getString("content"), result.getString("metadata_json"),
                        result.getTimestamp("created_at").toInstant()), sessionId, userId);
    }

    @Override
    public void rename(String sessionId, long userId, String title) {
        requireChanged(jdbc.update("UPDATE agent_session SET title=? WHERE id=? AND user_id=?",
                title, sessionId, userId));
    }

    @Override
    public void archive(String sessionId, long userId) {
        requireChanged(jdbc.update("UPDATE agent_session SET archived_at=CURRENT_TIMESTAMP(6) WHERE id=? AND user_id=?",
                sessionId, userId));
    }

    @Override
    public void delete(String sessionId, long userId) {
        requireChanged(jdbc.update("DELETE FROM agent_session WHERE id=? AND user_id=?", sessionId, userId));
    }

    private AgentConversationService.SessionSummary session(java.sql.ResultSet result) throws java.sql.SQLException {
        java.sql.Timestamp archived = result.getTimestamp("archived_at");
        return new AgentConversationService.SessionSummary(result.getString("id"), result.getString("title"),
                result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant(),
                archived == null ? null : archived.toInstant());
    }

    private void requireChanged(int changed) {
        if (changed != 1) throw new IllegalArgumentException("会话不存在");
    }
}
