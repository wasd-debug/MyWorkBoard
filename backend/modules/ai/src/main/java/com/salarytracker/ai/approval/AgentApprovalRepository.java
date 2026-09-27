package com.salarytracker.ai.approval;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AgentApprovalRepository {
    void insert(AgentApproval approval);

    Optional<AgentApproval> find(String id, long userId);

    List<AgentApproval> list(long userId, ApprovalStatus status);

    boolean transition(String id, long userId, ApprovalStatus expected, ApprovalStatus next,
                       String resultJson, Instant eventAt);
}
