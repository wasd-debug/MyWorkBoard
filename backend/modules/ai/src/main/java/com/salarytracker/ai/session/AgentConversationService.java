package com.salarytracker.ai.session;

import com.salarytracker.identity.CurrentUserResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;
import java.time.Instant;

@Service
public class AgentConversationService {
    static final int CONTEXT_MESSAGE_LIMIT = 20;
    private static final int MAX_MESSAGE_LENGTH = 8_000;
    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final AgentSessionRepository repository;
    private final CurrentUserResolver currentUser;

    public AgentConversationService(AgentSessionRepository repository, CurrentUserResolver currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional
    public SessionContext open(String requestedSessionId, String firstMessage) {
        long userId = currentUser.id();
        String sessionId = normalizeSessionId(requestedSessionId);
        OptionalLong owner = repository.findOwner(sessionId);
        if (owner.isPresent() && owner.getAsLong() != userId) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (owner.isEmpty()) {
            repository.create(sessionId, userId, title(firstMessage));
        }
        return new SessionContext(sessionId, userId,
                repository.recentMessages(sessionId, userId, CONTEXT_MESSAGE_LIMIT));
    }

    @Transactional
    public void complete(SessionContext context, String userMessage, String assistantMessage) {
        repository.appendExchange(context.sessionId(), context.userId(),
                validateMessage(userMessage), validateMessage(assistantMessage));
    }

    @Transactional
    public void complete(SessionContext context, String turnId, String userMessage, String assistantMessage,
                         String metadataJson) {
        repository.appendExchange(context.sessionId(), context.userId(), turnId,
                validateMessage(userMessage), validateMessage(assistantMessage), metadataJson);
    }

    public SessionSummary create(String requestedId, String title) {
        long userId = currentUser.id();
        String sessionId = normalizeSessionId(requestedId);
        if (repository.findOwner(sessionId).isPresent()) throw new IllegalArgumentException("会话已存在");
        repository.create(sessionId, userId, title(title));
        return repository.findSession(sessionId, userId).orElseThrow();
    }

    public List<SessionSummary> list(boolean archived) {
        return repository.listSessions(currentUser.id(), archived);
    }

    public List<MessageView> messages(String sessionId) {
        requireOwned(sessionId, currentUser.id());
        return repository.messages(sessionId, currentUser.id());
    }

    public SessionSummary rename(String sessionId, String title) {
        long userId = currentUser.id();
        requireOwned(sessionId, userId);
        repository.rename(sessionId, userId, title(title));
        return repository.findSession(sessionId, userId).orElseThrow();
    }

    public void archive(String sessionId) {
        long userId = currentUser.id();
        requireOwned(sessionId, userId);
        repository.archive(sessionId, userId);
    }

    public void delete(String sessionId) {
        long userId = currentUser.id();
        requireOwned(sessionId, userId);
        repository.delete(sessionId, userId);
    }

    public SessionContext openOwned(long userId, String requestedSessionId, String message) {
        String sessionId = normalizeSessionId(requestedSessionId);
        validateMessage(message);
        requireOwned(sessionId, userId);
        return new SessionContext(sessionId, userId,
                repository.recentMessages(sessionId, userId, CONTEXT_MESSAGE_LIMIT));
    }

    private void requireOwned(String sessionId, long userId) {
        OptionalLong owner = repository.findOwner(normalizeSessionId(sessionId));
        if (owner.isEmpty() || owner.getAsLong() != userId) throw new IllegalArgumentException("会话不存在");
    }

    private String normalizeSessionId(String requested) {
        if (requested == null || requested.isBlank()) return UUID.randomUUID().toString();
        String value = requested.trim();
        if (!SESSION_ID.matcher(value).matches()) throw new IllegalArgumentException("会话 ID 格式不正确");
        return value;
    }

    private String validateMessage(String message) {
        String value = message == null ? "" : message.trim();
        if (value.isBlank()) throw new IllegalArgumentException("消息不能为空");
        if (value.length() > MAX_MESSAGE_LENGTH) throw new IllegalArgumentException("消息不能超过 8000 个字符");
        return value;
    }

    private String title(String message) {
        String value = validateMessage(message).replaceAll("\\s+", " ");
        return value.substring(0, Math.min(value.length(), 80));
    }

    public record StoredMessage(String role, String content) {
    }

    public record SessionSummary(String id, String title, Instant createdAt, Instant updatedAt, Instant archivedAt) {
    }

    public record MessageView(long id, String turnId, String role, String content, String metadataJson,
                              Instant createdAt) {
    }

    public record SessionContext(String sessionId, long userId, List<StoredMessage> history) {
    }
}
