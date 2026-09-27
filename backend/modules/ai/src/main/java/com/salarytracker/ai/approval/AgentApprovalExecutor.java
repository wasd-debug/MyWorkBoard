package com.salarytracker.ai.approval;

import com.salarytracker.ai.tool.ToolResult;

public interface AgentApprovalExecutor {
    boolean supports(String toolName);

    ToolResult commit(String actionId);
}
