package com.salarytracker.ai;

import com.salarytracker.ai.operations.AgentOperationsService;
import com.salarytracker.ai.operations.AgentBudgetAlertService;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOperationsIntegrationTest extends MySqlIntegrationTestSupport {
    @Test
    void aggregatesOnlyCurrentUserAndResolvesCustomGranularity() {
        long owner = createUser("ops-owner");
        long other = createUser("ops-other");
        insertTurn(owner, "COMPLETED", Instant.now().minus(2, ChronoUnit.HOURS));
        insertTurn(other, "FAILED", Instant.now().minus(2, ChronoUnit.HOURS));
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(owner);
        AgentOperationsService service = new AgentOperationsService(jdbc, currentUser, alerts());

        AgentOperationsService.Metrics metrics = service.metrics("CUSTOM", "DAY", "2026-01-01", "2026-12-31");

        assertEquals("CUSTOM", metrics.preset());
        assertEquals("DAY", metrics.granularity());
        assertEquals(1, metrics.summary().turns());
        assertEquals(1, metrics.summary().completed());
        assertEquals(0, metrics.summary().failed());
    }

    @Test
    void savesBudgetWithOptimisticRevision() {
        long user = createUser("ops-budget");
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(user);
        AgentOperationsService service = new AgentOperationsService(jdbc, currentUser, alerts());

        AgentOperationsService.Budget saved = service.saveBudget(new AgentOperationsService.BudgetCommand(
                "CNY", new java.math.BigDecimal("10"), new java.math.BigDecimal("100"), null, 5000L,
                "WARN", true, true, true, 0L));

        assertEquals("CNY", saved.currency());
        assertEquals(new java.math.BigDecimal("10.000000000000"), saved.dailyLimit().setScale(12));
        assertEquals(1L, saved.revision());
        AgentOperationsService.Budget changed = service.saveBudget(new AgentOperationsService.BudgetCommand(
                "USD", new BigDecimal("20"), null, null, null, "WARN", true, true, true, saved.revision()));
        assertEquals(2L, changed.revision());
        assertThrows(IllegalStateException.class, () -> service.saveBudget(new AgentOperationsService.BudgetCommand(
                "CNY", null, null, null, null, "WARN", true, true, true, saved.revision())));
        assertEquals("USD", service.budget().currency());
    }

    @Test
    void resolvesAllTimePresetsAndAutomaticGranularity() {
        AgentOperationsService service = serviceFor(createUser("ops-window"));

        assertEquals("HOUR", service.metrics("TODAY", "AUTO", null, null).granularity());
        assertEquals("DAY", service.metrics("THIS_WEEK", "AUTO", null, null).granularity());
        assertEquals("DAY", service.metrics("THIS_MONTH", "AUTO", null, null).granularity());
        assertEquals("DAY", service.metrics("LAST_7_DAYS", "AUTO", null, null).granularity());
        assertEquals("DAY", service.metrics("LAST_30_DAYS", "AUTO", null, null).granularity());
        assertEquals("WEEK", service.metrics("CUSTOM", "AUTO", "2026-01-01", "2026-12-31").granularity());
        assertThrows(IllegalArgumentException.class,
                () -> service.metrics("CUSTOM", "DAY", "2026-02-01", "2026-01-01"));
        assertThrows(IllegalArgumentException.class,
                () -> service.metrics("CUSTOM", "DAY", "not-a-date", "2026-01-01"));
        assertThrows(IllegalArgumentException.class,
                () -> service.metrics("NOT_A_PRESET", "DAY", null, null));
    }

    @Test
    void keepsCurrenciesSeparateAndCalculatesCalendarBudgetProgress() {
        long user = createUser("ops-currency");
        AgentOperationsService service = serviceFor(user);
        service.saveBudget(new AgentOperationsService.BudgetCommand(
                "CNY", new BigDecimal("10"), new BigDecimal("100"), null, null,
                "HARD_LIMIT", true, true, true, 0L));
        insertUsage(user, "CNY", new BigDecimal("2.50"), Instant.now());
        insertUsage(user, "USD", new BigDecimal("99.00"), Instant.now());

        AgentOperationsService.Metrics metrics = service.metrics("TODAY", "AUTO", null, null);

        assertEquals(0, new BigDecimal("2.50").compareTo(metrics.summary().totalCost()));
        assertTrue(metrics.trend().get(0).bucket().contains("T"), "今日自动粒度应按小时分桶");
        assertEquals(2, metrics.models().size());
        assertEquals(0, new BigDecimal("2.50").compareTo(metrics.budgetProgress().dailyCost()));
        assertEquals(0, new BigDecimal("2.50").compareTo(metrics.budgetProgress().monthlyCost()));
        assertEquals(0, new BigDecimal("25.0").compareTo(metrics.budgetProgress().dailyPercent()));
        assertEquals(0, new BigDecimal("2.5").compareTo(metrics.budgetProgress().monthlyPercent()));
        assertEquals("WARN", metrics.budget().enforcementMode());
    }

    @Test
    void paginatesCallsAndFiltersFailuresWithoutLeakingOtherUsersOrRawErrors() {
        long user = createUser("ops-calls");
        AgentOperationsService service = serviceFor(user);
        for (int i = 0; i < 22; i++) insertTurnFixture(user, "COMPLETED", Instant.now().minusSeconds(i));
        TurnFixture failed = insertTurnFixture(user, "FAILED", Instant.now().minusSeconds(30));
        TurnFixture toolFailed = insertTurnFixture(user, "COMPLETED", Instant.now().minusSeconds(31));
        jdbc.update("UPDATE agent_turn SET error_message='secret-test-value' WHERE id=?", failed.turnId());
        jdbc.update("""
                INSERT INTO agent_tool_call(turn_id,session_id,user_id,sequence_no,tool_name,status,duration_ms,result_summary)
                VALUES(?,?,?,1,'ledger.books.list','FAILED',123,'secret-tool-result')
                """, toolFailed.turnId(), toolFailed.sessionId(), user);
        TurnFixture other = insertTurnFixture(createUser("ops-calls-other"), "FAILED", Instant.now());
        assertEquals(21, service.calls("LAST_7_DAYS", null, null, false, 0).size());
        assertEquals(4, service.calls("LAST_7_DAYS", null, null, false, 1).size());
        var failures = service.calls("LAST_7_DAYS", null, null, true, 0);
        assertEquals(2, failures.size());
        assertTrue(failures.stream().anyMatch(c -> c.turnId().equals(toolFailed.turnId()) && c.toolCount() == 1));
        var trace = service.callTrace(toolFailed.turnId());
        assertEquals(123, trace.tools().get(0).durationMs());
        assertNotNull(trace.failureSummary());
        assertFalse(trace.toString().contains("secret"));
        assertFalse(service.callTrace(failed.turnId()).toString().contains("secret"));
        assertFalse(service.metrics("LAST_7_DAYS", "AUTO", null, null).failures().toString().contains("secret"));
        assertThrows(IllegalArgumentException.class, () -> service.callTrace(other.turnId()));
        assertThrows(IllegalArgumentException.class, () -> service.calls("TODAY", null, null, false, -1));
        assertThrows(IllegalArgumentException.class, () -> service.calls("TODAY", null, null, false, 1001));
    }

    @Test
    void returnsPerRoundUsageAndCostsForOwnedCalls() {
        long user = createUser("ops-trace");
        insertUsage(user, "CNY", new BigDecimal("1.25"), Instant.now());
        AgentOperationsService service = serviceFor(user);
        var call = service.calls("LAST_7_DAYS", null, null, false, 0).get(0);
        assertEquals(100, call.tokens());
        assertEquals(120, call.firstTokenMs());
        assertEquals(1000, call.durationMs());
        var trace = service.callTrace(call.turnId());
        assertEquals(1, trace.usage().size());
        assertEquals(0, new BigDecimal("1.25").compareTo(trace.usage().get(0).cost()));
        assertNull(trace.failureSummary());
    }

    private long createUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', ?)", username, prefix);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private void insertTurn(long userId, String status, Instant createdAt) {
        insertTurnFixture(userId, status, createdAt);
    }

    private TurnFixture insertTurnFixture(long userId, String status, Instant createdAt) {
        String session = UUID.randomUUID().toString();
        String turn = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO agent_session(id,user_id,title) VALUES(?,?, '运行质量测试')", session, userId);
        jdbc.update("""
                INSERT INTO agent_turn(id,session_id,user_id,client_request_id,status,user_message,created_at,started_at,completed_at,error_message)
                VALUES(?,?,?,? ,?,?,?, ?,?,?)
                """, turn, session, userId, UUID.randomUUID().toString(), status, "测试消息",
                Timestamp.from(createdAt), Timestamp.from(createdAt), Timestamp.from(createdAt.plusSeconds(1)),
                "FAILED".equals(status) ? "测试失败" : null);
        return new TurnFixture(session, turn);
    }

    private void insertUsage(long userId, String currency, BigDecimal cost, Instant createdAt) {
        TurnFixture fixture = insertTurnFixture(userId, "COMPLETED", createdAt);
        jdbc.update("""
                INSERT INTO ai_usage(turn_id,session_id,user_id,provider_type,model_name,round_no,
                                     total_tokens,first_token_ms,duration_ms,currency,estimated_total_cost,created_at)
                VALUES(?,?,?,'DEEPSEEK','deepseek-chat',1,100,120,350,?,?,?)
                """, fixture.turnId(), fixture.sessionId(), userId, currency, cost, Timestamp.from(createdAt));
    }

    private AgentOperationsService serviceFor(long userId) {
        CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
        when(currentUser.id()).thenReturn(userId);
        return new AgentOperationsService(jdbc, currentUser, alerts());
    }

    private AgentBudgetAlertService alerts() {
        return new AgentBudgetAlertService(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    private record TurnFixture(String sessionId, String turnId) { }
}
