package com.salarytracker.ai.operations;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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
    @GetMapping("/alerts") @Operation(operationId = "listAgentBudgetAlerts")
    public ApiResponse<List<AgentOperationsService.BudgetAlert>> alerts() { return ApiResponse.ok(service.alerts()); }
    @PostMapping("/alerts/{id}/read") @Operation(operationId = "markAgentBudgetAlertRead")
    public ApiResponse<Void> read(@PathVariable long id) { service.markAlertRead(id); return ApiResponse.ok(null); }
    @GetMapping("/calls") @Operation(operationId = "listAgentOperationCalls")
    public ApiResponse<List<AgentOperationsService.CallDetail>> calls(@RequestParam(defaultValue = "TODAY") String preset,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "false") boolean failuresOnly, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.callsPage(preset, from, to, failuresOnly, page, pageSize));
    }
    @GetMapping("/calls/{turnId}") @Operation(operationId = "getAgentOperationCallTrace")
    public ApiResponse<AgentOperationsService.CallTrace> call(@PathVariable String turnId) {
        return ApiResponse.ok(service.callTrace(turnId));
    }
}
