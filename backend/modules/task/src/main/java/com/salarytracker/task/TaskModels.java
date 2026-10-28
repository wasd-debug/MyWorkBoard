package com.salarytracker.task;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public final class TaskModels {
    private TaskModels() {
    }

    @Schema(name = "TaskList")
    public record TaskList(String publicId, String groupId, String name, String color, String icon, String systemKey,
                           long sortOrder, boolean archived, boolean pinned, String viewType, String objectType,
                           boolean showInSmartLists,
                           long revision) {
        public TaskList(String publicId, String groupId, String name, String color, String icon, String systemKey,
                        int sortOrder, boolean archived, long revision) {
            this(publicId, groupId, name, color, icon, systemKey, sortOrder, archived, false, "LIST", "TASK", true, revision);
        }
        public TaskList(String publicId, String groupId, String name, String color, String icon, String systemKey,
                        int sortOrder, boolean archived, boolean pinned, String viewType, String objectType, long revision) {
            this(publicId, groupId, name, color, icon, systemKey, sortOrder, archived, pinned, viewType, objectType, true, revision);
        }
    }

    @Schema(name = "TaskListCommand")
    public record TaskListCommand(String name, String color, String icon, Long sortOrder, Boolean archived,
                                  Boolean pinned, String viewType, String objectType, Boolean showInSmartLists) {
        public TaskListCommand(String name, String color, String icon, Long sortOrder, Boolean archived) {
            this(name, color, icon, sortOrder, archived, null, null, null, null);
        }
        public TaskListCommand(String name, String color, String icon, int sortOrder, Boolean archived) {
            this(name, color, icon, (long) sortOrder, archived, null, null, null, null);
        }
        public TaskListCommand(String name, String color, String icon, int sortOrder, Boolean archived,
                               Boolean pinned, String viewType, String objectType) {
            this(name, color, icon, (long) sortOrder, archived, pinned, viewType, objectType, null);
        }
        public TaskListCommand(String name, String color, String icon, int sortOrder, Boolean archived,
                               Boolean pinned, String viewType, String objectType, Boolean showInSmartLists) {
            this(name, color, icon, (long) sortOrder, archived, pinned, viewType, objectType, showInSmartLists);
        }
    }

    @Schema(name = "TaskListGroup")
    public record ListGroup(String publicId, String name, String color, String icon, int sortOrder,
                            boolean pinned, long revision) {
        public ListGroup(String publicId, String name, String color, String icon, int sortOrder, long revision) {
            this(publicId, name, color, icon, sortOrder, false, revision);
        }
    }

    @Schema(name = "TaskListGroupCommand")
    public record ListGroupCommand(String name, String color, String icon, Integer sortOrder, Boolean pinned) {
        public ListGroupCommand(String name, String color, String icon, Integer sortOrder) {
            this(name, color, icon, sortOrder, null);
        }
    }

    @Schema(name = "TaskListGroupAssignment")
    public record ListGroupAssignment(String groupId, Long sortOrder) {
        public ListGroupAssignment(String groupId) {
            this(groupId, null);
        }
    }

    @Schema(name = "TaskActivity")
    public record Activity(String publicId, String eventType, String summary, String detailJson, String createdAt) {
    }

    @Schema(name = "TaskActivityPage")
    public record ActivityPage(List<Activity> items, int page, int size, long total) {
    }

    @Schema(name = "TaskListActivity")
    public record ListActivity(String publicId, String eventType, String summary, String taskId, String taskTitle,
                               String priority, List<String> tagIds, String actorName, String createdAt) {
    }

    @Schema(name = "TaskListActivityPage")
    public record ListActivityPage(List<ListActivity> items, int page, int size, long total) {
    }

    @Schema(name = "TaskComment")
    public record Comment(String publicId, String taskId, String authorId, String authorName,
                          String bodyMarkdown, String createdAt, String updatedAt, long revision,
                          boolean deleted) {
    }

    @Schema(name = "TaskCommentCommand")
    public record CommentCommand(String bodyMarkdown) {
    }

    @Schema(name = "TaskCommentPage")
    public record CommentPage(List<Comment> items, int page, int size, long total) {
    }

    @Schema(name = "TaskAttachment")
    public record Attachment(String publicId, String taskId, String fileName, String mimeType,
                             long sizeBytes, String checksumSha256, String downloadUrl,
                             String createdBy, String createdAt, long revision, boolean deleted) {
    }

    @Schema(name = "TaskAttachmentDelete")
    public record AttachmentDelete(String publicId, long revision, boolean deleted) {
    }

    @Schema(name = "TaskTag")
    public record TaskTag(String publicId, String parentId, String name, String color, String icon, int sortOrder,
                          long revision) {
        public TaskTag(String publicId, String parentId, String name, String color, int sortOrder, long revision) {
            this(publicId, parentId, name, color, null, sortOrder, revision);
        }
    }

    @Schema(name = "TaskTagCommand")
    public record TaskTagCommand(String parentId, String name, String color, Integer sortOrder, String icon) {
        public TaskTagCommand(String parentId, String name, String color, Integer sortOrder) {
            this(parentId, name, color, sortOrder, null);
        }
    }

    @Schema(name = "TaskTagMergeCommand")
    public record TaskTagMergeCommand(String targetTagId, long targetRevision) {
    }

    @Schema(name = "TaskTagMergeResult")
    public record TaskTagMergeResult(String sourceTagId, String targetTagId, int movedTasks,
                                     int movedChildren, long sourceRevision, long targetRevision) {
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
                           String plannedDueAt, int sortOrder, int focusMinutes, String createdAt, int attachmentCount,
                           @Schema(type = "string", format = "date-time") String abandonedAt,
                           String itemKind) {
        public TaskItem(String publicId, String listId, String title, String description, String status,
                        String priority, String startAt, String dueAt, boolean allDay, String timezone,
                        Integer durationMinutes, String source, String completedAt, long revision,
                        String parentId, List<String> tagIds, List<ChecklistItem> checklist,
                        int completedSubtasks, int totalSubtasks, boolean deleted, String deletedAt,
                        String rrule, String recurrenceAnchor, String seriesId, Integer seriesSequence,
                        String plannedDueAt, int sortOrder, int focusMinutes, String createdAt, int attachmentCount) {
            this(publicId, listId, title, description, status, priority, startAt, dueAt, allDay, timezone,
                    durationMinutes, source, completedAt, revision, parentId, tagIds, checklist,
                    completedSubtasks, totalSubtasks, deleted, deletedAt, rrule, recurrenceAnchor, seriesId,
                    seriesSequence, plannedDueAt, sortOrder, focusMinutes, createdAt, attachmentCount, null, null);
        }

        public TaskItem(String publicId, String listId, String title, String description, String status,
                        String priority, String startAt, String dueAt, boolean allDay, String timezone,
                        Integer durationMinutes, String source, String completedAt, long revision) {
            this(publicId, listId, title, description, status, priority, startAt, dueAt, allDay, timezone,
                    durationMinutes, source, completedAt, revision, null, List.of(), List.of(), 0, 0, false, null,
                    null, null, null, null, null, 0, 0, null, 0, null, null);
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
                              List<ChecklistCommand> checklist, String rrule, String recurrenceAnchor,
                              Integer sortOrder, String itemKind) {
        public TaskCommand(String listId, String title, String description, String priority, String startAt,
                           String dueAt, Boolean allDay, String timezone, Integer durationMinutes, String parentId,
                           List<String> tagIds, List<ChecklistCommand> checklist, String rrule, String recurrenceAnchor) {
            this(listId, title, description, priority, startAt, dueAt, allDay, timezone, durationMinutes,
                    parentId, tagIds, checklist, rrule, recurrenceAnchor, null, null);
        }
        public TaskCommand(String listId, String title, String description, String priority, String startAt,
                           String dueAt, Boolean allDay, String timezone, Integer durationMinutes, String parentId,
                           List<String> tagIds, List<ChecklistCommand> checklist) {
            this(listId, title, description, priority, startAt, dueAt, allDay, timezone, durationMinutes,
                    parentId, tagIds, checklist, null, null, null, null);
        }
        public TaskCommand(String listId, String title, String description, String priority, String startAt,
                           String dueAt, Boolean allDay, String timezone, Integer durationMinutes) {
            this(listId, title, description, priority, startAt, dueAt, allDay, timezone, durationMinutes,
                    null, null, null, null, null, null, null);
        }
    }

    @Schema(name = "TaskBatchTarget")
    public record BatchTarget(String publicId, long revision) {
    }

    @Schema(name = "TaskBatchCommand")
    public record BatchCommand(List<BatchTarget> targets, String action, String listId, String dueAt,
                               List<String> tagIds, String priority, ReminderCommand reminder) {
    }

    @Schema(name = "TaskBatchResult")
    public record BatchResult(int affected, List<TaskItem> items) {
    }

    @Schema(name = "TaskSortCommand")
    public record SortCommand(Integer sortOrder) {
    }

    @Schema(name = "TaskDeletedResource")
    public record DeletedResource(String publicId, long revision, boolean deleted) {
    }

    @Schema(name = "TaskSettings")
    public record Settings(String defaultListId, String defaultView, Integer defaultReminderMinutes,
                           @Schema(allowableValues = {"TODAY", "NONE"}) String defaultDueDate,
                           @Schema(description = "NEXT_HOUR or a local HH:mm time") String defaultDueTime,
                           int weekStart, boolean showLunar, boolean overlayWorktime, boolean overlayLedger,
                           boolean overlayHoliday, boolean overlayHabit, boolean notificationReminders,
                           boolean notificationOverdue, String dailySummaryTime, String quietStart, String quietEnd,
                           boolean feedIncludeDescription, String timezone, long revision) {
        public Settings(String defaultListId, String defaultView, int weekStart, String timezone, long revision) {
            this(defaultListId, defaultView, null, "TODAY", "NEXT_HOUR", weekStart, true, true, true, true, true,
                    true, true, null, null, null, false, timezone, revision);
        }
    }

    @Schema(name = "TaskSettingsCommand")
    public record SettingsCommand(String defaultListId, String defaultView, Integer defaultReminderMinutes,
                                  @Schema(allowableValues = {"TODAY", "NONE"}) String defaultDueDate,
                                  @Schema(description = "NEXT_HOUR or a local HH:mm time") String defaultDueTime,
                                  Integer weekStart, Boolean showLunar, Boolean overlayWorktime,
                                  Boolean overlayLedger, Boolean overlayHoliday, Boolean overlayHabit,
                                  Boolean notificationReminders, Boolean notificationOverdue,
                                  String dailySummaryTime, String quietStart, String quietEnd,
                                  Boolean feedIncludeDescription, String timezone) {
        public SettingsCommand(String defaultListId, String defaultView, Integer defaultReminderMinutes,
                               Integer weekStart, Boolean showLunar, Boolean overlayWorktime,
                               Boolean overlayLedger, Boolean overlayHoliday, Boolean overlayHabit,
                               Boolean notificationReminders, Boolean notificationOverdue,
                               String dailySummaryTime, String quietStart, String quietEnd,
                               Boolean feedIncludeDescription, String timezone) {
            this(defaultListId, defaultView, defaultReminderMinutes, null, null, weekStart, showLunar,
                    overlayWorktime, overlayLedger, overlayHoliday, overlayHabit, notificationReminders,
                    notificationOverdue, dailySummaryTime, quietStart, quietEnd, feedIncludeDescription, timezone);
        }
    }

    @Schema(name = "TaskSidebarPreferences")
    public record SidebarPreferences(List<String> hiddenSystemViews, List<String> pinnedSystemViews,
                                    List<String> conditionalSystemViews,
                                     List<String> hiddenListIds, List<String> pinnedListIds,
                                     List<String> hiddenTagIds, List<String> pinnedTagIds,
                                     List<String> hiddenFilterIds, List<String> pinnedFilterIds,
                                     Map<String, Boolean> collapsedSections, long revision) { }

    @Schema(name = "TaskSidebarPreferencesCommand")
    public record SidebarPreferencesCommand(List<String> hiddenSystemViews, List<String> pinnedSystemViews,
                                           List<String> conditionalSystemViews,
                                           List<String> hiddenListIds, List<String> pinnedListIds,
                                           List<String> hiddenTagIds, List<String> pinnedTagIds,
                                           List<String> hiddenFilterIds, List<String> pinnedFilterIds,
                                           Map<String, Boolean> collapsedSections) { }

    @Schema(name = "TaskFeedToken")
    public record FeedToken(boolean active, String tokenHint, String feedUrl, String createdAt,
                            String lastAccessedAt) {
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
                              Long sortOrder, Boolean archived, String rrule, String recurrenceAnchor,
                              Map<String, Object> extra, String itemKind) {
        public SyncPayload(String id, String listId, String title, String description, String status,
                           String priority, String startAt, String dueAt, Boolean allDay, String timezone,
                           Integer durationMinutes, Long revision, String parentId, List<String> tagIds,
                           List<ChecklistCommand> checklist, String name, String color, String icon,
                           Integer sortOrder, Boolean archived) {
            this(id, listId, title, description, status, priority, startAt, dueAt, allDay, timezone,
                    durationMinutes, revision, parentId, tagIds, checklist, name, color, icon,
                    sortOrder == null ? null : sortOrder.longValue(), archived,
                    null, null, null, null);
        }
        public SyncPayload(String id, String listId, String title, String description, String status,
                           String priority, String startAt, String dueAt, Boolean allDay, String timezone,
                           Integer durationMinutes, Long revision) {
            this(id, listId, title, description, status, priority, startAt, dueAt, allDay, timezone,
                    durationMinutes, revision, null, null, null, null, null, null, null, null, null, null, null, null);
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
                             Long sortOrder, Boolean archived, String rrule, String recurrenceAnchor,
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
