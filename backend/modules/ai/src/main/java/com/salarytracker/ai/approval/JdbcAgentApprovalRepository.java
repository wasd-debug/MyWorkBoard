package com.salarytracker.ai.approval;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAgentApprovalRepository implements AgentApprovalRepository {
    private final JdbcTemplate jdbc;
    private final RowMapper<AgentApproval> mapper = (result, rowNum) -> new AgentApproval(
            result.getString("id"), result.getLong("user_id"), result.getString("action_id"),
            result.getString("tool_name"), result.getInt("tool_version"), result.getString("book_id"),
            ApprovalStatus.valueOf(result.getString("status")), result.getString("summary"),
            result.getString("payload_json"), result.getString("result_json"),
            result.getTimestamp("expires_at").toInstant(), instant(result.getTimestamp("approved_at")),
            instant(result.getTimestamp("rejected_at")), instant(result.getTimestamp("executed_at")),
            result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant());

    public JdbcAgentApprovalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(AgentApproval approval) {
        jdbc.update("INSERT INTO agent_approval(id,user_id,action_id,tool_name,tool_version,book_id,status," +
                        "summary,payload_json,result_json,expires_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                approval.id(), approval.userId(), approval.actionId(), approval.toolName(), approval.toolVersion(),
                approval.bookId(), approval.status().name(), approval.summary(), approval.payloadJson(),
                approval.resultJson(), Timestamp.from(approval.expiresAt()), Timestamp.from(approval.createdAt()),
                Timestamp.from(approval.updatedAt()));
    }

    @Override
    public Optional<AgentApproval> find(String id, long userId) {
        return jdbc.query("SELECT * FROM agent_approval WHERE id=? AND user_id=?", mapper, id, userId)
                .stream().findFirst();
    }

    @Override
    public List<AgentApproval> list(long userId, ApprovalStatus status) {
        if (status == null) {
            return jdbc.query("SELECT * FROM agent_approval WHERE user_id=? ORDER BY created_at DESC LIMIT 200",
                    mapper, userId);
        }
        return jdbc.query("SELECT * FROM agent_approval WHERE user_id=? AND status=? ORDER BY created_at DESC LIMIT 200",
                mapper, userId, status.name());
    }

    @Override
    public boolean transition(String id, long userId, ApprovalStatus expected, ApprovalStatus next,
                              String resultJson, Instant eventAt) {
        String eventColumn = switch (next) {
            case APPROVED -> "approved_at";
            case REJECTED -> "rejected_at";
            case COMPLETED, FAILED -> "executed_at";
            default -> null;
        };
        String sql = "UPDATE agent_approval SET status=?,result_json=COALESCE(?,result_json),updated_at=?" +
                (eventColumn == null ? "" : "," + eventColumn + "=?") + " WHERE id=? AND user_id=? AND status=?";
        int changed = eventColumn == null
                ? jdbc.update(sql, next.name(), resultJson, Timestamp.from(eventAt), id, userId, expected.name())
                : jdbc.update(sql, next.name(), resultJson, Timestamp.from(eventAt), Timestamp.from(eventAt),
                id, userId, expected.name());
        return changed == 1;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
