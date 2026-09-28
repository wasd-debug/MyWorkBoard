package com.salarytracker.ai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolDefinition;
import com.salarytracker.ai.tool.ToolResult;
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
                action.expiresAt(), row.createdAt(), row.approvedAt(), row.rejectedAt());
    }

    private ExternalRow map(ResultSet result) throws SQLException {
        return new ExternalRow(result.getString("action_id"), result.getString("tool_name"),
                result.getString("risk_level"), result.getString("summary"), result.getString("structured_json"),
                result.getString("input_json"), result.getString("token_name"), result.getString("client_name"),
                instant(result, "created_at"), instant(result, "approved_at"), instant(result, "rejected_at"));
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
                                     Instant approvedAt, Instant rejectedAt) { }
    private record ExternalRow(String actionId, String toolName, String riskLevel, String summary,
                               String structuredJson, String inputJson, String tokenName, String clientName,
                               Instant createdAt, Instant approvedAt, Instant rejectedAt) { }
}
