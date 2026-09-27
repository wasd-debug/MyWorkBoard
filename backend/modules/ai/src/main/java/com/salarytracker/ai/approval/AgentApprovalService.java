package com.salarytracker.ai.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.identity.CurrentUserResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AgentApprovalService {
    private final AgentApprovalRepository repository;
    private final PendingActionService actions;
    private final LedgerImportApprovalExecutor importExecutor;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;

    public AgentApprovalService(AgentApprovalRepository repository, PendingActionService actions,
                                LedgerImportApprovalExecutor importExecutor, CurrentUserResolver currentUser,
                                ObjectMapper mapper) {
        this.repository = repository;
        this.actions = actions;
        this.importExecutor = importExecutor;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    @Transactional
    public AgentApproval create(PendingAction action, String bookId, String summary, JsonNode payload) {
        Instant now = Instant.now();
        AgentApproval approval = new AgentApproval(UUID.randomUUID().toString(), action.userId(), action.id(),
                action.toolName(), action.toolVersion(), bookId, ApprovalStatus.PENDING, summary, json(payload), null,
                action.expiresAt(), null, null, null, now, now);
        repository.insert(approval);
        return approval;
    }

    public List<AgentApproval> list(String status) {
        ApprovalStatus filter = status == null || status.isBlank() ? null : ApprovalStatus.valueOf(status.toUpperCase());
        return repository.list(currentUser.id(), filter).stream().map(this::expireIfNeeded).toList();
    }

    public AgentApproval get(String id) {
        return expireIfNeeded(required(id));
    }

    @Transactional
    public AgentApproval reject(String id) {
        long userId = currentUser.id();
        AgentApproval approval = expireIfNeeded(required(id));
        if (approval.status() == ApprovalStatus.REJECTED) return approval;
        if (approval.status() != ApprovalStatus.PENDING) throw new IllegalStateException("审批当前不可拒绝");
        Instant now = Instant.now();
        if (!repository.transition(id, userId, ApprovalStatus.PENDING, ApprovalStatus.REJECTED, null, now)) {
            throw new IllegalStateException("审批状态已变化，请刷新后重试");
        }
        actions.reject(approval.actionId(), userId);
        return required(id);
    }

    @Transactional
    public AgentApproval approveAndExecute(String id) {
        long userId = currentUser.id();
        AgentApproval approval = expireIfNeeded(required(id));
        if (approval.status() == ApprovalStatus.COMPLETED || approval.status() == ApprovalStatus.FAILED) return approval;
        if (approval.status() != ApprovalStatus.PENDING) throw new IllegalStateException("审批当前不可批准");
        Instant now = Instant.now();
        if (!repository.transition(id, userId, ApprovalStatus.PENDING, ApprovalStatus.APPROVED, null, now)) {
            throw new IllegalStateException("审批状态已变化，请刷新后重试");
        }
        actions.approve(approval.actionId(), userId);
        if (!repository.transition(id, userId, ApprovalStatus.APPROVED, ApprovalStatus.EXECUTING, null, Instant.now())) {
            throw new IllegalStateException("审批执行状态已变化");
        }
        ToolResult result = importExecutor.commit(approval.actionId());
        ApprovalStatus finalStatus = result.status().name().equals("COMPLETED")
                ? ApprovalStatus.COMPLETED : ApprovalStatus.FAILED;
        repository.transition(id, userId, ApprovalStatus.EXECUTING, finalStatus, json(mapper.valueToTree(result)), Instant.now());
        return required(id);
    }

    private AgentApproval expireIfNeeded(AgentApproval approval) {
        if (approval.status() == ApprovalStatus.PENDING && approval.expiresAt().isBefore(Instant.now())) {
            repository.transition(approval.id(), approval.userId(), ApprovalStatus.PENDING,
                    ApprovalStatus.EXPIRED, null, Instant.now());
            try {
                actions.transition(approval.actionId(), approval.userId(), com.salarytracker.ai.action.ActionStatus.EXPIRED);
            } catch (RuntimeException ignored) {
                // action may already be terminal; approval expiry remains authoritative for this request.
            }
            return required(approval.id());
        }
        return approval;
    }

    private AgentApproval required(String id) {
        return repository.find(id, currentUser.id()).orElseThrow(() -> new IllegalArgumentException("审批不存在"));
    }

    private String json(JsonNode value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法保存审批摘要", exception);
        }
    }
}
