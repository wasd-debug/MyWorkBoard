package com.salarytracker.task;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public final class TaskModels {
    private TaskModels() {
    }

    @Schema(name = "TaskList")
    public record TaskList(String publicId, String name, String color, String icon, String systemKey,
                           int sortOrder, boolean archived, long revision) {
    }

    @Schema(name = "TaskListCommand")
    public record TaskListCommand(String name, String color, String icon, Integer sortOrder, Boolean archived) {
    }

    @Schema(name = "TaskTag")
    public record TaskTag(String publicId, String parentId, String name, String color, int sortOrder,
                          long revision) {
    }

    @Schema(name = "TaskTagCommand")
    public record TaskTagCommand(String parentId, String name, String color, Integer sortOrder) {
    }

    @Schema(name = "TaskChecklistItem")
    public record ChecklistItem(String publicId, String title, boolean completed, int sortOrder, long revision) {
    }

    @Schema(name = "TaskChecklistCommand")
    public record ChecklistCommand(String publicId, String title, Boolean completed, Integer sortOrder) {
    }

    @Schema(name = "TaskItem")
    public record TaskItem(String publicId, String listId, String title, String description, String status,
                           String priority, String startAt, String dueAt, boolean allDay, String timezone,
                           Integer durationMinutes, String source, String completedAt, long revision,
                           String parentId, List<String> tagIds, List<ChecklistItem> checklist,
                           int completedSubtasks, int totalSubtasks, boolean deleted, String deletedAt,
                           String rrule, String recurrenceAnchor, String seriesId, Integer seriesSequence,
                           String plannedDueAt, int focusMinutes) {
        public TaskItem(String publicId, String listId, String title, String description, String status,
                        String priority, String startAt, String dueAt, boolean allDay, String timezone,
                        Integer durationMinutes, String source, String completedAt, long revision) {
            this(publicId, listId, title, description, status, priority, startAt, dueAt, allDay, timezone,
                    durationMinutes, source, completedAt, revision, null, List.of(), List.of(), 0, 0, false, null,
                    null, null, null, null, null, 0);
        }
    }

    @Schema(name = "TaskPage")
    public record TaskPage(List<TaskItem> items, int page, int size, long total) {
    }

    @Schema(name = "TaskCalendarItem")
    public record CalendarItem(String publicId, String title, String status, String priority,
                               String startAt, String dueAt, boolean allDay, String timezone,
                               Integer durationMinutes, long revision) {
    }

    @Schema(name = "TaskRescheduleCommand")
    public record RescheduleCommand(String startAt, String dueAt, Boolean allDay, Integer durationMinutes) {
    }

    @Schema(name = "TaskCommand")
    public record TaskCommand(String listId, String title, String description, String priority,
                              String startAt, String dueAt, Boolean allDay, String timezone,
                              Integer durationMinutes, String parentId, List<String> tagIds,
                              List<ChecklistCommand> checklist, String rrule, String recurrenceAnchor) {
        public TaskCommand(String listId, String title, String description, String priority, String startAt,
                           String dueAt, Boolean allDay, String timezone, Integer durationMinutes, String parentId,
                           List<String> tagIds, List<ChecklistCommand> checklist) {
            this(listId, title, description, priority, startAt, dueAt, allDay, timezone, durationMinutes,
                    parentId, tagIds, checklist, null, null);
        }
        public TaskCommand(String listId, String title, String description, String priority, String startAt,
                           String dueAt, Boolean allDay, String timezone, Integer durationMinutes) {
            this(listId, title, description, priority, startAt, dueAt, allDay, timezone, durationMinutes,
                    null, null, null, null, null);
        }
    }

    @Schema(name = "TaskDeletedResource")
    public record DeletedResource(String publicId, long revision, boolean deleted) {
    }

    @Schema(name = "TaskSettings")
    public record Settings(String defaultListId, String defaultView, int weekStart, String timezone, long revision) {
    }

    @Schema(name = "TaskReminder")
    public record Reminder(String publicId, String taskId, String kind, Integer offsetMinutes, String remindAt,
                           String channel, boolean sent, String sentAt, boolean dailyUntilDone, long revision) {
    }

    @Schema(name = "TaskReminderCommand")
    public record ReminderCommand(String kind, Integer offsetMinutes, String remindAt, String channel,
                                  Boolean dailyUntilDone) {
    }

    @Schema(name = "TaskInboxMessage")
    public record InboxMessage(String publicId, String category, String type, String title, String body,
                               String level, String taskId, String deepLink, String readAt, String createdAt) {
    }

    @Schema(name = "TaskInboxPage")
    public record InboxPage(List<InboxMessage> items, int page, int size, long total) {
    }

    @Schema(name = "TaskInboxUnread")
    public record InboxUnread(long unread, long overdue) {
    }

    @Schema(name = "TaskInboxReadCommand")
    public record InboxReadCommand(List<String> ids, Boolean all) {
    }

    public enum SyncAction { UPSERT, DELETE, PURGE }

    public enum SyncStatus { APPLIED, DUPLICATE, CONFLICT, FORBIDDEN, REJECTED }

    @Schema(name = "TaskSyncPayload")
    public record SyncPayload(String id, String listId, String title, String description, String status,
                              String priority, String startAt, String dueAt, Boolean allDay, String timezone,
                              Integer durationMinutes, Long revision, String parentId, List<String> tagIds,
                              List<ChecklistCommand> checklist, String name, String color, String icon,
                              Integer sortOrder, Boolean archived, String rrule, String recurrenceAnchor,
                              Map<String, Object> extra) {
        public SyncPayload(String id, String listId, String title, String description, String status,
                           String priority, String startAt, String dueAt, Boolean allDay, String timezone,
                           Integer durationMinutes, Long revision, String parentId, List<String> tagIds,
                           List<ChecklistCommand> checklist, String name, String color, String icon,
                           Integer sortOrder, Boolean archived) {
            this(id, listId, title, description, status, priority, startAt, dueAt, allDay, timezone,
                    durationMinutes, revision, parentId, tagIds, checklist, name, color, icon, sortOrder, archived,
                    null, null, null);
        }
        public SyncPayload(String id, String listId, String title, String description, String status,
                           String priority, String startAt, String dueAt, Boolean allDay, String timezone,
                           Integer durationMinutes, Long revision) {
            this(id, listId, title, description, status, priority, startAt, dueAt, allDay, timezone,
                    durationMinutes, revision, null, null, null, null, null, null, null, null, null, null, null);
        }
    }

    @Schema(name = "TaskSyncOperation")
    public record SyncOperation(String opId, String entityType, String entityId, SyncAction operation,
                                Long baseRevision, SyncPayload payload) {
    }

    @Schema(name = "TaskSyncEntity")
    public record SyncEntity(String id, String listId, String title, String description, String status,
                             String priority, String startAt, String dueAt, boolean allDay, String timezone,
                             Integer durationMinutes, String source, String completedAt, long revision,
                             boolean deleted, String parentId, List<String> tagIds, List<ChecklistItem> checklist,
                             String deletedAt, String name, String color, String icon, String systemKey,
                             Integer sortOrder, Boolean archived, String rrule, String recurrenceAnchor,
                             String seriesId, Integer seriesSequence, String plannedDueAt, Integer focusMinutes,
                             Map<String, Object> extra) {
        public SyncEntity(String id, String listId, String title, String description, String status,
                          String priority, String startAt, String dueAt, boolean allDay, String timezone,
                          Integer durationMinutes, String source, String completedAt, long revision,
                          boolean deleted) {
            this(id, listId, title, description, status, priority, startAt, dueAt, allDay, timezone,
                    durationMinutes, source, completedAt, revision, deleted, null, List.of(), List.of(), null,
                    null, null, null, null, null, null, null, null, null, null, null, null, null);
        }
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
