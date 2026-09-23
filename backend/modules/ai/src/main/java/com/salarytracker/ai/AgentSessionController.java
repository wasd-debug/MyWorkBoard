package com.salarytracker.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.session.AgentConversationService;
import com.salarytracker.ai.session.AgentTurnRepository;
import com.salarytracker.ai.session.AgentTurnService;
import com.salarytracker.ai.model.AiModelConnectionService;
import com.salarytracker.ai.trace.AgentTraceService;
import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.ai.LlmGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/api/v1/agent", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Agent Sessions")
@PreAuthorize("isAuthenticated()")
public class AgentSessionController {
    private final AgentConversationService conversations;
    private final AgentTurnService turns;
    private final ObjectMapper mapper;
    private final AiModelConnectionService models;
    private final AgentTraceService traces;

    public AgentSessionController(AgentConversationService conversations, AgentTurnService turns,
                                  ObjectMapper mapper, AiModelConnectionService models,
                                  AgentTraceService traces) {
        this.conversations = conversations;
        this.turns = turns;
        this.mapper = mapper;
        this.models = models;
        this.traces = traces;
    }

    @PostMapping(value = "/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createAgentSession")
    public ApiResponse<AgentConversationService.SessionSummary> create(@RequestBody SessionCommand command) {
        AgentConversationService.SessionSummary session = conversations.create(command == null ? null : command.id(),
                command == null ? "新对话" : command.title());
        if (command != null && command.modelConnectionId() != null) {
            String sessionId = session.id();
            models.bindSession(sessionId, command.modelConnectionId());
            session = conversations.list(false).stream().filter(item -> item.id().equals(sessionId))
                    .findFirst().orElse(session);
        }
        return ApiResponse.ok(session);
    }

