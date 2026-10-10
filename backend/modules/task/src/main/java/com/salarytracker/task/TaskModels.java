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
}
