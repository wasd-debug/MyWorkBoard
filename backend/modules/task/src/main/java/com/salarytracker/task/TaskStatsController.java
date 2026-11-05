package com.salarytracker.task;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.salarytracker.task.TaskStatsModels.*;

@RestController
@RequestMapping(value = "/api/v1/tasks/stats")
@Tag(name = "Task statistics")
public class TaskStatsController {
    private final TaskStatsService service;

    public TaskStatsController(TaskStatsService service) {
        this.service = service;
    }

    @GetMapping("/overview") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskStatsOverview")
    public ApiResponse<Overview> overview() {
        return ApiResponse.ok(service.overview());
    }

    @GetMapping("/tasks") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskStatsTaskPage")
    public ApiResponse<TaskPageStats> tasks(@RequestParam(required = false) String mode,
                                            @RequestParam(required = false) String date,
                                            @RequestParam(required = false) String groupMode) {
        return ApiResponse.ok(service.taskPage(mode, date, groupMode));
    }

    @GetMapping("/focus") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskStatsFocusPage")
    public ApiResponse<FocusPage> focus(@RequestParam(required = false) String date,
                                        @RequestParam(required = false) String groupMode,
                                        @RequestParam(required = false) String trendMode,
                                        @RequestParam(required = false) String trendDate,
                                        @RequestParam(required = false) String timelineDate,
                                        @RequestParam(required = false) String month,
                                        @RequestParam(required = false) String year) {
        return ApiResponse.ok(service.focusPage(date, groupMode, trendMode, trendDate, timelineDate, month, year));
    }
}
