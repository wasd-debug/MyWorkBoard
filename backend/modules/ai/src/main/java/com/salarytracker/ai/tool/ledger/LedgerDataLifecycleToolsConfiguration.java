package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import com.salarytracker.ledger.LedgerImportService;
import com.salarytracker.ledger.LedgerTransactionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerDataLifecycleToolsConfiguration {
    @Bean DomainTool ledgerRecycleListTool(LedgerBookService b, ObjectMapper m) { return new LedgerRecycleListTool(b, m); }
    @Bean DomainTool ledgerRecycleRestorePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return new LedgerRecycleRestorePrepareTool(b, a, u, m); }
    @Bean DomainTool ledgerRecycleRestoreCommitTool(LedgerBookService b, LedgerTransactionService t, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return new LedgerRecycleRestoreCommitTool(b, t, a, u, m); }
    @Bean DomainTool ledgerRecyclePurgePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return new LedgerRecyclePurgePrepareTool(b, a, u, m); }
    @Bean DomainTool ledgerExportPrepareTool(LedgerBookService b, LedgerTransactionService t, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return new LedgerExportPrepareTool(b, t, a, u, m); }
    @Bean DomainTool ledgerExportCommitTool(LedgerImportService i, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return new LedgerExportCommitTool(i, a, u, m); }
    @Bean DomainTool ledgerImportPreviewPrepareTool(LedgerBookService b, LedgerImportService i, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return new LedgerImportPreviewPrepareTool(b, i, a, u, m); }
}
