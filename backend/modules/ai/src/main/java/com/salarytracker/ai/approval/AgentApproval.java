package com.salarytracker.ai.approval;

import java.time.Instant;

public record AgentApproval(
        String id,
        long userId,
        String actionId,
        String toolName,
        int toolVersion,
        String bookId,
        ApprovalStatus status,
        String summary,
        String payloadJson,
        String resultJson,
        Instant expiresAt,
        Instant approvedAt,
        Instant rejectedAt,
        Instant executedAt,
        Instant createdAt,
        Instant updatedAt) {
}
