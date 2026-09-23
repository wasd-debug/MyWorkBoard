package com.salarytracker.ai.session;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAgentTurnRepository implements AgentTurnRepository {
    private final JdbcTemplate jdbc;

    public JdbcAgentTurnRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public AgentTurnView enqueue(String turnId, String sessionId, long userId, String clientRequestId,
                                 String userMessage, String retryOfTurnId) {
        try {
            Long next = jdbc.queryForObject("""
                    SELECT COALESCE(MAX(queue_position),0)+1000 FROM agent_turn
                    WHERE session_id=? AND user_id=? AND status IN ('QUEUED','RECEIVED','PLANNING')
                    """, Long.class, sessionId, userId);
            jdbc.update("""
                    INSERT INTO agent_turn(id,session_id,user_id,client_request_id,retry_of_turn_id,status,queue_position,user_message)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, turnId, sessionId, userId, clientRequestId, retryOfTurnId,
                    AgentTurnStatus.QUEUED.name(), next == null ? 1000 : next, userMessage);
            bumpRevision(sessionId, userId);
        } catch (DuplicateKeyException ignored) {
            // request id and retry source are both idempotency boundaries.
        }
        List<AgentTurnView> rows = jdbc.query("SELECT * FROM agent_turn WHERE user_id=? AND client_request_id=?",
                (result, rowNum) -> map(result), userId, clientRequestId);
        if (rows.isEmpty() && retryOfTurnId != null) {
            rows = jdbc.query("SELECT * FROM agent_turn WHERE user_id=? AND retry_of_turn_id=?",
                    (result, rowNum) -> map(result), userId, retryOfTurnId);
        }
        if (rows.isEmpty() || !rows.get(0).sessionId().equals(sessionId)) {
            throw new IllegalArgumentException("请求标识已被其他会话使用");
        }
        return rows.get(0);
    }

    @Override public Optional<AgentTurnView> find(String turnId, long userId) {
        return jdbc.query("SELECT * FROM agent_turn WHERE id=? AND user_id=?", (r, n) -> map(r), turnId, userId)
                .stream().findFirst();
    }

    @Override public List<AgentTurnView> listQueue(String sessionId, long userId) {
        return jdbc.query("""
                SELECT * FROM agent_turn WHERE session_id=? AND user_id=?
                AND status IN ('QUEUED','RECEIVED','PLANNING')
                ORDER BY CASE WHEN status IN ('RECEIVED','PLANNING') THEN 0 ELSE 1 END, queue_position, created_at
                """, (r, n) -> map(r), sessionId, userId);
    }

    @Override public long queueRevision(String sessionId, long userId) {
        List<Long> rows = jdbc.query("SELECT queue_revision FROM agent_session WHERE id=? AND user_id=?",
                (r, n) -> r.getLong(1), sessionId, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("会话不存在");
        return rows.get(0);
    }

    @Override
    @Transactional
    public long reorder(String sessionId, long userId, long expectedRevision, List<String> turnIds) {
        List<AgentTurnView> queued = jdbc.query("""
                SELECT * FROM agent_turn WHERE session_id=? AND user_id=? AND status='QUEUED'
                ORDER BY queue_position,created_at
                """, (r, n) -> map(r), sessionId, userId);
        List<String> current = queued.stream().map(AgentTurnView::id).toList();
        if (turnIds.size() != current.size() || !current.containsAll(turnIds)) {
            throw new IllegalArgumentException("队列内容已变化，请刷新后重试");
        }
        int changed = jdbc.update("""
                UPDATE agent_session SET queue_revision=queue_revision+1
                WHERE id=? AND user_id=? AND queue_revision=?
                """, sessionId, userId, expectedRevision);
        if (changed != 1) throw new IllegalStateException("队列版本冲突，请刷新后重试");
        for (int index = 0; index < turnIds.size(); index++) {
            jdbc.update("UPDATE agent_turn SET queue_position=? WHERE id=? AND user_id=? AND status='QUEUED'",
                    (index + 1L) * 1000L, turnIds.get(index), userId);
        }
        return expectedRevision + 1;
    }

    @Override
    @Transactional
    public Optional<AgentTurnView> claimNext(String sessionId, long userId) {
        List<Long> revisions = jdbc.query("""
                SELECT queue_revision FROM agent_session WHERE id=? AND user_id=? FOR UPDATE
                """, (r, n) -> r.getLong(1), sessionId, userId);
        if (revisions.isEmpty()) throw new IllegalArgumentException("会话不存在");
        Integer running = jdbc.queryForObject("""
                SELECT COUNT(*) FROM agent_turn WHERE session_id=? AND user_id=? AND status IN ('RECEIVED','PLANNING')
                """, Integer.class, sessionId, userId);
        if (running != null && running > 0) return Optional.empty();
        List<String> ids = jdbc.query("""
                SELECT id FROM agent_turn WHERE session_id=? AND user_id=? AND status='QUEUED'
                ORDER BY queue_position,created_at LIMIT 1 FOR UPDATE
                """, (r, n) -> r.getString(1), sessionId, userId);
        if (ids.isEmpty()) return Optional.empty();
        String id = ids.get(0);
        if (jdbc.update("""
                UPDATE agent_turn SET status='RECEIVED',started_at=COALESCE(started_at,CURRENT_TIMESTAMP(6))
                WHERE id=? AND user_id=? AND status='QUEUED'
                """, id, userId) != 1) return Optional.empty();
        bumpRevision(sessionId, userId);
        return find(id, userId);
    }

    @Override public void markPlanning(String turnId, long userId) {
        jdbc.update("UPDATE agent_turn SET status='PLANNING' WHERE id=? AND user_id=? AND status='RECEIVED'",
                turnId, userId);
    }

    @Override public List<PendingQueue> pendingQueues(int limit) {
        return jdbc.query("""
                SELECT session_id,user_id FROM agent_turn queued
                WHERE status='QUEUED' AND NOT EXISTS (
                    SELECT 1 FROM agent_turn running
                    WHERE running.session_id=queued.session_id AND running.user_id=queued.user_id
                    AND running.status IN ('RECEIVED','PLANNING')
                )
                GROUP BY session_id,user_id ORDER BY MIN(queue_position),MIN(created_at) LIMIT ?
                """, (r, n) -> new PendingQueue(r.getString("session_id"), r.getLong("user_id")), limit);
    }

    @Override @Transactional public int requeueInterrupted() {
        List<PendingQueue> queues = jdbc.query("""
                SELECT DISTINCT session_id,user_id FROM agent_turn WHERE status IN ('RECEIVED','PLANNING')
                """, (r, n) -> new PendingQueue(r.getString("session_id"), r.getLong("user_id")));
        int changed = jdbc.update("""
                UPDATE agent_turn SET status='QUEUED',started_at=NULL
                WHERE status IN ('RECEIVED','PLANNING')
                """);
        queues.forEach(queue -> bumpRevision(queue.sessionId(), queue.userId()));
        return changed;
    }

    @Override @Transactional public boolean complete(String id, long userId, String content, String json) {
        Optional<AgentTurnView> before = find(id, userId);
        boolean changed = jdbc.update("""
                UPDATE agent_turn SET status='COMPLETED',assistant_content=?,response_json=?,completed_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND status IN ('RECEIVED','PLANNING')
                """, content, json, id, userId) == 1;
        if (changed && before.isPresent()) bumpRevision(before.get().sessionId(), userId);
        return changed;
    }

    @Override @Transactional public boolean fail(String id, long userId, String error) {
        Optional<AgentTurnView> before = find(id, userId);
        boolean changed = jdbc.update("""
                UPDATE agent_turn SET status='FAILED',error_message=?,completed_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND status IN ('RECEIVED','PLANNING')
                """, error, id, userId) == 1;
        if (changed && before.isPresent()) bumpRevision(before.get().sessionId(), userId);
        return changed;
    }

    @Override
    @Transactional
    public boolean cancel(String id, long userId) {
        Optional<AgentTurnView> before = find(id, userId);
        boolean changed = jdbc.update("""
                UPDATE agent_turn SET status='CANCELLED',completed_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND status IN ('QUEUED','RECEIVED','PLANNING')
                """, id, userId) == 1;
        if (changed && before.isPresent()) bumpRevision(before.get().sessionId(), userId);
        return changed;
    }

    @Override @Transactional public boolean cancelQueued(String id, long userId) {
        Optional<AgentTurnView> before = find(id, userId);
        boolean changed = jdbc.update("""
                UPDATE agent_turn SET status='CANCELLED',completed_at=CURRENT_TIMESTAMP(6)
                WHERE id=? AND user_id=? AND status='QUEUED'
                """, id, userId) == 1;
        if (changed && before.isPresent()) bumpRevision(before.get().sessionId(), userId);
        return changed;
    }

    private void bumpRevision(String sessionId, long userId) {
        jdbc.update("UPDATE agent_session SET queue_revision=queue_revision+1 WHERE id=? AND user_id=?", sessionId, userId);
    }

    private AgentTurnView map(ResultSet r) throws SQLException {
        return new AgentTurnView(r.getString("id"), r.getString("session_id"), r.getLong("user_id"),
                r.getString("client_request_id"), r.getString("retry_of_turn_id"), AgentTurnStatus.valueOf(r.getString("status")),
                r.getLong("queue_position"), r.getString("user_message"), r.getString("assistant_content"),
                r.getString("response_json"), r.getString("error_message"), instant(r, "created_at"),
                instant(r, "started_at"), instant(r, "completed_at"));
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        java.sql.Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
