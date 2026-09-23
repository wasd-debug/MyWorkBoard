package com.salarytracker.ai;

import com.salarytracker.ai.session.AgentConversationService;
import com.salarytracker.ai.session.JdbcAgentSessionRepository;
import com.salarytracker.ai.session.JdbcAgentTurnRepository;
import com.salarytracker.ai.session.AgentTurnStatus;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void supportsSessionRecoveryLifecycleAndUserIsolation() {
        long firstUser = createUser("agent-session-a-");
        long secondUser = createUser("agent-session-b-");
        JdbcAgentSessionRepository repository = new JdbcAgentSessionRepository(jdbc);
        String sessionId = UUID.randomUUID().toString();
        repository.create(sessionId, firstUser, "原始标题");
        new JdbcAgentTurnRepository(jdbc).enqueue("turn-1", sessionId, firstUser, "request-1", "问题", null);
        repository.appendExchange(sessionId, firstUser, "turn-1", "问题", "回答", "{\"durationMs\":18}");

        assertEquals(1, repository.listSessions(firstUser, false).size());
        assertEquals(0, repository.listSessions(secondUser, false).size());
        assertEquals("turn-1", repository.messages(sessionId, firstUser).get(0).turnId());
        assertEquals(0, repository.messages(sessionId, secondUser).size());
        repository.rename(sessionId, firstUser, "新标题");
        assertEquals("新标题", repository.findSession(sessionId, firstUser).orElseThrow().title());
        repository.setPinned(sessionId, firstUser, true);
        assertTrue(repository.findSession(sessionId, firstUser).orElseThrow().pinnedAt() != null);
        var group = repository.createGroup("group-1", firstUser, "工作");
        assertEquals("工作", group.name());
        assertEquals(0, repository.listGroups(secondUser).size());
        repository.moveToGroup(sessionId, firstUser, group.id());
        assertEquals(group.id(), repository.findSession(sessionId, firstUser).orElseThrow().groupId());
        assertThrows(IllegalArgumentException.class,
                () -> repository.moveToGroup(sessionId, firstUser, "other-users-group"));
        repository.renameGroup(group.id(), firstUser, "重要工作");
        assertEquals("重要工作", repository.listGroups(firstUser).get(0).name());
        repository.deleteGroup(group.id(), firstUser);
        assertEquals(null, repository.findSession(sessionId, firstUser).orElseThrow().groupId());
        assertThrows(IllegalArgumentException.class, () -> repository.rename(sessionId, secondUser, "越权"));
        repository.archive(sessionId, firstUser);
        assertEquals(1, repository.listSessions(firstUser, true).size());
        repository.delete(sessionId, firstUser);
        assertTrue(repository.findSession(sessionId, firstUser).isEmpty());
    }

    @Test
    void keepsTurnsIdempotentAndTerminalStatesStable() {
        long userId = createUser("agent-turn-");
        JdbcAgentSessionRepository sessions = new JdbcAgentSessionRepository(jdbc);
        JdbcAgentTurnRepository turns = new JdbcAgentTurnRepository(jdbc);
        String sessionId = UUID.randomUUID().toString();
        sessions.create(sessionId, userId, "turn 测试");

        var original = turns.enqueue("turn-a", sessionId, userId, "request-a", "问题", null);
        var replay = turns.enqueue("turn-b", sessionId, userId, "request-a", "问题", null);
        assertEquals(original.id(), replay.id());
        assertThrows(IllegalArgumentException.class,
                () -> turns.enqueue("turn-c", UUID.randomUUID().toString(), userId, "request-a", "问题", null));
        assertEquals(AgentTurnStatus.QUEUED, original.status());
        assertEquals(original.id(), turns.claimNext(sessionId, userId).orElseThrow().id());
        turns.markPlanning(original.id(), userId);
        assertEquals(AgentTurnStatus.PLANNING, turns.find(original.id(), userId).orElseThrow().status());
        assertTrue(turns.cancel(original.id(), userId));
        assertTrue(!turns.complete(original.id(), userId, "不应覆盖", "{}"));
        assertEquals(AgentTurnStatus.CANCELLED, turns.find(original.id(), userId).orElseThrow().status());
        assertTrue(turns.find(original.id(), userId + 1).isEmpty());
    }

    @Test
    void persistsQueueOrderRevisionAndSingleRetry() {
        long userId = createUser("agent-queue-");
        JdbcAgentSessionRepository sessions = new JdbcAgentSessionRepository(jdbc);
        JdbcAgentTurnRepository turns = new JdbcAgentTurnRepository(jdbc);
        String sessionId = UUID.randomUUID().toString();
        sessions.create(sessionId, userId, "queue 测试");

        var first = turns.enqueue("queue-a", sessionId, userId, "queue-request-a", "第一条", null);
        var second = turns.enqueue("queue-b", sessionId, userId, "queue-request-b", "第二条", null);
        long revision = turns.queueRevision(sessionId, userId);
        long reordered = turns.reorder(sessionId, userId, revision, List.of(second.id(), first.id()));
        assertEquals(List.of(second.id(), first.id()), turns.listQueue(sessionId, userId).stream()
                .map(com.salarytracker.ai.session.AgentTurnRepository.AgentTurnView::id).toList());
        assertThrows(IllegalStateException.class,
                () -> turns.reorder(sessionId, userId, revision, List.of(first.id(), second.id())));

        var claimed = turns.claimNext(sessionId, userId).orElseThrow();
        assertEquals(second.id(), claimed.id());
        assertTrue(turns.claimNext(sessionId, userId).isEmpty());
        turns.markPlanning(second.id(), userId);
        assertTrue(turns.fail(second.id(), userId, "模拟失败"));
        var retry = turns.enqueue("queue-retry-a", sessionId, userId, "queue-retry-request-a", "第二条", second.id());
        var duplicateRetry = turns.enqueue("queue-retry-b", sessionId, userId, "queue-retry-request-b", "第二条", second.id());
        assertEquals(retry.id(), duplicateRetry.id());
        assertTrue(turns.queueRevision(sessionId, userId) > reordered);
        assertTrue(turns.find(retry.id(), userId + 1).isEmpty());
    }

    private long createUser(String prefix) {
        String username = prefix + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'agent')", username);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }
}
