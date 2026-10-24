package com.salarytracker.task;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
import com.salarytracker.task.TaskModels.DeletedResource;
import com.salarytracker.task.TaskModels.Settings;
import com.salarytracker.task.TaskModels.SettingsCommand;
import com.salarytracker.task.TaskModels.SidebarPreferences;
import com.salarytracker.task.TaskModels.SidebarPreferencesCommand;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import com.salarytracker.task.TaskModels.TaskList;
import com.salarytracker.task.TaskModels.TaskPage;
import com.salarytracker.task.TaskModels.TaskListCommand;
import com.salarytracker.task.TaskModels.TaskTag;
import com.salarytracker.task.TaskModels.TaskTagCommand;
import com.salarytracker.task.TaskModels.TaskTagMergeCommand;
import com.salarytracker.task.TaskModels.TaskTagMergeResult;
import com.salarytracker.task.TaskModels.ListGroup;
import com.salarytracker.task.TaskModels.ListGroupCommand;
import com.salarytracker.task.TaskModels.ListGroupAssignment;
import com.salarytracker.task.TaskModels.ActivityPage;
import com.salarytracker.task.TaskModels.ListActivityPage;
import com.salarytracker.task.TaskModels.Comment;
import com.salarytracker.task.TaskModels.CommentCommand;
import com.salarytracker.task.TaskModels.CommentPage;
import com.salarytracker.task.TaskModels.Attachment;
import com.salarytracker.task.TaskModels.SyncOperation;
import com.salarytracker.task.TaskModels.SyncPullResponse;
import com.salarytracker.task.TaskModels.SyncPushResponse;
import com.salarytracker.task.TaskModels.RescheduleCommand;
import com.salarytracker.task.TaskModels.BatchCommand;
import com.salarytracker.task.TaskModels.BatchResult;
import com.salarytracker.task.TaskModels.SortCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tasks")
public class TaskController {
    private final TaskService taskService;
    private final TaskSyncService taskSyncService;
    private final TaskReminderService reminderService;
    private final TaskCommentService commentService;
    private final TaskAttachmentService attachmentService;

    public TaskController(TaskService taskService, TaskSyncService taskSyncService, TaskReminderService reminderService,
                          TaskCommentService commentService, TaskAttachmentService attachmentService) {
        this.taskService = taskService;
        this.taskSyncService = taskSyncService;
        this.reminderService = reminderService;
        this.commentService = commentService;
        this.attachmentService = attachmentService;
    }

    @GetMapping("/lists")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskLists")
    public ApiResponse<List<TaskList>> lists() {
        return ApiResponse.ok(taskService.listLists());
    }

    @GetMapping("/list-groups")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskListGroups")
    public ApiResponse<List<ListGroup>> listGroups() { return ApiResponse.ok(taskService.listGroups()); }

    @PostMapping(value = "/list-groups", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "createTaskListGroup")
    public ApiResponse<ListGroup> createListGroup(@RequestBody ListGroupCommand body) {
        return ApiResponse.ok(taskService.createListGroup(body));
    }

    @PutMapping(value = "/list-groups/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "updateTaskListGroup")
    public ApiResponse<ListGroup> updateListGroup(@PathVariable String publicId, @RequestBody ListGroupCommand body,
                                                   @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.updateListGroup(publicId, body, ifMatch));
    }

