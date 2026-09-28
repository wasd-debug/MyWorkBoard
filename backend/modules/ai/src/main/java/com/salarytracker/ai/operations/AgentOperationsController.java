package com.salarytracker.ai.operations;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent/operations")
@PreAuthorize("isAuthenticated()")
@Tag(name = "Agent Operations")
public class AgentOperationsController {
    private final AgentOperationsService service;
    public AgentOperationsController(AgentOperationsService service) { this.service = service; }
    @GetMapping("/metrics") @Operation(operationId = "getAgentOperationMetrics")
    public ApiResponse<AgentOperationsService.Metrics> metrics(@RequestParam(defaultValue = "TODAY") String preset,
                                                                 @RequestParam(defaultValue = "AUTO") String granularity,
                                                                 @RequestParam(required = false) String from,
                                                                 @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.metrics(preset, granularity, from, to));
    }
    @GetMapping("/budget") @Operation(operationId = "getAgentUsageBudget")
    public ApiResponse<AgentOperationsService.Budget> budget() { return ApiResponse.ok(service.budget()); }
    @PutMapping("/budget") @Operation(operationId = "saveAgentUsageBudget")
    public ApiResponse<AgentOperationsService.Budget> budget(@RequestBody AgentOperationsService.BudgetCommand command) { return ApiResponse.ok(service.saveBudget(command)); }
}
