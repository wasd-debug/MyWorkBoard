package com.salarytracker.ai.operations;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class AgentBudgetAlertService {
    private static final Logger LOG = LoggerFactory.getLogger(AgentBudgetAlertService.class);
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate alertTransaction;

    public AgentBudgetAlertService(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.alertTransaction = new TransactionTemplate(transactions);
        this.alertTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void reconcile() {
        try {
            LocalDate month = LocalDate.now(ZONE).withDayOfMonth(1);
            for (Long userId : jdbc.queryForList("""
                    SELECT b.user_id FROM ai_usage_budget b
                    WHERE (b.daily_limit>0 OR b.monthly_limit>0)
                      AND (b.alert_at_50 OR b.alert_at_80 OR b.alert_at_100)
                      AND EXISTS (SELECT 1 FROM ai_usage u WHERE u.user_id=b.user_id
                        AND u.created_at>=? AND u.created_at<?)
                    """, Long.class, month.atStartOfDay(ZONE).toInstant(),
                    month.plusMonths(1).atStartOfDay(ZONE).toInstant())) evaluateSafely(userId);
        } catch (RuntimeException e) {
            LOG.warn("Budget alert reconciliation failed ({})", e.getClass().getSimpleName());
        }
    }

    public void evaluateAfterCommit(long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { evaluateSafely(userId); }
            });
        } else evaluateSafely(userId);
    }

    private void evaluateSafely(long userId) {
        try { alertTransaction.executeWithoutResult(status -> evaluate(userId)); }
        catch (RuntimeException e) {
            LOG.warn("Budget alert evaluation failed for user {} ({})", userId, e.getClass().getSimpleName());
        }
    }

    public void evaluate(long userId) {
        evaluate(userId, LocalDate.now(ZONE));
    }

    void evaluate(long userId, LocalDate today) {
        List<BudgetRow> budgets = jdbc.query("""
                SELECT currency,daily_limit,monthly_limit,alert_at_50,alert_at_80,alert_at_100
                FROM ai_usage_budget WHERE user_id=?
                """, (r, n) -> new BudgetRow(r.getString("currency"), r.getBigDecimal("daily_limit"),
                r.getBigDecimal("monthly_limit"), r.getBoolean("alert_at_50"),
                r.getBoolean("alert_at_80"), r.getBoolean("alert_at_100")), userId);
        if (budgets.isEmpty()) return;
        BudgetRow budget = budgets.get(0);
        evaluatePeriod(userId, budget, "DAILY", today.toString(), budget.dailyLimit(),
                today, today.plusDays(1));
        evaluatePeriod(userId, budget, "MONTHLY", today.toString().substring(0, 7), budget.monthlyLimit(),
                today.withDayOfMonth(1), today.withDayOfMonth(1).plusMonths(1));
    }

    private void evaluatePeriod(long userId, BudgetRow budget, String type, String period,
                                BigDecimal limit, LocalDate start, LocalDate end) {
        if (limit == null || limit.signum() <= 0) return;
        BigDecimal spent = jdbc.queryForObject("""
                SELECT COALESCE(SUM(estimated_total_cost),0) FROM ai_usage
                WHERE user_id=? AND currency=? AND created_at>=? AND created_at<?
                """, BigDecimal.class, userId, budget.currency(), start.atStartOfDay(ZONE).toInstant(),
                end.atStartOfDay(ZONE).toInstant());
        for (int threshold : new int[]{50, 80, 100}) {
            if (threshold == 50 && !budget.at50() || threshold == 80 && !budget.at80()
                    || threshold == 100 && !budget.at100()) continue;
            if (spent.multiply(BigDecimal.valueOf(100)).compareTo(limit.multiply(BigDecimal.valueOf(threshold))) < 0) continue;
            jdbc.update("""
                    INSERT IGNORE INTO ai_usage_alert
                      (user_id,alert_type,period_key,threshold_percent,current_value,limit_value,currency,status)
                    VALUES(?,?,?,?,?,?,?,'OPEN')
                    """, userId, type, period + ":" + budget.currency(), threshold, spent, limit, budget.currency());
        }
    }

    private record BudgetRow(String currency, BigDecimal dailyLimit, BigDecimal monthlyLimit,
                             boolean at50, boolean at80, boolean at100) { }
}
