package com.salarytracker.ledger;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class LedgerMaintenanceScheduler {
    private final LedgerTransactionService transactions;

    public LedgerMaintenanceScheduler(LedgerTransactionService transactions) {
        this.transactions = transactions;
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
}