    @GetMapping("/sessions")
    @Operation(operationId = "listAgentSessions")
    public ApiResponse<List<AgentConversationService.SessionSummary>> list(
            @RequestParam(defaultValue = "false") boolean archived) {
        return ApiResponse.ok(conversations.list(archived));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(operationId = "listAgentMessages")
    public ApiResponse<List<AgentConversationService.MessageView>> messages(@PathVariable String sessionId) {
        return ApiResponse.ok(conversations.messages(sessionId));
    }

    @PatchMapping(value = "/sessions/{sessionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateAgentSession")
    public ApiResponse<AgentConversationService.SessionSummary> update(@PathVariable String sessionId,
                                                                       @RequestBody SessionCommand command) {
        if (command != null && command.pinned() != null) {
            return ApiResponse.ok(conversations.setPinned(sessionId, command.pinned()));
        }
        return ApiResponse.ok(conversations.rename(sessionId, command == null ? "新对话" : command.title()));
    }

    @PatchMapping(value = "/sessions/{sessionId}/group", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "moveAgentSessionToGroup")
    public ApiResponse<AgentConversationService.SessionSummary> moveToGroup(@PathVariable String sessionId,
                                                                            @RequestBody MoveGroupCommand command) {
        return ApiResponse.ok(conversations.moveToGroup(sessionId, command == null ? null : command.groupId()));
    }

    @GetMapping("/session-groups")
    @Operation(operationId = "listAgentSessionGroups")
    public ApiResponse<List<AgentConversationService.SessionGroup>> groups() {
        return ApiResponse.ok(conversations.groups());
    }

    @PostMapping(value = "/session-groups", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createAgentSessionGroup")
    public ApiResponse<AgentConversationService.SessionGroup> createGroup(@RequestBody GroupCommand command) {
        return ApiResponse.ok(conversations.createGroup(command == null ? null : command.id(),
                command == null ? null : command.name()));
    }

    @PatchMapping(value = "/session-groups/{groupId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "renameAgentSessionGroup")
    public ApiResponse<AgentConversationService.SessionGroup> renameGroup(@PathVariable String groupId,
                                                                          @RequestBody GroupCommand command) {
        return ApiResponse.ok(conversations.renameGroup(groupId, command == null ? null : command.name()));
    }

    @DeleteMapping("/session-groups/{groupId}")
    @Operation(operationId = "deleteAgentSessionGroup")
    public ApiResponse<Map<String, Boolean>> deleteGroup(@PathVariable String groupId) {
        conversations.deleteGroup(groupId);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping("/sessions/{sessionId}/archive")
    @Operation(operationId = "archiveAgentSession")
    public ApiResponse<Map<String, Boolean>> archive(@PathVariable String sessionId) {
        conversations.archive(sessionId);
        return ApiResponse.ok(Map.of("archived", true));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(operationId = "deleteAgentSession")
    public ApiResponse<Map<String, Boolean>> delete(@PathVariable String sessionId) {
        conversations.delete(sessionId);
        return ApiResponse.ok(Map.of("deleted", true));
    }

    @PostMapping(value = "/sessions/{sessionId}/turns", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "streamAgentTurn")
    public SseEmitter stream(@PathVariable String sessionId, @RequestBody TurnCommand command) {
        SseEmitter emitter = new SseEmitter(70_000L);
        SseTurnEvents events = new SseTurnEvents(emitter, mapper);
        try {
            turns.start(sessionId,
                    command == null ? null : command.clientRequestId(),
                    command == null ? "" : command.message(), events);
        } catch (Exception exception) {
            events.failed("", exception.getMessage() == null ? "无法创建 turn" : exception.getMessage());
            events.close();
        }
        return emitter;
    }

    @GetMapping("/turns/{turnId}")
    @Operation(operationId = "getAgentTurn")
    public ApiResponse<AgentTurnRepository.AgentTurnView> turn(@PathVariable String turnId) {
        return ApiResponse.ok(turns.get(turnId));
    }

    @GetMapping("/turns/{turnId}/trace")
    @Operation(operationId = "getAgentTurnTrace")
    public ApiResponse<AgentTraceService.TraceView> trace(@PathVariable String turnId) {
        return ApiResponse.ok(traces.get(turnId));
    }

    @PatchMapping(value = "/sessions/{sessionId}/model", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "changeAgentSessionModel")
    public ApiResponse<AgentConversationService.SessionSummary> changeModel(@PathVariable String sessionId,
                                                                            @RequestBody ModelCommand command) {
        if (!turns.queue(sessionId).items().isEmpty()) throw new IllegalStateException("队列非空时不能切换模型");
        models.bindSession(sessionId, command == null ? null : command.modelConnectionId());
        return ApiResponse.ok(conversations.list(false).stream().filter(item -> item.id().equals(sessionId))
                .findFirst().orElseThrow());
    }

    @PostMapping("/turns/{turnId}/cancel")
    @Operation(operationId = "cancelAgentTurn")
    public ApiResponse<AgentTurnRepository.AgentTurnView> cancel(@PathVariable String turnId) {
        return ApiResponse.ok(turns.cancel(turnId));
    }

    @GetMapping("/sessions/{sessionId}/queue")
    @Operation(operationId = "listAgentQueue")
    public ApiResponse<AgentTurnRepository.QueueView> queue(@PathVariable String sessionId) {
        return ApiResponse.ok(turns.queue(sessionId));
    }

    @PostMapping(value = "/sessions/{sessionId}/queue", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "enqueueAgentTurn")
    public ApiResponse<AgentTurnRepository.AgentTurnView> enqueue(@PathVariable String sessionId,
                                                                  @RequestBody TurnCommand command) {
        return ApiResponse.ok(turns.enqueue(sessionId, command == null ? null : command.clientRequestId(),
                command == null ? "" : command.message()));
    }

    @PutMapping(value = "/sessions/{sessionId}/queue/order", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "reorderAgentQueue")
    public ApiResponse<AgentTurnRepository.QueueView> reorder(@PathVariable String sessionId,
                                                               @RequestBody QueueOrderCommand command) {
        return ApiResponse.ok(turns.reorder(sessionId, command == null ? -1 : command.revision(),
                command == null ? List.of() : command.turnIds()));
    }

    @DeleteMapping("/queue/{turnId}")
    @Operation(operationId = "removeQueuedAgentTurn")
    public ApiResponse<AgentTurnRepository.AgentTurnView> removeQueued(@PathVariable String turnId) {
        return ApiResponse.ok(turns.removeQueued(turnId));
    }

    @PostMapping(value = "/turns/{turnId}/retry", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "retryAgentTurn")
    public ApiResponse<AgentTurnRepository.AgentTurnView> retry(@PathVariable String turnId,
                                                                @RequestBody RetryCommand command) {
        return ApiResponse.ok(turns.retry(turnId, command == null ? null : command.clientRequestId()));
    }

    public record SessionCommand(String id, String title, Boolean pinned, String modelConnectionId) {
    }

    public record GroupCommand(String id, String name) {
    }

    public record MoveGroupCommand(String groupId) {
    }

    public record TurnCommand(String clientRequestId, String message) {
    }

    public record QueueOrderCommand(long revision, List<String> turnIds) {
    }

    public record RetryCommand(String clientRequestId) {
    }

    public record ModelCommand(String modelConnectionId) { }

    private static final class SseTurnEvents implements AgentTurnService.TurnEvents {
        private final SseEmitter emitter;
        private final ObjectMapper mapper;

        private SseTurnEvents(SseEmitter emitter, ObjectMapper mapper) {
            this.emitter = emitter;
            this.mapper = mapper;
        }

        @Override public void received(String turnId, com.salarytracker.ai.session.AgentTurnStatus status,
                                       boolean replayed) {
            send("turn.received", Map.of("turnId", turnId, "status", status.name(), "replayed", replayed));
        }
        @Override public void status(String turnId, com.salarytracker.ai.session.AgentTurnStatus status) {
            send("turn.status", Map.of("turnId", turnId, "status", status.name()));
        }
        @Override public void delta(String turnId, String content) {
            send("assistant.delta", Map.of("turnId", turnId, "content", content));
        }
        @Override public void toolStarted(String turnId, String name) {
            send("tool.started", Map.of("turnId", turnId, "name", name));
        }
        @Override public void toolCompleted(String turnId, LlmGateway.ToolExecution execution) {
            send("tool.completed", Map.of("turnId", turnId, "execution", execution));
        }
        @Override public void inputRequired(String turnId, com.salarytracker.ai.tool.ToolResult result) {
            send("input.required", Map.of("turnId", turnId, "action", result));
        }
        @Override public void confirmationRequired(String turnId, com.salarytracker.ai.tool.ToolResult result) {
            send("confirmation.required", Map.of("turnId", turnId, "action", result));
        }
        @Override public void completed(String turnId, LlmGateway.ChatResponse response) {
            send("turn.completed", Map.of("turnId", turnId, "response", response));
        }
        @Override public void failed(String turnId, String message) {
            send("turn.failed", Map.of("turnId", turnId, "message", message));
        }
        @Override public void cancelled(String turnId) {
            send("turn.cancelled", Map.of("turnId", turnId, "status", "CANCELLED"));
        }
        @Override public void replay(AgentTurnRepository.AgentTurnView turn) {
            if (turn.status() == com.salarytracker.ai.session.AgentTurnStatus.COMPLETED && turn.responseJson() != null) {
                try {
                    send("turn.completed", Map.of("turnId", turn.id(), "response",
                            mapper.readTree(turn.responseJson()), "replayed", true));
                } catch (Exception exception) {
                    failed(turn.id(), "无法恢复 turn 结果");
                }
            } else if (turn.status() == com.salarytracker.ai.session.AgentTurnStatus.FAILED) {
                failed(turn.id(), turn.errorMessage());
            } else if (turn.status() == com.salarytracker.ai.session.AgentTurnStatus.CANCELLED) {
                cancelled(turn.id());
            } else {
                status(turn.id(), turn.status());
            }
        }
        @Override public void close() {
            try { emitter.complete(); } catch (Exception ignored) { }
        }
        private synchronized void send(String name, Object data) {
            try {
                emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ignored) {
                // The turn continues and can be recovered through GET /turns/{id}.
            }
        }
    }
}
