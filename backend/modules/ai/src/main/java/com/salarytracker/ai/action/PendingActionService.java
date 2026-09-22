package com.salarytracker.ai.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.ToolDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
@Service
public class PendingActionService {
    private final PendingActionRepository repository;
    private final ObjectMapper mapper;
    private final InteractionPolicy policy;

    public PendingActionService(PendingActionRepository repository, ObjectMapper mapper, InteractionPolicy policy) {
        this.repository = repository;
        this.mapper = mapper;
        this.policy = policy;
    }

    @Transactional
    public PendingAction prepare(long userId, ToolDefinition tool, JsonNode input,
                                 Long expectedRevision, boolean missingInput, Duration ttl) {
        if (!policy.requiresConfirmation(tool.riskLevel())) {
            throw new IllegalArgumentException("只有写工具可以创建 pending action");
        }
        Duration effectiveTtl = ttl == null ? Duration.ofMinutes(15) : ttl;
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            throw new IllegalArgumentException("action 有效期必须大于 0");
        }
        PendingAction action = PendingAction.create(userId, tool.name(), tool.version(), snapshot(input),
                expectedRevision, Instant.now().plus(effectiveTtl));
        create(action);
        transition(action.id(), userId, ActionStatus.PLANNING);
        return transition(action.id(), userId,
                missingInput ? ActionStatus.WAITING_INPUT : ActionStatus.WAITING_CONFIRMATION);
    }

    public PendingAction create(PendingAction action) {
        if (action.expiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("操作已过期");
        }
        repository.insert(action);
        return action;
    }

    @Transactional
    public PendingAction getForUser(String id, long userId) {
        PendingAction action = required(id, userId);
        if (action.expiresAt().isBefore(Instant.now()) && action.status() != ActionStatus.EXPIRED) {
            return transition(id, userId, ActionStatus.EXPIRED);
        }
        return action;
    }

    @Transactional
    public PendingAction transition(String id, long userId, ActionStatus next) {
        PendingAction current = required(id, userId);
        Instant now = Instant.now();
        PendingAction changed = current.transition(next, now);
        if (changed.status() == current.status()) return current;
        if (!repository.transition(id, userId, current.status(), changed.status(), now)) {
            throw new IllegalStateException("action 状态已变化，请刷新后重试");
        }
        return changed;
    }

    public PendingAction approve(String id, long userId) {
        return transition(id, userId, ActionStatus.APPROVED);
    }

    @Transactional
    public PendingAction reject(String id, long userId) {
        return transition(id, userId, ActionStatus.DENIED);
    }

    @Transactional
    public PendingAction beginCommit(String id, long userId) {
        PendingAction current = required(id, userId);
        Instant now = Instant.now();
        if (current.expiresAt().isBefore(now)) {
            transition(id, userId, ActionStatus.EXPIRED);
            throw new IllegalStateException("action 已过期");
        }
        if (current.status() != ActionStatus.APPROVED) {
            throw new IllegalStateException("action 尚未批准或已开始提交");
        }
        PendingAction executing = current.transition(ActionStatus.EXECUTING, now);
        if (!repository.transition(id, userId, ActionStatus.APPROVED, ActionStatus.EXECUTING, now)) {
            throw new IllegalStateException("action 尚未批准或已开始提交");
        }
        return executing;
    }

    private PendingAction required(String id, long userId) {
        return repository.find(id, userId).orElseThrow(() -> new IllegalArgumentException("操作不存在"));
    }

    private String snapshot(JsonNode input) {
        try {
            return mapper.writeValueAsString(input == null ? mapper.createObjectNode() : input);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法保存 action 参数快照", exception);
        }
    }
}
