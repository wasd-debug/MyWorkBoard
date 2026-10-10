package com.salarytracker.task;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
import com.salarytracker.task.TaskModels.DeletedResource;
import com.salarytracker.task.TaskModels.Settings;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import com.salarytracker.task.TaskModels.TaskList;
import com.salarytracker.task.TaskModels.TaskPage;
import com.salarytracker.task.TaskModels.TaskListCommand;
import com.salarytracker.task.TaskModels.TaskTag;
import com.salarytracker.task.TaskModels.TaskTagCommand;
import com.salarytracker.task.TaskModels.SyncOperation;
import com.salarytracker.task.TaskModels.SyncPullResponse;
import com.salarytracker.task.TaskModels.SyncPushResponse;
import com.salarytracker.task.TaskModels.RescheduleCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tasks")
public class TaskController {
    private final TaskService taskService;
    private final TaskSyncService taskSyncService;
    private final TaskReminderService reminderService;

    public TaskController(TaskService taskService, TaskSyncService taskSyncService, TaskReminderService reminderService) {
        this.taskService = taskService;
        this.taskSyncService = taskSyncService;
        this.reminderService = reminderService;
    }

    @GetMapping("/lists")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskLists")
    public ApiResponse<List<TaskList>> lists() {
        return ApiResponse.ok(taskService.listLists());
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
