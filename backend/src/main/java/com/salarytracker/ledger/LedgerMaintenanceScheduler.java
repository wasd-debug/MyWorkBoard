package com.salarytracker.ledger;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class LedgerMaintenanceScheduler {
    private final LedgerTransactionService transactions;
    private final LedgerScheduledTaskService scheduledTasks;

    public LedgerMaintenanceScheduler(LedgerTransactionService transactions, LedgerScheduledTaskService scheduledTasks) {
        this.transactions = transactions;
        this.scheduledTasks = scheduledTasks;
    }

    @Scheduled(cron = "0 10 0 1 * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "ledgerMonthEndBalance", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1S")
    public void materializePreviousMonth() {
        transactions.materializeAll(YearMonth.now().minusMonths(1));
    }

    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "ledgerRecycleRetention", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1S")
    public void purgeExpiredRecycle() {
        transactions.purgeExpiredRecycle();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Shanghai")
    @SchedulerLock(name = "ledgerScheduledTasks", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1S")
    public void runScheduledTasks() {
        scheduledTasks.runDueTasks();
    }
}
