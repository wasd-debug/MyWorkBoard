package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerManagementToolsConfiguration {
    @Bean DomainTool ledgerBookCreatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.BOOK_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerBookCreateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.BOOK_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerBookUpdatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.BOOK_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerBookUpdateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.BOOK_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerBookDeletePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.BOOK_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerAccountCreatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.ACCOUNT_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerAccountCreateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.ACCOUNT_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerAccountUpdatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.ACCOUNT_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerAccountUpdateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.ACCOUNT_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerAccountDeletePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.ACCOUNT_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerAccountDeleteCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.ACCOUNT_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerCategoryCreatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.CATEGORY_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerCategoryCreateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.CATEGORY_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerCategoryUpdatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.CATEGORY_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerCategoryUpdateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.CATEGORY_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerCategoryDeletePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.CATEGORY_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerCategoryDeleteCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.CATEGORY_DELETE, b, a, u, m); }

    private DomainTool prepare(LedgerManagementToolMode mode, LedgerBookService books, PendingActionService actions,
                               CurrentUserResolver user, ObjectMapper mapper) {
        return new LedgerManagementPrepareTool(mode, books, actions, user, mapper);
    }

    private DomainTool commit(LedgerManagementToolMode mode, LedgerBookService books, PendingActionService actions,
                              CurrentUserResolver user, ObjectMapper mapper) {
        return new LedgerManagementCommitTool(mode, books, actions, user, mapper);
    }
}
