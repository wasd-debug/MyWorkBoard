package com.salarytracker.ai.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.AgentOrchestrator;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ai.LlmGateway;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.annotation.PreDestroy;

@Service
public class AgentTurnService {
    private final AgentTurnRepository turns;
    private final AgentConversationService conversations;
    private final AgentOrchestrator orchestrator;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper mapper;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "agent-turn");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Thread> running = new ConcurrentHashMap<>();

    public AgentTurnService(AgentTurnRepository turns, AgentConversationService conversations,
                            AgentOrchestrator orchestrator, CurrentUserResolver currentUser,
                            ObjectMapper mapper) {
        this.turns = turns;
        this.conversations = conversations;
        this.orchestrator = orchestrator;
        this.currentUser = currentUser;
        this.mapper = mapper;
    }

    public TurnStart start(String sessionId, String clientRequestId, String message, TurnEvents events) {
        long userId = currentUser.id();
        conversations.openOwned(userId, sessionId, message);
        String requestId = normalizeId(clientRequestId, "clientRequestId");
        String proposedTurnId = UUID.randomUUID().toString();
        AgentTurnRepository.AgentTurnView turn = turns.createOrFind(proposedTurnId, sessionId, userId,
                requestId, message == null ? "" : message.trim());
        events.received(turn.id(), turn.status(), !turn.id().equals(proposedTurnId));
        if (!turn.id().equals(proposedTurnId)) {
            events.replay(turn);
            events.close();
            return new TurnStart(turn.id(), turn.status(), true);
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        executor.submit(() -> execute(turn.id(), sessionId, userId, message, events, authentication));
        return new TurnStart(turn.id(), turn.status(), false);
    }

    private void execute(String turnId, String sessionId, long userId, String message, TurnEvents events,
                         Authentication authentication) {
        running.put(turnId, Thread.currentThread());
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            turns.markPlanning(turnId, userId);
            events.status(turnId, AgentTurnStatus.PLANNING);
            LlmGateway.ChatResponse response = orchestrator.chatStreamingForUser(sessionId, userId, message,
                    new AgentOrchestrator.StreamListener() {
                        @Override public void delta(String content) { events.delta(turnId, content); }
                        @Override public void toolStarted(String name) { events.toolStarted(turnId, name); }
                        @Override public void toolCompleted(LlmGateway.ToolExecution execution) {
                            events.toolCompleted(turnId, execution);
                        }
                    });
            String json = mapper.writeValueAsString(response);
            if (turns.complete(turnId, userId, response.content(), json)) {
                conversations.complete(conversations.openOwned(userId, sessionId, message), turnId,
                        message, response.content(), json);
                events.completed(turnId, response);
            }
        } catch (Exception exception) {
            if (turns.fail(turnId, userId, safeMessage(exception))) events.failed(turnId, safeMessage(exception));
        } finally {
            SecurityContextHolder.clearContext();
            running.remove(turnId);
            events.close();
        }
    }

    public AgentTurnRepository.AgentTurnView get(String turnId) {
        return turns.find(turnId, currentUser.id()).orElseThrow(() -> new IllegalArgumentException("turn 不存在"));
    }

    public AgentTurnRepository.AgentTurnView cancel(String turnId) {
        long userId = currentUser.id();
        AgentTurnRepository.AgentTurnView turn = turns.find(turnId, userId)
                .orElseThrow(() -> new IllegalArgumentException("turn 不存在"));
        if (turns.cancel(turnId, userId)) {
            Thread thread = running.get(turnId);
            if (thread != null) thread.interrupt();
        }
        return turns.find(turnId, userId).orElse(turn);
    }

    private String normalizeId(String value, String label) {
        if (value == null || value.isBlank() || value.length() > 64 || !value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(label + " 格式不正确");
        }
        return value;
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? "Agent 执行失败" : value;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public record TurnStart(String turnId, AgentTurnStatus status, boolean replayed) {
    }

    public interface TurnEvents {
        void received(String turnId, AgentTurnStatus status, boolean replayed);
        void status(String turnId, AgentTurnStatus status);
        void delta(String turnId, String content);
        void toolStarted(String turnId, String name);
        void toolCompleted(String turnId, LlmGateway.ToolExecution execution);
        void completed(String turnId, LlmGateway.ChatResponse response);
        void failed(String turnId, String message);
        void replay(AgentTurnRepository.AgentTurnView turn);
        void close();
    }
}
