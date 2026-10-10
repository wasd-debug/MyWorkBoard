package com.salarytracker.task;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public final class TaskModels {
    private TaskModels() {
    }

    @Schema(name = "TaskList")
    public record TaskList(String publicId, String name, String systemKey, int sortOrder, long revision) {
    }

    @Schema(name = "TaskItem")
    public record TaskItem(String publicId, String listId, String title, String description, String status,
                           String priority, String startAt, String dueAt, boolean allDay, String timezone,
                           Integer durationMinutes, String source, String completedAt, long revision) {
    }

    @Schema(name = "TaskPage")
    public record TaskPage(List<TaskItem> items, int page, int size, long total) {
    }

    @Schema(name = "TaskCommand")
    public record TaskCommand(String listId, String title, String description, String priority,
                              String startAt, String dueAt, Boolean allDay, String timezone,
                              Integer durationMinutes) {
    }

    @Schema(name = "TaskDeletedResource")
    public record DeletedResource(String publicId, long revision, boolean deleted) {
    }

    @Schema(name = "TaskSettings")
    public record Settings(String defaultListId, String defaultView, int weekStart, String timezone, long revision) {
    }

    public enum SyncAction { UPSERT, DELETE }

    public enum SyncStatus { APPLIED, DUPLICATE, CONFLICT, FORBIDDEN, REJECTED }

    @Schema(name = "TaskSyncPayload")
    public record SyncPayload(String id, String listId, String title, String description, String status,
                              String priority, String startAt, String dueAt, Boolean allDay, String timezone,
                              Integer durationMinutes, Long revision) {
    }

    @Schema(name = "TaskSyncOperation")
    public record SyncOperation(String opId, String entityType, String entityId, SyncAction operation,
                                Long baseRevision, SyncPayload payload) {
    }

    @Schema(name = "TaskSyncEntity")
    public record SyncEntity(String id, String listId, String title, String description, String status,
                             String priority, String startAt, String dueAt, boolean allDay, String timezone,
                             Integer durationMinutes, String source, String completedAt, long revision,
                             boolean deleted) {
    }

    @Schema(name = "TaskSyncOperationResult")
    public record SyncOperationResult(String opId, String entityType, String entityId, SyncStatus status,
                                      SyncEntity entity, Long serverRevision, SyncEntity serverEntity,
                                      List<String> conflictFields, String message) {
    }

    @Schema(name = "TaskSyncPushResponse")
    public record SyncPushResponse(List<SyncOperationResult> results, long applied, long duplicates) {
    }

    @Schema(name = "TaskSyncChange")
    public record SyncChange(long cursor, String opId, String entityType, String entityId,
                             SyncAction operation, SyncEntity payload, String createdAt) {
    }

    @Schema(name = "TaskSyncPullResponse")
    public record SyncPullResponse(List<SyncChange> operations, long cursor, boolean hasMore, long serverCursor) {
    }
}
