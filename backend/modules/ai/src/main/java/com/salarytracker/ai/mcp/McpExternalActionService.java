package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.DomainToolRegistry;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
import com.salarytracker.ai.tool.ToolRisk;
import com.salarytracker.ai.tool.ToolStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
public class McpExternalActionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final PendingActionService actions;
    private final SecureRandom random = new SecureRandom();

    public McpExternalActionService(JdbcTemplate jdbc, ObjectMapper mapper, PendingActionService actions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.actions = actions;
    }

    @Transactional
    public Binding bind(McpPersonalTokenService.AuthenticatedToken token, ToolDefinition definition,
                        ToolResult result, String clientName, String baseUrl) {
        if (result.actionId() == null || result.actionId().isBlank()) return new Binding(null, null);
        String rawConfirmation = result.status() == com.salarytracker.ai.tool.ToolStatus.NEEDS_CONFIRMATION
                ? randomToken() : null;
        Instant confirmationExpiresAt = rawConfirmation == null || result.expiresAt() == null
                ? null : Instant.parse(result.expiresAt());
        jdbc.update("""
                INSERT INTO mcp_external_action(action_id,user_id,token_id,token_name,client_name,tool_name,
                    risk_level,summary,structured_json,confirmation_token_hash,confirmation_expires_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?)
                """, result.actionId(), token.user().id(), token.id(), token.name(), cleanClient(clientName),
                definition.name(), definition.riskLevel().name(), truncate(result.summary(), 500),
                json(result.structuredContent()), rawConfirmation == null ? null : hash(rawConfirmation),
                confirmationExpiresAt == null ? null : Timestamp.from(confirmationExpiresAt));
        String confirmationUrl = rawConfirmation == null ? null
                : normalizeBaseUrl(baseUrl) + "/mcp/actions/confirm?token=" + rawConfirmation;
        return new Binding(result.actionId(), confirmationUrl);
    }

    public void requireActionBook(McpPersonalTokenService.AuthenticatedToken token, String actionId,
                                  McpPersonalTokenService tokens) {
        PendingAction action = actions.getForUser(actionId, token.user().id());
        String bookId = parse(action.inputSnapshot()).path("bookId").asText(null);
        try {
            tokens.requireBook(token, bookId);
        } catch (SecurityException exception) {
            if (!action.isTerminal()) actions.transition(actionId, token.user().id(), ActionStatus.CANCELLED);
            throw exception;
        }
    }

    public ExternalActionView getForToken(McpPersonalTokenService.AuthenticatedToken token, String actionId) {
        ExternalRow row = requiredForToken(token, actionId);
        return view(row, actions.getForUser(actionId, token.user().id()));
    }

    public CommitResult commitForToken(McpPersonalTokenService.AuthenticatedToken token, String actionId,
                                       DomainToolRegistry registry, McpPersonalTokenService tokens) {
        ExternalRow row = requiredForToken(token, actionId);
        PendingAction action = actions.getForUser(actionId, token.user().id());
        if (!action.toolName().endsWith(".prepare")) throw new IllegalArgumentException("action 工具不支持提交");
        ToolDefinition prepare = registry.definition(action.toolName());
        if (prepare.riskLevel() != ToolRisk.R2) throw new SecurityException("MCP 目前只允许提交 R2 低风险操作");
        requireCommitScope(token, action.toolName());
        if (action.toolName().startsWith("ledger.")) requireActionBook(token, actionId, tokens);

        CommitResult replay = replayedCommit(row, action);
        if (replay != null) return replay;
        if (action.status() != ActionStatus.APPROVED) throw new IllegalStateException("action 尚未在网站批准");

        String commitTool = action.toolName().substring(0,
                action.toolName().length() - ".prepare".length()) + ".commit";
        ToolDefinition commit = registry.definition(commitTool);
        if (commit.riskLevel() != ToolRisk.R2) throw new SecurityException("MCP 目前只允许提交 R2 低风险操作");
        int claimed = jdbc.update("""
                UPDATE mcp_external_action SET commit_status='EXECUTING',commit_started_at=CURRENT_TIMESTAMP(6)
                WHERE action_id=? AND token_id=? AND user_id=? AND commit_status IS NULL
                """, actionId, token.id(), token.user().id());
        if (claimed != 1) {
            ExternalRow latest = requiredForToken(token, actionId);
            PendingAction latestAction = actions.getForUser(actionId, token.user().id());
            CommitResult latestReplay = replayedCommit(latest, latestAction);
            if (latestReplay != null) return latestReplay;
            if ("EXECUTING".equals(latest.commitStatus()) && latestAction.status() == ActionStatus.APPROVED
                    && latest.commitStartedAt() != null
                    && latest.commitStartedAt().isBefore(Instant.now().minusSeconds(120))) {
                jdbc.update("""
                        UPDATE mcp_external_action SET commit_status=NULL,commit_started_at=NULL
                        WHERE action_id=? AND commit_status='EXECUTING' AND commit_started_at<?
                        """, actionId, Timestamp.from(Instant.now().minusSeconds(120)));
                return commitForToken(token, actionId, registry, tokens);
            }
            throw new IllegalStateException("action 正在提交，请稍后查询状态");
        }

        ToolResult result;
        try {
            result = registry.invoke(commitTool, mapper.createObjectNode().put("actionId", actionId));
        } catch (RuntimeException exception) {
            failActionIfNecessary(actionId, token.user().id());
            result = ToolResult.failed(exception.getMessage() == null ? "提交失败" : exception.getMessage(),
                    mapper.createObjectNode().put("actionId", actionId), actionId);
        }
        saveCommitResult(actionId, result);
        return new CommitResult(result.status(), result.summary(), result.structuredContent(), actionId,
                false, Instant.now());
    }

    public List<ExternalActionView> listForToken(McpPersonalTokenService.AuthenticatedToken token, int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 50));
        return jdbc.query("""
                SELECT e.*, p.input_json FROM mcp_external_action e
                JOIN agent_pending_action p ON BINARY p.id=BINARY e.action_id
                WHERE e.token_id=? AND e.user_id=? ORDER BY e.created_at DESC LIMIT ?
                """, (result, rowNum) -> {
            ExternalRow row = map(result);
            return view(row, actions.getForUser(row.actionId(), token.user().id()));
        }, token.id(), token.user().id(), effectiveLimit);
    }

    @Transactional
    public ExternalActionView cancelForToken(McpPersonalTokenService.AuthenticatedToken token, String actionId) {
        ExternalRow row = requiredForToken(token, actionId);
        PendingAction action = actions.getForUser(actionId, token.user().id());
        if (!action.isTerminal()) action = actions.transition(actionId, token.user().id(), ActionStatus.CANCELLED);
        jdbc.update("UPDATE mcp_external_action SET rejected_at=COALESCE(rejected_at,CURRENT_TIMESTAMP(6)) WHERE action_id=?",
                actionId);
        return view(row, action);
    }

    public ExternalActionView getForConfirmation(String rawToken, long userId) {
        ExternalRow row = requiredForConfirmation(rawToken, userId);
        return view(row, actions.getForUser(row.actionId(), userId));
    }

    @Transactional
    public ExternalActionView approveConfirmation(String rawToken, long userId) {
        ExternalRow row = requiredForConfirmation(rawToken, userId);
        PendingAction action = actions.getForUser(row.actionId(), userId);
        if (action.status() == ActionStatus.APPROVED) return view(row, action);
        if (action.status() != ActionStatus.WAITING_CONFIRMATION) {
            throw new IllegalStateException("该操作当前不可批准");
        }
        action = actions.approve(row.actionId(), userId);
        jdbc.update("UPDATE mcp_external_action SET approved_at=CURRENT_TIMESTAMP(6) WHERE action_id=? AND approved_at IS NULL",
                row.actionId());
        return view(row, action);
    }

    @Transactional
    public ExternalActionView rejectConfirmation(String rawToken, long userId) {
        ExternalRow row = requiredForConfirmation(rawToken, userId);
        PendingAction action = actions.getForUser(row.actionId(), userId);
        if (action.status() == ActionStatus.DENIED || action.status() == ActionStatus.CANCELLED) return view(row, action);
        action = actions.reject(row.actionId(), userId);
        jdbc.update("UPDATE mcp_external_action SET rejected_at=CURRENT_TIMESTAMP(6) WHERE action_id=? AND rejected_at IS NULL",
                row.actionId());
        return view(row, action);
    }

    private ExternalRow requiredForToken(McpPersonalTokenService.AuthenticatedToken token, String actionId) {
        return jdbc.query("""
                SELECT e.*, p.input_json FROM mcp_external_action e
                JOIN agent_pending_action p ON BINARY p.id=BINARY e.action_id
                WHERE e.action_id=? AND e.token_id=? AND e.user_id=?
                """, (result, rowNum) -> map(result), actionId, token.id(), token.user().id()).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("外部 action 不存在"));
    }

    private ExternalRow requiredForConfirmation(String rawToken, long userId) {
        if (rawToken == null || rawToken.isBlank()) throw new IllegalArgumentException("确认令牌缺失");
        return jdbc.query("""
                SELECT e.*, p.input_json FROM mcp_external_action e
                JOIN agent_pending_action p ON BINARY p.id=BINARY e.action_id
                JOIN mcp_personal_token t ON BINARY t.id=BINARY e.token_id
                WHERE e.confirmation_token_hash=? AND e.user_id=?
                  AND e.confirmation_expires_at>CURRENT_TIMESTAMP
                  AND t.revoked_at IS NULL AND (t.expires_at IS NULL OR t.expires_at>CURRENT_TIMESTAMP)
                """, (result, rowNum) -> map(result), hash(rawToken.trim()), userId).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("确认链接无效、已过期或所属 Token 已撤销"));
    }

    private ExternalActionView view(ExternalRow row, PendingAction action) {
        return new ExternalActionView(action.id(), row.toolName(), row.riskLevel(), action.status().name(),
                row.summary(), parse(row.structuredJson()), parse(row.inputJson()), row.tokenName(), row.clientName(),
                action.expiresAt(), row.createdAt(), row.approvedAt(), row.rejectedAt(), row.commitStatus(),
                row.commitSummary(), parse(row.commitResultJson()), row.commitStartedAt(), row.committedAt());
    }

    private ExternalRow map(ResultSet result) throws SQLException {
        return new ExternalRow(result.getString("action_id"), result.getString("tool_name"),
                result.getString("risk_level"), result.getString("summary"), result.getString("structured_json"),
                result.getString("input_json"), result.getString("token_name"), result.getString("client_name"),
                instant(result, "created_at"), instant(result, "approved_at"), instant(result, "rejected_at"),
                result.getString("commit_status"), result.getString("commit_summary"),
                result.getString("commit_result_json"), instant(result, "commit_started_at"),
                instant(result, "committed_at"));
    }

    private void requireCommitScope(McpPersonalTokenService.AuthenticatedToken token, String toolName) {
        String required = toolName.startsWith("ledger.")
                ? McpPersonalTokenService.LEDGER_COMMIT : McpPersonalTokenService.WORKTIME_COMMIT;
        if (!token.scopes().contains(required)) throw new SecurityException("Token 缺少工具所需 commit scope");
    }

    private CommitResult replayedCommit(ExternalRow row, PendingAction action) {
        if (row.commitStatus() == null) return null;
        if ("EXECUTING".equals(row.commitStatus()) && !action.isTerminal()) return null;
        ToolStatus status;
        try {
            status = "EXECUTING".equals(row.commitStatus())
                    ? toolStatus(action.status()) : ToolStatus.valueOf(row.commitStatus());
        } catch (IllegalArgumentException exception) {
            status = ToolStatus.FAILED;
        }
        String summary = row.commitSummary() == null || row.commitSummary().isBlank()
                ? terminalSummary(action.status()) : row.commitSummary();
        JsonNode content = parse(row.commitResultJson());
        if (content.isEmpty()) content = mapper.createObjectNode().put("actionId", action.id());
        if ("EXECUTING".equals(row.commitStatus()) && action.isTerminal()) {
            ToolResult recovered = new ToolResult(status, summary, content, action.id(), null, null, null);
            saveCommitResult(action.id(), recovered);
        }
        return new CommitResult(status, summary, content, action.id(), true,
                row.committedAt() == null ? action.updatedAt() : row.committedAt());
    }

    private ToolStatus toolStatus(ActionStatus status) {
        return switch (status) {
            case COMPLETED -> ToolStatus.COMPLETED;
            case CONFLICT -> ToolStatus.CONFLICT;
            case DENIED -> ToolStatus.DENIED;
            default -> ToolStatus.FAILED;
        };
    }

    private String terminalSummary(ActionStatus status) {
        return switch (status) {
            case COMPLETED -> "操作已完成";
            case CONFLICT -> "操作因数据冲突未完成";
            case DENIED -> "操作已拒绝";
            default -> "操作未完成";
        };
    }

    private void saveCommitResult(String actionId, ToolResult result) {
        jdbc.update("""
                UPDATE mcp_external_action
                SET commit_status=?,commit_summary=?,commit_result_json=?,committed_at=CURRENT_TIMESTAMP(6)
                WHERE action_id=?
                """, result.status().name(), truncate(result.summary(), 500), json(result.structuredContent()), actionId);
    }

    private void failActionIfNecessary(String actionId, long userId) {
        try {
            PendingAction current = actions.getForUser(actionId, userId);
            if (current.status() == ActionStatus.APPROVED) current = actions.beginCommit(actionId, userId);
            if (current.status() == ActionStatus.EXECUTING) {
                actions.transition(actionId, userId, ActionStatus.FAILED);
            }
        } catch (RuntimeException ignored) {
            // 保留原始失败结果；状态竞争由 action 查询展示真实终态。
        }
    }

    private JsonNode parse(String value) {
        try { return value == null || value.isBlank() ? mapper.createObjectNode() : mapper.readTree(value); }
        catch (Exception exception) { throw new IllegalStateException("外部 action 数据损坏", exception); }
    }

    private String json(JsonNode value) {
        try { return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value); }
        catch (Exception exception) { throw new IllegalStateException("无法保存外部 action", exception); }
    }

    private String hash(String raw) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("无法计算确认令牌摘要", exception); }
    }

    private String randomToken() {
        byte[] bytes = new byte[36];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeBaseUrl(String value) {
        String base = value == null || value.isBlank() ? "" : value.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }

    private String cleanClient(String value) {
        String client = value == null ? null : value.trim();
        return client == null || client.isBlank() ? null : truncate(client, 160);
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record Binding(String actionId, String confirmationUrl) { }
    public record ExternalActionView(String actionId, String toolName, String riskLevel, String status,
                                     String summary, JsonNode structuredContent, JsonNode input,
                                     String tokenName, String clientName, Instant expiresAt, Instant createdAt,
                                     Instant approvedAt, Instant rejectedAt, String commitStatus,
                                     String commitSummary, JsonNode commitResult, Instant commitStartedAt,
                                     Instant committedAt) { }
    public record CommitResult(ToolStatus status, String summary, JsonNode structuredContent,
                               String actionId, boolean replayed, Instant committedAt) { }
    private record ExternalRow(String actionId, String toolName, String riskLevel, String summary,
                               String structuredJson, String inputJson, String tokenName, String clientName,
                               Instant createdAt, Instant approvedAt, Instant rejectedAt, String commitStatus,
                               String commitSummary, String commitResultJson, Instant commitStartedAt,
                               Instant committedAt) { }
}
