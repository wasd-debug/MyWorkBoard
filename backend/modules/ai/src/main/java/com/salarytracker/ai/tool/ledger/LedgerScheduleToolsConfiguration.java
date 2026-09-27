package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerScheduledTaskService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerScheduleToolsConfiguration {
    @Bean DomainTool ledgerScheduleListTool(LedgerScheduledTaskService s, ObjectMapper m) { return new LedgerScheduleListTool(s, m); }
    @Bean DomainTool ledgerScheduleCreatePrepareTool(LedgerScheduledTaskService s, LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerScheduleToolMode.CREATE, s, b, a, u, m); }
    @Bean DomainTool ledgerScheduleCreateCommitTool(LedgerScheduledTaskService s, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerScheduleToolMode.CREATE, s, a, u, m); }
    @Bean DomainTool ledgerScheduleUpdatePrepareTool(LedgerScheduledTaskService s, LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerScheduleToolMode.UPDATE, s, b, a, u, m); }
    @Bean DomainTool ledgerScheduleUpdateCommitTool(LedgerScheduledTaskService s, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerScheduleToolMode.UPDATE, s, a, u, m); }
    @Bean DomainTool ledgerScheduleDeletePrepareTool(LedgerScheduledTaskService s, LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerScheduleToolMode.DELETE, s, b, a, u, m); }
    @Bean DomainTool ledgerScheduleDeleteCommitTool(LedgerScheduledTaskService s, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerScheduleToolMode.DELETE, s, a, u, m); }
    @Bean DomainTool ledgerScheduleRunPrepareTool(LedgerScheduledTaskService s, LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerScheduleToolMode.RUN, s, b, a, u, m); }
    @Bean DomainTool ledgerScheduleRunCommitTool(LedgerScheduledTaskService s, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerScheduleToolMode.RUN, s, a, u, m); }

    private DomainTool prepare(LedgerScheduleToolMode mode, LedgerScheduledTaskService schedules,
                               LedgerBookService books, PendingActionService actions,
                               CurrentUserResolver user, ObjectMapper mapper) {
        return new LedgerSchedulePrepareTool(mode, schedules, books, actions, user, mapper);
    }

    private DomainTool commit(LedgerScheduleToolMode mode, LedgerScheduledTaskService schedules,
                              PendingActionService actions, CurrentUserResolver user, ObjectMapper mapper) {
        return new LedgerScheduleCommitTool(mode, schedules, actions, user, mapper);
    }
}
