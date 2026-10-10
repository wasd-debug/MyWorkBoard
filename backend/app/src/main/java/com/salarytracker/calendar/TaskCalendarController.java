package com.salarytracker.calendar;

import com.salarytracker.calendar.TaskCalendarModels.CalendarResponse;
import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/tasks/calendar")
@Tag(name = "Tasks")
public class TaskCalendarController {
    private final TaskCalendarService calendarService;

    public TaskCalendarController(TaskCalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getTaskCalendar")
    public ApiResponse<CalendarResponse> calendar(@RequestParam String from, @RequestParam String to,
                                                  @RequestParam(required = false) String layers,
                                                  @RequestParam(defaultValue = "1") String lunar) {
        return ApiResponse.ok(calendarService.calendar(LocalDate.parse(from), LocalDate.parse(to), layers,
                "1".equals(lunar) || "true".equalsIgnoreCase(lunar)));
    }
}
