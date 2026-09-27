package com.salarytracker.ai.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/agent/approvals", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("isAuthenticated()")
@Tag(name = "Agent Approvals")
public class AgentApprovalController {
    private final AgentApprovalService approvals;
    private final ObjectMapper mapper;

    public AgentApprovalController(AgentApprovalService approvals, ObjectMapper mapper) {
        this.approvals = approvals;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(operationId = "listAgentApprovals")
    public ApiResponse<List<ApprovalView>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(approvals.list(status).stream().map(this::view).toList());
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getAgentApproval")
    public ApiResponse<ApprovalView> get(@PathVariable String id) {
        return ApiResponse.ok(view(approvals.get(id)));
    }

    @PostMapping("/{id}/approve")
    @Operation(operationId = "approveAgentApproval")
    public ApiResponse<ApprovalView> approve(@PathVariable String id) {
        return ApiResponse.ok(view(approvals.approveAndExecute(id)));
    }

    @PostMapping("/{id}/reject")
    @Operation(operationId = "rejectAgentApproval")
    public ApiResponse<ApprovalView> reject(@PathVariable String id) {
        return ApiResponse.ok(view(approvals.reject(id)));
    }

    private ApprovalView view(AgentApproval approval) {
        return new ApprovalView(approval.id(), approval.actionId(), approval.toolName(), approval.bookId(),
                approval.status().name(), approval.summary(), tree(approval.payloadJson()), tree(approval.resultJson()),
                approval.expiresAt().toString(), approval.createdAt().toString(),
                approval.updatedAt().toString());
    }

    private JsonNode tree(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return mapper.readTree(value);
        } catch (Exception exception) {
            return mapper.createObjectNode().put("message", "审批数据无法解析");
        }
    }

    public record ApprovalView(String id, String actionId, String toolName, String bookId, String status,
                               String summary, JsonNode payload, JsonNode result, String expiresAt,
                               String createdAt, String updatedAt) {
    }
}
