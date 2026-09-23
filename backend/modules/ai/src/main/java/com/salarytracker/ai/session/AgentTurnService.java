package com.salarytracker.ai.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.AgentOrchestrator;
import com.salarytracker.ai.model.AiModelConnectionService;
import com.salarytracker.ai.trace.AgentTraceService;
import com.salarytracker.identity.AuthService;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ai.LlmGateway;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class AgentTurnService {
    private static final TurnEvents NO_EVENTS = new TurnEvents() { };
    private final AgentTurnRepository turns;
    private final AgentConversationService conversations;
    private final AgentOrchestrator orchestrator;
    private final CurrentUserResolver currentUser;
    private final AuthService authService;
    private final ObjectMapper mapper;
    private final AiModelConnectionService models;
    private final AgentTraceService traces;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "agent-turn");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, TurnEvents> subscribers = new ConcurrentHashMap<>();
    private final Map<String, Thread> running = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> dispatching = new ConcurrentHashMap<>();

    public AgentTurnService(AgentTurnRepository turns, AgentConversationService conversations,
                            AgentOrchestrator orchestrator, CurrentUserResolver currentUser,
                            AuthService authService, ObjectMapper mapper, AiModelConnectionService models,
                            AgentTraceService traces) {
        this.turns = turns;
        this.conversations = conversations;
        this.orchestrator = orchestrator;
        this.currentUser = currentUser;
        this.authService = authService;
        this.mapper = mapper;
        this.models = models;
        this.traces = traces;
    }

    public TurnStart start(String sessionId, String clientRequestId, String message, TurnEvents events) {
        AgentTurnRepository.AgentTurnView turn = enqueue(sessionId, clientRequestId, message, null);
        boolean terminal = List.of(AgentTurnStatus.COMPLETED, AgentTurnStatus.FAILED,
                AgentTurnStatus.CANCELLED).contains(turn.status());
        events.received(turn.id(), turn.status(), terminal);
        if (terminal) {
            events.replay(turn);
            events.close();
            return new TurnStart(turn.id(), turn.status(), true);
        }
        subscribers.put(turn.id(), events);
        dispatch(sessionId, turn.userId());
        return new TurnStart(turn.id(), turn.status(), false);
    }

    public AgentTurnRepository.AgentTurnView enqueue(String sessionId, String clientRequestId, String message) {
        AgentTurnRepository.AgentTurnView turn = enqueue(sessionId, clientRequestId, message, null);
        dispatch(sessionId, currentUser.id());
        return turn;
    }

    private AgentTurnRepository.AgentTurnView enqueue(String sessionId, String clientRequestId, String message,
                                                       String retryOfTurnId) {
        long userId = currentUser.id();
        conversations.openOwned(userId, sessionId, message);
        String requestId = normalizeId(clientRequestId, "clientRequestId");
        String proposedTurnId = UUID.randomUUID().toString();
        return turns.enqueue(proposedTurnId, sessionId, userId, requestId,
                message == null ? "" : message.trim(), retryOfTurnId);
    }

    public AgentTurnRepository.QueueView queue(String sessionId) {
        long userId = currentUser.id();
        conversations.openOwned(userId, sessionId, "读取队列");
        return new AgentTurnRepository.QueueView(turns.queueRevision(sessionId, userId),
                turns.listQueue(sessionId, userId));
    }

    public AgentTurnRepository.QueueView reorder(String sessionId, long revision, List<String> turnIds) {
        long userId = currentUser.id();
        conversations.openOwned(userId, sessionId, "调整队列");
        long updated = turns.reorder(sessionId, userId, revision, turnIds == null ? List.of() : turnIds);
        return new AgentTurnRepository.QueueView(updated, turns.listQueue(sessionId, userId));
    }

    public AgentTurnRepository.AgentTurnView retry(String turnId, String clientRequestId) {
        long userId = currentUser.id();
        AgentTurnRepository.AgentTurnView original = turns.find(turnId, userId)
                .orElseThrow(() -> new IllegalArgumentException("turn 不存在"));
        if (original.status() != AgentTurnStatus.FAILED && original.status() != AgentTurnStatus.CANCELLED) {
            throw new IllegalArgumentException("只有失败或已取消的 turn 可以重试");
        }
        AgentTurnRepository.AgentTurnView retried = enqueue(original.sessionId(), clientRequestId,
                original.userMessage(), original.id());
        dispatch(original.sessionId(), userId);
        return retried;
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
            TurnEvents events = subscribers.remove(turnId);
            if (events != null) { events.cancelled(turnId); events.close(); }
            if (turn.status() != AgentTurnStatus.QUEUED) persistTerminalMessage(turn,
                    "本轮对话已取消。", "CANCELLED");
            dispatch(turn.sessionId(), userId);
        }
        return turns.find(turnId, userId).orElse(turn);
    }

    public AgentTurnRepository.AgentTurnView removeQueued(String turnId) {
        long userId = currentUser.id();
        AgentTurnRepository.AgentTurnView turn = turns.find(turnId, userId)
                .orElseThrow(() -> new IllegalArgumentException("turn 不存在"));
        if (!turns.cancelQueued(turnId, userId)) throw new IllegalArgumentException("只能移除排队中的 turn");
        return turns.find(turnId, userId).orElse(turn);
    }

    @PostConstruct
    void recoverInterruptedTurns() {
        turns.requeueInterrupted();
    }

    @Scheduled(fixedDelayString = "${app.agent.queue-recovery-ms:5000}")
    void recoverPendingQueues() {
        for (AgentTurnRepository.PendingQueue queue : turns.pendingQueues(20)) {
            dispatch(queue.sessionId(), queue.userId());
        }
    }

    private void dispatch(String sessionId, long userId) {
        String key = userId + ":" + sessionId;
        AtomicBoolean lock = dispatching.computeIfAbsent(key, ignored -> new AtomicBoolean());
        if (!lock.compareAndSet(false, true)) return;
        executor.submit(() -> {
            try {
                while (true) {
                    AgentTurnRepository.AgentTurnView next = turns.claimNext(sessionId, userId).orElse(null);
                    if (next == null) return;
                    execute(next, userId, subscribers.getOrDefault(next.id(), NO_EVENTS));
                }
            } finally {
                lock.set(false);
                dispatching.remove(key, lock);
                if (!turns.listQueue(sessionId, userId).isEmpty()) dispatch(sessionId, userId);
            }
        });
    }

    private void execute(AgentTurnRepository.AgentTurnView turn, long userId, TurnEvents events) {
        running.put(turn.id(), Thread.currentThread());
        try {
            CurrentUser user = authService.loadUser(userId);
            if (user == null) throw new IllegalStateException("用户不可用");
            var authorities = user.authorities().stream().map(SimpleGrantedAuthority::new).toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(user, null, authorities));
            turns.markPlanning(turn.id(), userId);
            events.status(turn.id(), AgentTurnStatus.PLANNING);
            AiModelConnectionService.RuntimeConnection model = models.resolveForTurn(turn.id(), turn.sessionId(), userId);
            try { traces.bindModel(turn, model); } catch (Exception ignored) { /* trace must not block replies */ }
            LlmGateway.ChatResponse response = orchestrator.chatStreamingForUser(turn.sessionId(), userId,
                    turn.userMessage(), new AgentOrchestrator.StreamListener() {
                        @Override public void delta(String content) { events.delta(turn.id(), content); }
                        @Override public void toolStarted(String name) { events.toolStarted(turn.id(), name); }
                        @Override public void toolCompleted(LlmGateway.ToolExecution execution) {
                            events.toolCompleted(turn.id(), execution);
                        }
                        @Override public void inputRequired(com.salarytracker.ai.tool.ToolResult result) {
                            events.inputRequired(turn.id(), result);
                        }
                        @Override public void confirmationRequired(com.salarytracker.ai.tool.ToolResult result) {
                            events.confirmationRequired(turn.id(), result);
                        }
                    }, model);
            String json = mapper.writeValueAsString(response);
            try { traces.persist(turn, model, response); } catch (Exception ignored) { /* trace must not block replies */ }
            if (turns.complete(turn.id(), userId, response.content(), json)) {
                conversations.complete(conversations.openOwned(userId, turn.sessionId(), turn.userMessage()),
                        turn.id(), turn.userMessage(), response.content(), json);
                events.completed(turn.id(), response);
            } else if (turns.find(turn.id(), userId).map(AgentTurnRepository.AgentTurnView::status)
                    .orElse(AgentTurnStatus.FAILED) == AgentTurnStatus.CANCELLED) {
                events.cancelled(turn.id());
            }
        } catch (Exception exception) {
            if (turns.fail(turn.id(), userId, safeMessage(exception))) {
                String error = safeMessage(exception);
                persistTerminalMessage(turn, error, "FAILED");
                events.failed(turn.id(), error);
            }
        } finally {
            SecurityContextHolder.clearContext();
            running.remove(turn.id());
            subscribers.remove(turn.id());
            events.close();
        }
    }

    private void persistTerminalMessage(AgentTurnRepository.AgentTurnView turn, String content, String status) {
        try {
            String metadata = mapper.writeValueAsString(Map.of("content", content, "status", status));
            conversations.complete(conversations.openOwned(turn.userId(), turn.sessionId(), turn.userMessage()),
                    turn.id(), turn.userMessage(), content, metadata);
        } catch (Exception ignored) {
            // The turn remains recoverable even if the presentation message cannot be stored.
        }
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

    @PreDestroy void shutdown() { executor.shutdownNow(); }

    public record TurnStart(String turnId, AgentTurnStatus status, boolean replayed) { }

    public interface TurnEvents {
        default void received(String turnId, AgentTurnStatus status, boolean replayed) { }
        default void status(String turnId, AgentTurnStatus status) { }
        default void delta(String turnId, String content) { }
        default void toolStarted(String turnId, String name) { }
        default void toolCompleted(String turnId, LlmGateway.ToolExecution execution) { }
        default void inputRequired(String turnId, com.salarytracker.ai.tool.ToolResult result) { }
        default void confirmationRequired(String turnId, com.salarytracker.ai.tool.ToolResult result) { }
        default void completed(String turnId, LlmGateway.ChatResponse response) { }
        default void failed(String turnId, String message) { }
        default void cancelled(String turnId) { }
        default void replay(AgentTurnRepository.AgentTurnView turn) { }
        default void close() { }
    }
}