    @DeleteMapping("/list-groups/{publicId}")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "deleteTaskListGroup")
    public ApiResponse<Void> deleteListGroup(@PathVariable String publicId,
                                              @RequestHeader("If-Match") String ifMatch) {
        taskService.deleteListGroup(publicId, ifMatch);
        return ApiResponse.ok(null);
    }

    @PutMapping(value = "/lists/{publicId}/group", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "assignTaskListGroup")
    public ApiResponse<TaskList> assignListGroup(@PathVariable String publicId,
                                                  @RequestBody ListGroupAssignment body,
                                                  @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.assignListGroup(publicId, body == null ? null : body.groupId(),
                body == null ? null : body.sortOrder(), ifMatch));
    }

    @GetMapping("/lists/trash")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listDeletedTaskLists")
    public ApiResponse<List<TaskList>> deletedLists() {
        return ApiResponse.ok(taskService.listDeletedLists());
    }

    @PostMapping(value = "/lists", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "createTaskList")
    public ApiResponse<TaskList> createList(@RequestBody TaskListCommand body,
                                            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(taskService.createList(body, idempotencyKey));
    }

    @PutMapping(value = "/lists/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "updateTaskList")
    public ApiResponse<TaskList> updateList(@PathVariable String publicId, @RequestBody TaskListCommand body,
                                            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.updateList(publicId, body, ifMatch));
    }

    @DeleteMapping("/lists/{publicId}")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "deleteTaskList")
    public ApiResponse<DeletedResource> deleteList(@PathVariable String publicId,
                                                   @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.deleteList(publicId, ifMatch));
    }

    @PostMapping("/lists/{publicId}/restore")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "restoreTaskList")
    public ApiResponse<TaskList> restoreList(@PathVariable String publicId,
                                             @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.restoreList(publicId, ifMatch));
    }

    @GetMapping("/lists/{publicId}/activity")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskListActivity")
    public ApiResponse<ListActivityPage> listActivity(@PathVariable String publicId,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(taskService.listActivity(publicId, page, size));
    }

    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskTags")
    public ApiResponse<List<TaskTag>> tags() {
        return ApiResponse.ok(taskService.listTags());
    }

    @PostMapping(value = "/tags", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "createTaskTag")
    public ApiResponse<TaskTag> createTag(@RequestBody TaskTagCommand body) {
        return ApiResponse.ok(taskService.createTag(body));
    }

    @PutMapping(value = "/tags/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "updateTaskTag")
    public ApiResponse<TaskTag> updateTag(@PathVariable String publicId, @RequestBody TaskTagCommand body,
                                          @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.updateTag(publicId, body, ifMatch));
    }

    @DeleteMapping("/tags/{publicId}")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "deleteTaskTag")
    public ApiResponse<DeletedResource> deleteTag(@PathVariable String publicId,
                                                  @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.deleteTag(publicId, ifMatch));
    }

    @PostMapping(value = "/tags/{publicId}/merge", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.tag.merge", targetType = "task-tag")
    @Operation(operationId = "mergeTaskTag")
    public ApiResponse<TaskTagMergeResult> mergeTag(@PathVariable String publicId,
                                                     @RequestBody TaskTagMergeCommand body,
                                                     @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.mergeTag(publicId, body.targetTagId(), ifMatch, body.targetRevision()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTasks")
    public ApiResponse<TaskPage> tasks(@RequestParam(required = false) String listId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String view,
                                      @RequestParam(required = false) String tagId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(taskService.listTasks(listId, status, view, tagId, page, size));
    }

    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.batch", targetType = "task")
    @Operation(operationId = "batchTasks")
    public ApiResponse<BatchResult> batch(@RequestBody BatchCommand body) {
        return ApiResponse.ok(taskService.batch(body));
    }

    @PostMapping(value = "/{publicId}/sort", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.sort", targetType = "task")
    @Operation(operationId = "sortTask")
    public ApiResponse<TaskItem> sort(@PathVariable String publicId, @RequestBody SortCommand body,
                                      @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.sort(publicId, body, ifMatch));
    }

    @GetMapping("/{publicId}/activity")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskActivity")
    public ApiResponse<ActivityPage> activity(@PathVariable String publicId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(taskService.activity(publicId, page, size));
    }

    @GetMapping("/{publicId}/comments")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskComments")
    public ApiResponse<CommentPage> comments(@PathVariable String publicId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(commentService.list(publicId, page, size));
    }

    @PostMapping(value = "/{publicId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.comment.create", targetType = "task")
    @Operation(operationId = "createTaskComment")
    public ApiResponse<Comment> createComment(@PathVariable String publicId, @RequestBody CommentCommand body,
                                                @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(commentService.create(publicId, body, idempotencyKey));
    }

    @PutMapping(value = "/{publicId}/comments/{commentId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.comment.update", targetType = "task")
    @Operation(operationId = "updateTaskComment")
    public ApiResponse<Comment> updateComment(@PathVariable String publicId, @PathVariable String commentId,
                                                @RequestBody CommentCommand body,
                                                @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(commentService.update(publicId, commentId, body, ifMatch));
    }

    @DeleteMapping("/{publicId}/comments/{commentId}")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.comment.delete", targetType = "task")
    @Operation(operationId = "deleteTaskComment")
    public ApiResponse<Boolean> deleteComment(@PathVariable String publicId, @PathVariable String commentId,
                                               @RequestHeader("If-Match") String ifMatch) {
        commentService.delete(publicId, commentId, ifMatch);
        return ApiResponse.ok(true);
    }

    @GetMapping("/{publicId}/attachments")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskAttachments")
    public ApiResponse<List<Attachment>> attachments(@PathVariable String publicId) {
        return ApiResponse.ok(attachmentService.list(publicId));
    }

    @PostMapping(value = "/{publicId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.attachment.create", targetType = "task")
    @Operation(operationId = "uploadTaskAttachment")
    public ApiResponse<Attachment> uploadAttachment(@PathVariable String publicId,
                                                      @RequestPart("file") MultipartFile file,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(attachmentService.upload(publicId, file, idempotencyKey));
    }

    @GetMapping("/{publicId}/attachments/{attachmentId}/content")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "downloadTaskAttachment")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable String publicId, @PathVariable String attachmentId) {
        TaskAttachmentService.Download download = attachmentService.download(publicId, attachmentId);
        Attachment metadata = download.metadata();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(metadata.mimeType()))
                .contentLength(metadata.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(metadata.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(download.resource());
    }

    @DeleteMapping("/{publicId}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.attachment.delete", targetType = "task")
    @Operation(operationId = "deleteTaskAttachment")
    public ApiResponse<Boolean> deleteAttachment(@PathVariable String publicId, @PathVariable String attachmentId,
                                                   @RequestHeader("If-Match") String ifMatch) {
        attachmentService.delete(publicId, attachmentId, ifMatch);
        return ApiResponse.ok(true);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.create", targetType = "task")
    @Operation(operationId = "createTask")
    public ApiResponse<TaskItem> create(@RequestBody TaskCommand body,
                                        @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(taskService.create(body, idempotencyKey));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTask")
    public ApiResponse<TaskItem> task(@PathVariable String publicId) {
        return ApiResponse.ok(taskService.get(publicId));
    }

    @PutMapping(value = "/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.update", targetType = "task")
    @Operation(operationId = "updateTask")
    public ApiResponse<TaskItem> update(@PathVariable String publicId, @RequestBody TaskCommand body,
                                        @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.update(publicId, body, ifMatch));
    }

    @PostMapping(value = "/{publicId}/reschedule", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.reschedule", targetType = "task")
    @Operation(operationId = "rescheduleTask")
    public ApiResponse<TaskItem> reschedule(@PathVariable String publicId, @RequestBody RescheduleCommand body,
                                            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.reschedule(publicId, body, ifMatch));
    }

    @PostMapping("/{publicId}/complete")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.complete", targetType = "task")
    @Operation(operationId = "completeTask")
    public ApiResponse<TaskItem> complete(@PathVariable String publicId,
                                          @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.complete(publicId, ifMatch));
    }

    @PostMapping("/{publicId}/reopen")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.reopen", targetType = "task")
    @Operation(operationId = "reopenTask")
    public ApiResponse<TaskItem> reopen(@PathVariable String publicId,
                                        @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.reopen(publicId, ifMatch));
    }

    @PostMapping("/{publicId}/abandon")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.abandon", targetType = "task")
    @Operation(operationId = "abandonTask")
    public ApiResponse<TaskItem> abandon(@PathVariable String publicId,
                                         @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.abandon(publicId, ifMatch));
    }

    @PostMapping("/{publicId}/restore-abandoned")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.restoreAbandoned", targetType = "task")
    @Operation(operationId = "restoreAbandonedTask")
    public ApiResponse<TaskItem> restoreAbandoned(@PathVariable String publicId,
                                                  @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.restoreAbandoned(publicId, ifMatch));
    }

    @PostMapping("/{publicId}/restore")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.restore", targetType = "task")
    @Operation(operationId = "restoreTask")
    public ApiResponse<TaskItem> restore(@PathVariable String publicId,
                                         @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.restore(publicId, ifMatch));
    }

    @DeleteMapping("/trash/{publicId}")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.purge", targetType = "task")
    @Operation(operationId = "purgeTask")
    public ApiResponse<DeletedResource> purge(@PathVariable String publicId,
                                              @RequestParam String confirmation) {
        return ApiResponse.ok(taskService.purge(publicId, confirmation));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasAuthority('task:write')")
    @Audit(module = "task", action = "task.delete", targetType = "task")
    @Operation(operationId = "deleteTask")
    public ApiResponse<DeletedResource> delete(@PathVariable String publicId,
                                               @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.delete(publicId, ifMatch));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskSettings")
    public ApiResponse<Settings> settings() {
        return ApiResponse.ok(taskService.settings());
    }

    @PutMapping(value = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "updateTaskSettings")
    public ApiResponse<Settings> updateSettings(@RequestBody SettingsCommand body,
                                                @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.updateSettings(body, ifMatch));
    }

    @GetMapping("/sidebar-preferences")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskSidebarPreferences")
    public ApiResponse<SidebarPreferences> sidebarPreferences() {
        return ApiResponse.ok(taskService.sidebarPreferences());
    }

    @PutMapping(value = "/sidebar-preferences", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "updateTaskSidebarPreferences")
    public ApiResponse<SidebarPreferences> updateSidebarPreferences(@RequestBody SidebarPreferencesCommand body,
                                                                    @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(taskService.updateSidebarPreferences(body, ifMatch));
    }

    @PostMapping(value = "/sync/push", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "pushTaskSync")
    public ApiResponse<SyncPushResponse> syncPush(@RequestBody List<SyncOperation> operations) {
        return ApiResponse.ok(taskSyncService.push(operations));
    }

    @GetMapping("/sync/pull")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "pullTaskSync")
    public ApiResponse<SyncPullResponse> syncPull(@RequestParam(defaultValue = "0") long cursor,
                                                  @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(taskSyncService.pull(cursor, limit));
    }

    @GetMapping("/{publicId}/reminders")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskReminders")
    public ApiResponse<List<TaskModels.Reminder>> reminders(@PathVariable String publicId) {
        return ApiResponse.ok(reminderService.list(publicId));
    }

    @PostMapping(value = "/{publicId}/reminders", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "createTaskReminder")
    public ApiResponse<TaskModels.Reminder> createReminder(@PathVariable String publicId,
                                                            @RequestBody TaskModels.ReminderCommand body) {
        return ApiResponse.ok(reminderService.create(publicId, body));
    }

    @PutMapping(value = "/{publicId}/reminders/{reminderId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "updateTaskReminder")
    public ApiResponse<TaskModels.Reminder> updateReminder(@PathVariable String publicId, @PathVariable String reminderId,
                                                            @RequestBody TaskModels.ReminderCommand body,
                                                            @RequestHeader("If-Match") long revision) {
        return ApiResponse.ok(reminderService.update(publicId, reminderId, body, revision));
    }

    @DeleteMapping("/{publicId}/reminders/{reminderId}")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "deleteTaskReminder")
    public ApiResponse<Boolean> deleteReminder(@PathVariable String publicId, @PathVariable String reminderId,
                                                @RequestHeader("If-Match") long revision) {
        reminderService.delete(publicId, reminderId, revision);
        return ApiResponse.ok(true);
    }

    @GetMapping("/inbox")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskInbox")
    public ApiResponse<TaskModels.InboxPage> inbox(@RequestParam(defaultValue = "false") boolean unread,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(reminderService.inbox(unread, page, size));
    }

    @GetMapping("/inbox/unread")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskInboxUnread")
    public ApiResponse<TaskModels.InboxUnread> inboxUnread() { return ApiResponse.ok(reminderService.unread()); }

    @GetMapping(value = "/inbox/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "streamTaskInbox")
    public SseEmitter inboxStream() { return reminderService.stream(); }

    @PostMapping(value = "/inbox/read", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "readTaskInbox")
    public ApiResponse<TaskModels.InboxUnread> readInbox(@RequestBody TaskModels.InboxReadCommand body) {
        return ApiResponse.ok(reminderService.read(body));
    }

    @DeleteMapping("/inbox")
    @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "clearTaskInbox")
    public ApiResponse<Long> clearInbox() { return ApiResponse.ok(reminderService.clearRead()); }
}
