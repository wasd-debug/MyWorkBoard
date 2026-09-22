package com.salarytracker.ai.action;

import com.salarytracker.ai.tool.ToolRisk;
import org.springframework.stereotype.Component;

@Component
public class InteractionPolicy {
    public boolean requiresConfirmation(ToolRisk risk) {
        return risk == ToolRisk.R2 || risk == ToolRisk.R3 || risk == ToolRisk.R4;
    }

    public boolean requiresWebApproval(ToolRisk risk) {
        return risk == ToolRisk.R4;
    }
}
