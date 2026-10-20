package com.salarytracker.task;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public final class TaskEnhancementModels {
    private TaskEnhancementModels() { }

    @Schema(name = "TaskSmartFilter")
    public record SmartFilter(String publicId, String name, List<String> listIds, List<String> tagIds,
                              List<String> priorities, String status, String dueRange, String keyword,
                              boolean includeSubtasks, boolean matchAllTags, int sortOrder, boolean pinned,
                              String icon, JsonNode rules, long revision) {
        public SmartFilter(String publicId, String name, List<String> listIds, List<String> tagIds,
                           List<String> priorities, String status, String dueRange, String keyword,
                           boolean includeSubtasks, boolean matchAllTags, int sortOrder, boolean pinned,
                           long revision) {
            this(publicId, name, listIds, tagIds, priorities, status, dueRange, keyword, includeSubtasks,
                    matchAllTags, sortOrder, pinned, null, null, revision);
        }
    }

    @Schema(name = "TaskSmartFilterCommand")
    public record SmartFilterCommand(String name, List<String> listIds, List<String> tagIds,
                                     List<String> priorities, String status, String dueRange, String keyword,
                                     Boolean includeSubtasks, Boolean matchAllTags, Integer sortOrder,
                                     Boolean pinned, String icon, JsonNode rules) {
        public SmartFilterCommand(String name, List<String> listIds, List<String> tagIds,
                                  List<String> priorities, String status, String dueRange, String keyword,
                                  Boolean includeSubtasks, Boolean matchAllTags, Integer sortOrder, Boolean pinned) {
            this(name, listIds, tagIds, priorities, status, dueRange, keyword, includeSubtasks, matchAllTags,
                    sortOrder, pinned, null, null);
        }
    }

    @Schema(name = "TaskBoardColumn")
    public record BoardColumn(String key, String label, List<TaskModels.TaskItem> tasks) { }

    @Schema(name = "TaskBoard")
    public record Board(String dimension, List<BoardColumn> columns) { }

    @Schema(name = "TaskSearchResult")
    public record SearchResult(List<TaskModels.TaskItem> tasks, List<TaskModels.TaskList> lists,
                               List<TaskModels.TaskTag> tags, String query) { }

    @Schema(name = "TaskStats")
    public record Stats(String from, String to, long created, long completed, long overdueCompleted,
                        double completionRate, double averageCompletionHours, int focusMinutes,
                        Map<String, Long> byPriority, Map<String, Long> byList, Map<String, Long> byTag) { }

    @Schema(name = "TaskParseCommand")
    public record ParseCommand(String text) { }

    @Schema(name = "TaskParsedDraft")
    public record ParsedDraft(String title, String dueAt, String priority, List<String> tagNames,
                              String listName, String rrule, List<String> unrecognized) { }

    @Schema(name = "TaskImportRow")
    public record ImportRow(int rowNumber, String sheet, String title, String description, String listName,
                            String dueAt, String priority, List<String> tags, String status, String rrule,
                            String state, String message) { }

    @Schema(name = "TaskImportPreview")
    public record ImportPreview(String previewId, String fileName, int valid, int duplicates, int errors,
                                List<ImportRow> rows, String expiresAt) { }

    @Schema(name = "TaskImportCommitCommand")
    public record ImportCommitCommand(String previewId, Boolean skipDuplicates) { }

    @Schema(name = "TaskImportResult")
    public record ImportResult(String previewId, int created, int skipped, boolean replayed) { }

    @Schema(name = "TaskExportCommand")
    public record ExportCommand(String format, Boolean includeCompleted) { }
}
