package com.salarytracker.ai.operations;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentBudgetAlertIntegrationTest extends MySqlIntegrationTestSupport {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Test
    void createsEachThresholdOnceAndKeepsReadStateAndUsersIsolated() {
        long user = user();
        AgentOperationsService service = service(user);
        service.saveBudget(budget("CNY", "10", "10", true, true, true, 0L));
        usage(user, LocalDate.now(ZONE), "CNY", "5");
        alerts().evaluate(user);
        assertEquals(2, service.alerts().size());
        usage(user, LocalDate.now(ZONE), "CNY", "3");
        alerts().evaluate(user);
        assertEquals(4, service.alerts().size());
        usage(user, LocalDate.now(ZONE), "CNY", "2");
        alerts().evaluate(user);
        assertEquals(6, service.alerts().size());
        alerts().evaluate(user);
        long id = service.alerts().get(0).id();
        service.markAlertRead(id);
        service.markAlertRead(id);
        alerts().evaluate(user);
        assertEquals(6, service.alerts().size());
        assertEquals("READ", service.alerts().get(0).status());
        AgentOperationsService other = service(user());
        assertTrue(other.alerts().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> other.markAlertRead(id));
    }

    @Test
    void respectsSwitchesAndCurrencyAndReconcilesExistingSpendOnSave() {
        long user = user();
        usage(user, LocalDate.now(ZONE), "CNY", "10");
        usage(user, LocalDate.now(ZONE), "USD", "20");
        usage(user, LocalDate.now(ZONE), null, null);
        AgentOperationsService service = service(user);
        service.saveBudget(budget("CNY", "10", null, false, true, false, 0L));
        assertEquals(1, service.alerts().size());
        assertEquals(80, service.alerts().get(0).threshold());
        assertEquals(0, new BigDecimal("10").compareTo(service.alerts().get(0).current()));
        service.saveBudget(budget("USD", "10", null, false, true, false, 1L));
        assertEquals(2, service.alerts().size());
        assertEquals("USD", service.alerts().get(0).currency());
        assertThrows(IllegalArgumentException.class, () -> service.saveBudget(budget("EUR", "10", null, true, true, true, 2L)));
    }

    @Test
    void usesCalendarBoundariesAndDistinctPeriods() {
        long user = user();
        AgentOperationsService service = service(user);
        service.saveBudget(budget("CNY", "10", "10", true, false, false, 0L));
        LocalDate first = LocalDate.of(2020, 2, 29);
        usage(user, first.minusDays(1), "CNY", "5");
        AgentBudgetAlertService alerts = alerts();
        alerts.evaluate(user, first);
        assertEquals(1, service.alerts().size());
        assertEquals("MONTHLY", service.alerts().get(0).type());
        usage(user, first, "CNY", "5");
        alerts.evaluate(user, first);
        assertEquals(2, service.alerts().size());
        alerts.evaluate(user, first.plusDays(1));
        assertEquals(2, service.alerts().size());
        usage(user, first.plusDays(1), "CNY", "5");
        alerts.evaluate(user, first.plusDays(1));
        assertEquals(4, service.alerts().size());
        assertTrue(service.alerts().stream().anyMatch(a -> a.period().equals("2020-03:CNY")));
    }

    @Test
    void doesNotAlertOnUnpricedUsageOrUnconfiguredLimits() {
        long user = user();
        usage(user, LocalDate.now(ZONE), null, null);
        AgentOperationsService service = service(user);
        service.saveBudget(budget("CNY", "1", "1", true, true, true, 0L));
        assertTrue(service.alerts().isEmpty());
        service.saveBudget(budget("CNY", null, null, true, true, true, 1L));
        usage(user, LocalDate.now(ZONE), "CNY", "1000");
        alerts().evaluate(user);
        assertTrue(service.alerts().isEmpty());
    }

    @Test
    void runsAfterCommitInSeparateTransactionAndIsolatesAlertFailures() {
        long user = user();
        AgentOperationsService service = service(user);
        service.saveBudget(budget("CNY", "10", null, true, false, false, 0L));
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));
        AgentBudgetAlertService alerts = alerts();
        tx.executeWithoutResult(status -> {
            usage(user, LocalDate.now(ZONE), "CNY", "5");
            alerts.evaluateAfterCommit(user);
            assertTrue(service.alerts().isEmpty());
        });
        assertEquals(1, service.alerts().size(), "提交后产生的告警也必须持久化");
        AgentBudgetAlertService failing = spy(alerts());
        doThrow(new IllegalStateException("sensitive test exception")).when(failing).evaluate(user);
        assertDoesNotThrow(() -> tx.executeWithoutResult(status -> {
            jdbc.update("UPDATE app_user SET nickname='trace survives' WHERE id=?", user);
            failing.evaluateAfterCommit(user);
        }));
        assertEquals("trace survives", jdbc.queryForObject("SELECT nickname FROM app_user WHERE id=?", String.class, user));
        AgentBudgetAlertService rolledBack = spy(alerts());
        tx.executeWithoutResult(status -> { rolledBack.evaluateAfterCommit(user); status.setRollbackOnly(); });
        verify(rolledBack, never()).evaluate(user);
    }

    private AgentBudgetAlertService alerts() {
        return new AgentBudgetAlertService(jdbc, new DataSourceTransactionManager(jdbc.getDataSource()));
    }

    private AgentOperationsService service(long user) {
        CurrentUserResolver resolver = mock(CurrentUserResolver.class);
        when(resolver.id()).thenReturn(user);
        return new AgentOperationsService(jdbc, resolver, alerts());
    }

    private AgentOperationsService.BudgetCommand budget(String currency, String day, String month,
                                                       boolean at50, boolean at80, boolean at100, long revision) {
        return new AgentOperationsService.BudgetCommand(currency, day == null ? null : new BigDecimal(day),
                month == null ? null : new BigDecimal(month), null, null, "WARN", at50, at80, at100, revision);
    }

    private long user() {
        String name = "budget-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', '预算测试')", name);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, name);
    }

    private void usage(long user, LocalDate date, String currency, String cost) {
        String session = UUID.randomUUID().toString(), turn = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO agent_session(id,user_id,title) VALUES(?,?,'预算测试')", session, user);
        jdbc.update("INSERT INTO agent_turn(id,session_id,user_id,client_request_id,status,user_message) VALUES(?,?,?,?,'COMPLETED','测试')",
                turn, session, user, UUID.randomUUID().toString());
        jdbc.update("""
                INSERT INTO ai_usage(turn_id,session_id,user_id,provider_type,model_name,round_no,total_tokens,currency,estimated_total_cost,created_at)
                VALUES(?,?,?,'DEEPSEEK','deepseek-chat',1,100,?,?,?)
                """, turn, session, user, currency, cost == null ? null : new BigDecimal(cost),
                Timestamp.from(date.atTime(12, 0).atZone(ZONE).toInstant()));
    }
}
