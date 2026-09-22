package com.salarytracker.ai.action;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class JdbcPendingActionRepository implements PendingActionRepository {
    private final JdbcTemplate jdbc;

    public JdbcPendingActionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(PendingAction action) {
        try {
            jdbc.update("INSERT INTO agent_pending_action " +
                            "(id,user_id,tool_name,tool_version,input_json,expected_revision,status,expires_at,created_at,updated_at) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    action.id(), action.userId(), action.toolName(), action.toolVersion(), action.inputSnapshot(),
                    action.expectedRevision(), action.status().name(), Timestamp.from(action.expiresAt()),
                    Timestamp.from(action.updatedAt()), Timestamp.from(action.updatedAt()));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("actionId 已存在", exception);
        }
    }

    @Override
    public Optional<PendingAction> find(String id, long userId) {
        return jdbc.query("SELECT id,user_id,tool_name,tool_version,input_json,expected_revision,status," +
                        "expires_at,updated_at FROM agent_pending_action WHERE id=? AND user_id=?",
                (result, rowNum) -> new PendingAction(result.getString("id"), result.getLong("user_id"),
                        result.getString("tool_name"), result.getInt("tool_version"),
                        result.getString("input_json"), nullableLong(result, "expected_revision"),
                        ActionStatus.valueOf(result.getString("status")),
                        result.getTimestamp("expires_at").toInstant(), result.getTimestamp("updated_at").toInstant()),
                id, userId).stream().findFirst();
    }

    @Override
    public boolean transition(String id, long userId, ActionStatus expected, ActionStatus next, Instant updatedAt) {
        return jdbc.update("UPDATE agent_pending_action SET status=?,updated_at=? " +
                        "WHERE id=? AND user_id=? AND status=?",
                next.name(), Timestamp.from(updatedAt), id, userId, expected.name()) == 1;
    }

    private Long nullableLong(java.sql.ResultSet result, String column) throws java.sql.SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }
}
