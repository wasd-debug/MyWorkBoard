package com.salarytracker.ai.tool.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.approval.AgentApprovalService;
import com.salarytracker.ai.approval.LedgerMemberRoleApprovalExecutor;
import com.salarytracker.ai.tool.DomainTool;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.ledger.LedgerBookService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerMemberRoleToolsConfiguration {
    @Bean DomainTool ledgerMembersListTool(LedgerBookService b, ObjectMapper m) { return new LedgerMemberRoleListTool(true, b, m); }
    @Bean DomainTool ledgerRolesListTool(LedgerBookService b, ObjectMapper m) { return new LedgerMemberRoleListTool(false, b, m); }

    @Bean DomainTool ledgerMemberCreatePrepareTool(LedgerBookService b, PendingActionService a, AgentApprovalService p, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerMemberRoleToolMode.MEMBER_CREATE, b, a, p, u, m); }
    @Bean DomainTool ledgerMemberCreateCommitTool(LedgerMemberRoleApprovalExecutor e, ObjectMapper m) { return commit(LedgerMemberRoleToolMode.MEMBER_CREATE, e, m); }
    @Bean DomainTool ledgerMemberUpdatePrepareTool(LedgerBookService b, PendingActionService a, AgentApprovalService p, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerMemberRoleToolMode.MEMBER_UPDATE, b, a, p, u, m); }
    @Bean DomainTool ledgerMemberUpdateCommitTool(LedgerMemberRoleApprovalExecutor e, ObjectMapper m) { return commit(LedgerMemberRoleToolMode.MEMBER_UPDATE, e, m); }
    @Bean DomainTool ledgerMemberDeletePrepareTool(LedgerBookService b, PendingActionService a, AgentApprovalService p, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerMemberRoleToolMode.MEMBER_DELETE, b, a, p, u, m); }
    @Bean DomainTool ledgerMemberDeleteCommitTool(LedgerMemberRoleApprovalExecutor e, ObjectMapper m) { return commit(LedgerMemberRoleToolMode.MEMBER_DELETE, e, m); }
    @Bean DomainTool ledgerRoleCreatePrepareTool(LedgerBookService b, PendingActionService a, AgentApprovalService p, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerMemberRoleToolMode.ROLE_CREATE, b, a, p, u, m); }
    @Bean DomainTool ledgerRoleCreateCommitTool(LedgerMemberRoleApprovalExecutor e, ObjectMapper m) { return commit(LedgerMemberRoleToolMode.ROLE_CREATE, e, m); }
    @Bean DomainTool ledgerRoleUpdatePrepareTool(LedgerBookService b, PendingActionService a, AgentApprovalService p, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerMemberRoleToolMode.ROLE_UPDATE, b, a, p, u, m); }
    @Bean DomainTool ledgerRoleUpdateCommitTool(LedgerMemberRoleApprovalExecutor e, ObjectMapper m) { return commit(LedgerMemberRoleToolMode.ROLE_UPDATE, e, m); }
    @Bean DomainTool ledgerRoleDeletePrepareTool(LedgerBookService b, PendingActionService a, AgentApprovalService p, CurrentUserResolver u, ObjectMapper m) { return prepare(LedgerMemberRoleToolMode.ROLE_DELETE, b, a, p, u, m); }
    @Bean DomainTool ledgerRoleDeleteCommitTool(LedgerMemberRoleApprovalExecutor e, ObjectMapper m) { return commit(LedgerMemberRoleToolMode.ROLE_DELETE, e, m); }

    private DomainTool prepare(LedgerMemberRoleToolMode mode, LedgerBookService books,
                               PendingActionService actions, AgentApprovalService approvals,
                               CurrentUserResolver user, ObjectMapper mapper) {
        return new LedgerMemberRolePrepareTool(mode, books, actions, approvals, user, mapper);
    }

    private DomainTool commit(LedgerMemberRoleToolMode mode, LedgerMemberRoleApprovalExecutor executor,
                              ObjectMapper mapper) {
        return new LedgerMemberRoleCommitTool(mode, executor, mapper);
    }
}
