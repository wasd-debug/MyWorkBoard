package com.salarytracker.ai.session;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAgentTurnRepository implements AgentTurnRepository {
    private final JdbcTemplate jdbc;

    public JdbcAgentTurnRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AgentTurnView createOrFind(String turnId, String sessionId, long userId, String clientRequestId,
                                      String userMessage) {
        try {
            jdbc.update("""
                    INSERT INTO agent_turn(id,session_id,user_id,client_request_id,status,user_message)
                    VALUES(?,?,?,?,?,?)
                    """, turnId, sessionId, userId, clientRequestId, AgentTurnStatus.RECEIVED.name(), userMessage);
        } catch (DuplicateKeyException ignored) {
            // Idempotent replay returns the original turn for this user and request id.
        }
        List<AgentTurnView> rows = jdbc.query("""
                        SELECT * FROM agent_turn WHERE user_id=? AND client_request_id=?
                        """, (result, rowNum) -> map(result), userId, clientRequestId);
        if (rows.isEmpty() || !rows.get(0).sessionId().equals(sessionId)) {
            throw new IllegalArgumentException("请求标识已被其他会话使用");
        }
        return rows.get(0);
    }

    @Override
    public Optional<AgentTurnView> find(String turnId, long userId) {
        return jdbc.query("SELECT * FROM agent_turn WHERE id=? AND user_id=?",
                (result, rowNum) -> map(result), turnId, userId).stream().findFirst();
    }

    @Override
    public void markPlanning(String turnId, long userId) {
        jdbc.update("""
                UPDATE agent_turn SET status=?,started_at=COALESCE(started_at,CURRENT_TIMESTAMP(6))
                WHERE id=? AND user_id=? AND status=?
                """, AgentTurnStatus.PLANNING.name(), turnId, userId, AgentTurnStatus.RECEIVED.name());
    }

    @Override
    public boolean complete(String turnId, long userId, String assistantContent, String responseJson) {
        return jdbc.update("""
                UPDATE agent_turn SET status=?,assistant_content=?,response_json=?,completed_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND status IN (?,?)
                """, AgentTurnStatus.COMPLETED.name(), assistantContent, responseJson, turnId, userId,
                AgentTurnStatus.RECEIVED.name(), AgentTurnStatus.PLANNING.name()) == 1;
    }

    @Override
    public boolean fail(String turnId, long userId, String errorMessage) {
        return jdbc.update("""
                UPDATE agent_turn SET status=?,error_message=?,completed_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND status IN (?,?)
                """, AgentTurnStatus.FAILED.name(), errorMessage, turnId, userId,
                AgentTurnStatus.RECEIVED.name(), AgentTurnStatus.PLANNING.name()) == 1;
    }

    @Override
    public boolean cancel(String turnId, long userId) {
        return jdbc.update("""
                UPDATE agent_turn SET status=?,completed_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND status IN (?,?)
                """, AgentTurnStatus.CANCELLED.name(), turnId, userId,
                AgentTurnStatus.RECEIVED.name(), AgentTurnStatus.PLANNING.name()) == 1;
    }

    private AgentTurnView map(ResultSet result) throws SQLException {
        return new AgentTurnView(result.getString("id"), result.getString("session_id"),
                result.getString("client_request_id"), AgentTurnStatus.valueOf(result.getString("status")),
                result.getString("user_message"), result.getString("assistant_content"),
                result.getString("response_json"), result.getString("error_message"),
                instant(result, "created_at"), instant(result, "started_at"), instant(result, "completed_at"));
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        java.sql.Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
