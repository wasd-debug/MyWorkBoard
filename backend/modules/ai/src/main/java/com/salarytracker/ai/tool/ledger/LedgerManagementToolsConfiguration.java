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
    @Bean DomainTool ledgerMerchantListTool(LedgerBookService b, ObjectMapper m) { return new LedgerNamedResourceListTool("merchant", "商家", b, m); }
    @Bean DomainTool ledgerProjectListTool(LedgerBookService b, ObjectMapper m) { return new LedgerNamedResourceListTool("project", "项目", b, m); }
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
    @Bean DomainTool ledgerMerchantCreatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.MERCHANT_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerMerchantCreateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.MERCHANT_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerMerchantUpdatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.MERCHANT_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerMerchantUpdateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.MERCHANT_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerMerchantDeletePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.MERCHANT_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerMerchantDeleteCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.MERCHANT_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerProjectCreatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.PROJECT_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerProjectCreateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.PROJECT_CREATE, b, a, u, m); }
    @Bean DomainTool ledgerProjectUpdatePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.PROJECT_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerProjectUpdateCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.PROJECT_UPDATE, b, a, u, m); }
    @Bean DomainTool ledgerProjectDeletePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.PROJECT_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerProjectDeleteCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.PROJECT_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerBudgetUpsertPrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.BUDGET_UPSERT, b, a, u, m); }
    @Bean DomainTool ledgerBudgetUpsertCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.BUDGET_UPSERT, b, a, u, m); }
    @Bean DomainTool ledgerBudgetDeletePrepareTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerManagementToolMode.BUDGET_DELETE, b, a, u, m); }
    @Bean DomainTool ledgerBudgetDeleteCommitTool(LedgerBookService b, PendingActionService a, CurrentUserResolver u, ObjectMapper m) { return commit(LedgerManagementToolMode.BUDGET_DELETE, b, a, u, m); }

    private DomainTool prepare(LedgerManagementToolMode mode, LedgerBookService books, PendingActionService actions,
                               CurrentUserResolver user, ObjectMapper mapper) {
        return new LedgerManagementPrepareTool(mode, books, actions, user, mapper);
    }

    private DomainTool commit(LedgerManagementToolMode mode, LedgerBookService books, PendingActionService actions,
                              CurrentUserResolver user, ObjectMapper mapper) {
        return new LedgerManagementCommitTool(mode, books, actions, user, mapper);
    }
}
