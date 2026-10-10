package com.salarytracker.task;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
import com.salarytracker.task.TaskModels.DeletedResource;
import com.salarytracker.task.TaskModels.Settings;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import com.salarytracker.task.TaskModels.TaskList;
import com.salarytracker.task.TaskModels.TaskPage;
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

import java.util.List;

@RestController
@RequestMapping(value = "/api/v1/tasks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping("/lists")
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTaskLists")
    public ApiResponse<List<TaskList>> lists() {
        return ApiResponse.ok(taskService.listLists());
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listTasks")
    public ApiResponse<TaskPage> tasks(@RequestParam(required = false) String listId,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(taskService.listTasks(listId, status, page, size));
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
}
