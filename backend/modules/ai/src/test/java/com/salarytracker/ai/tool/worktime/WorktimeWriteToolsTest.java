package com.salarytracker.ai.tool.worktime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.ai.action.ActionStatus;
import com.salarytracker.ai.action.InteractionPolicy;
import com.salarytracker.ai.action.PendingAction;
import com.salarytracker.ai.action.PendingActionRepository;
import com.salarytracker.ai.action.PendingActionService;
import com.salarytracker.ai.tool.ToolStatus;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.worktime.WorktimeModels.RecordCommand;
import com.salarytracker.worktime.WorktimeModels.RecordPreview;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;
import com.salarytracker.worktime.WorktimeService;
import com.salarytracker.platform.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorktimeWriteToolsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WorktimeService worktime = mock(WorktimeService.class);
    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    private final MemoryRepository repository = new MemoryRepository();
    private final PendingActionService actions = new PendingActionService(repository, mapper, new InteractionPolicy());

    WorktimeWriteToolsTest() {
        when(currentUser.id()).thenReturn(7L);
        when(currentUser.required()).thenReturn(new CurrentUser(7L, "alice", "Alice",
                Set.of("worktime:read", "worktime:write")));
    }

    @Test
    void prepareReturnsMissingFieldsWithoutCallingDomainPreview() {
        var tool = new WorktimeRecordCreatePrepareTool(worktime, actions, currentUser, mapper);

        var result = tool.execute(mapper.createObjectNode().put("date", "2026-09-22"));

        assertEquals(ToolStatus.NEEDS_INPUT, result.status());
        assertEquals("start", result.structuredContent().path("missingFields").path(0).asText());
        verify(worktime, never()).previewCreateRecord(any());
    }

    @Test
    void prepareUsesServerPreviewAndRequiresConfirmation() {
        RecordPreview preview = new RecordPreview("2026-09-22", "09:00", "20:30", 60,
                150, new BigDecimal("40.50"), "release");
        when(worktime.previewCreateRecord(any())).thenReturn(preview);
        var tool = new WorktimeRecordCreatePrepareTool(worktime, actions, currentUser, mapper);

        var result = tool.execute(mapper.createObjectNode().put("date", "2026-09-22")
                .put("start", "09:00").put("end", "20:30").put("rest", 60).put("note", "release"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, result.status());
        assertEquals(150, result.structuredContent().path("overtimeMin").asInt());
        assertEquals(ActionStatus.WAITING_CONFIRMATION,
                repository.find(result.actionId(), 7L).orElseThrow().status());
    }

    @Test
    void commitRequiresApprovalAndCanExecuteOnlyOnce() {
        RecordPreview preview = new RecordPreview("2026-09-22", "09:00", "20:30", 60,
                150, new BigDecimal("40.50"), "release");
        when(worktime.previewCreateRecord(any())).thenReturn(preview);
        WorkRecord saved = new WorkRecord(9L, "2026-09-22", "09:00", "20:30", 60, 150,
                new BigDecimal("40.50"), "release", "v1", "Asia/Shanghai", 1);
        when(worktime.createRecord(any(), any())).thenReturn(saved);
        var prepare = new WorktimeRecordCreatePrepareTool(worktime, actions, currentUser, mapper);
        var commit = new WorktimeRecordCreateCommitTool(worktime, actions, currentUser, mapper);
        var prepared = prepare.execute(mapper.createObjectNode().put("date", "2026-09-22")
                .put("start", "09:00").put("end", "20:30").put("rest", 60).put("note", "release"));
        var input = mapper.createObjectNode().put("actionId", prepared.actionId());

        assertThrows(IllegalStateException.class, () -> commit.execute(input));
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED, commit.execute(input).status());
        assertThrows(IllegalStateException.class, () -> commit.execute(input));
        verify(worktime).createRecord(any(RecordCommand.class), eq(prepared.actionId()));
    }

    @Test
    void updatePrepareBuildsServerDiffAndCommitUsesOriginalRevision() {
        WorkRecord original = new WorkRecord(9L, "2026-09-23", "09:00", "18:00", 60, 0,
                new BigDecimal("50.00"), "normal", "v1", "Asia/Shanghai", 4);
        RecordPreview preview = new RecordPreview("2026-09-23", "09:00", "21:00", 90,
                150, new BigDecimal("38.10"), "release");
        when(worktime.record(9L)).thenReturn(original);
        when(worktime.previewUpdateRecord(eq(9L), any())).thenReturn(preview);
        when(worktime.updateRecord(eq(9L), any(), eq("4"))).thenReturn(
                new WorkRecord(9L, "2026-09-23", "09:00", "21:00", 90, 150,
                        new BigDecimal("38.10"), "release", "v2", "Asia/Shanghai", 5));
        var prepare = new WorktimeRecordUpdatePrepareTool(worktime, actions, currentUser, mapper);
        var commit = new WorktimeRecordUpdateCommitTool(worktime, actions, currentUser, mapper);

        var prepared = prepare.execute(mapper.createObjectNode().put("recordId", 9)
                .put("end", "21:00").put("rest", 90).put("note", "release"));

        assertEquals(ToolStatus.NEEDS_CONFIRMATION, prepared.status());
        assertEquals(5, prepared.structuredContent().path("diff").size());
        assertEquals(4L, repository.find(prepared.actionId(), 7L).orElseThrow().expectedRevision());
        actions.approve(prepared.actionId(), 7L);
        assertEquals(ToolStatus.COMPLETED,
                commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId())).status());
        verify(worktime).updateRecord(eq(9L), any(RecordCommand.class), eq("4"));
    }

    @Test
    void updateCommitReturnsConflictAndCannotOverwriteNewerRecord() {
        WorkRecord original = new WorkRecord(9L, "2026-09-23", "09:00", "18:00", 60, 0,
                new BigDecimal("50.00"), "normal", "v1", "Asia/Shanghai", 4);
        when(worktime.record(9L)).thenReturn(original);
        when(worktime.previewUpdateRecord(eq(9L), any())).thenReturn(
                new RecordPreview("2026-09-23", "09:00", "20:00", 60, 90,
                        new BigDecimal("42.00"), "normal"));
        when(worktime.updateRecord(eq(9L), any(), eq("4")))
                .thenThrow(new ConflictException("资源版本已变化", 5));
        var prepare = new WorktimeRecordUpdatePrepareTool(worktime, actions, currentUser, mapper);
        var commit = new WorktimeRecordUpdateCommitTool(worktime, actions, currentUser, mapper);
        var prepared = prepare.execute(mapper.createObjectNode().put("recordId", 9).put("end", "20:00"));

        actions.approve(prepared.actionId(), 7L);
        var result = commit.execute(mapper.createObjectNode().put("actionId", prepared.actionId()));

        assertEquals(ToolStatus.CONFLICT, result.status());
        assertEquals(5L, result.structuredContent().path("latestRevision").asLong());
    }

    private static final class MemoryRepository implements PendingActionRepository {
        private final Map<String, PendingAction> actions = new ConcurrentHashMap<>();

        @Override
        public void insert(PendingAction action) { actions.put(action.id(), action); }

        @Override
        public Optional<PendingAction> find(String id, long userId) {
            return Optional.ofNullable(actions.get(id)).filter(action -> action.userId() == userId);
        }

        @Override
        public boolean transition(String id, long userId, ActionStatus expected, ActionStatus next, Instant updatedAt) {
            final boolean[] changed = {false};
            actions.computeIfPresent(id, (key, current) -> {
                if (current.userId() != userId || current.status() != expected) return current;
                changed[0] = true;
                return current.transition(next, updatedAt);
            });
            return changed[0];
        }
    }
}
