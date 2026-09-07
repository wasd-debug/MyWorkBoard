package com.salarytracker.ledger;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

@Component
public class RecurringBillScheduler {
    private final RecurringBillService recurring;
    private final LedgerService ledger;

    public RecurringBillScheduler(RecurringBillService recurring, LedgerService ledger) { this.recurring = recurring; this.ledger = ledger; }

    @Scheduled(cron = "0 5 0 * * *")
    @SchedulerLock(name = "ledgerRecurringBills", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void generateDueBills() { recurring.runDue(); }

    @Scheduled(cron = "0 30 23 L * *")
    @SchedulerLock(name = "ledgerMonthEndBalance", lockAtMostFor = "PT20M", lockAtLeastFor = "PT1S")
    public void materializeMonthEnd() { ledger.materializeAllUsers(java.time.YearMonth.now().minusMonths(1).toString()); }
}
