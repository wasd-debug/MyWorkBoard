package com.salarytracker.ai.action;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.tool.ToolDefinition;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 3A 的临时内存实现。接入 Web Agent 前替换为 agent_pending_action 持久化实现。
 */
@Service
public class PendingActionService {
    private final Map<String, PendingAction> actions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final InteractionPolicy policy;

    public PendingActionService(ObjectMapper mapper, InteractionPolicy policy) {
        this.mapper = mapper;
        this.policy = policy;
    }

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
        if (actions.putIfAbsent(action.id(), action) != null) {
            throw new IllegalStateException("actionId 已存在");
        }
        return action;
    }

    public PendingAction getForUser(String id, long userId) {
        PendingAction action = actions.get(id);
        if (action == null || action.userId() != userId) {
            throw new IllegalArgumentException("操作不存在");
        }
        if (action.expiresAt().isBefore(Instant.now()) && action.status() != ActionStatus.EXPIRED) {
            return transition(id, userId, ActionStatus.EXPIRED);
        }
        return action;
    }

    public PendingAction transition(String id, long userId, ActionStatus next) {
        return actions.compute(id, (key, current) -> {
            if (current == null || current.userId() != userId) throw new IllegalArgumentException("操作不存在");
            return current.transition(next, Instant.now());
        });
    }

    public PendingAction approve(String id, long userId) {
        return transition(id, userId, ActionStatus.APPROVED);
    }

    public PendingAction beginCommit(String id, long userId) {
        PendingAction result = actions.compute(id, (key, current) -> {
            if (current == null || current.userId() != userId) throw new IllegalArgumentException("操作不存在");
            Instant now = Instant.now();
            if (current.expiresAt().isBefore(now)) return current.transition(ActionStatus.EXPIRED, now);
            if (current.status() != ActionStatus.APPROVED) {
                throw new IllegalStateException("action 尚未批准或已开始提交");
            }
            return current.transition(ActionStatus.EXECUTING, now);
        });
        if (result.status() == ActionStatus.EXPIRED) throw new IllegalStateException("action 已过期");
        return result;
    }

    private String snapshot(JsonNode input) {
        try {
            return mapper.writeValueAsString(input == null ? mapper.createObjectNode() : input);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法保存 action 参数快照", exception);
        }
    }
}
