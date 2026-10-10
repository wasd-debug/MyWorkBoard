package com.salarytracker.task;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.task.TaskEfficiencyModels.Countdown;
import com.salarytracker.task.TaskEfficiencyModels.CountdownCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusFinishCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusSession;
import com.salarytracker.task.TaskEfficiencyModels.FocusSettings;
import com.salarytracker.task.TaskEfficiencyModels.FocusSettingsCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusStartCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusStats;
import com.salarytracker.task.TaskEfficiencyModels.Habit;
import com.salarytracker.task.TaskEfficiencyModels.HabitCheckin;
import com.salarytracker.task.TaskEfficiencyModels.HabitCheckinCommand;
import com.salarytracker.task.TaskEfficiencyModels.HabitCommand;
import com.salarytracker.task.TaskEfficiencyModels.HabitStats;
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
@Tag(name = "Task efficiency")
public class TaskEfficiencyController {
    private final TaskEfficiencyService service;

    public TaskEfficiencyController(TaskEfficiencyService service) {
        this.service = service;
    }

    @GetMapping("/focus/settings") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getFocusSettings")
    public ApiResponse<FocusSettings> settings() { return ApiResponse.ok(service.focusSettings()); }

    @PutMapping(value = "/focus/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')") @Operation(operationId = "updateFocusSettings")
    public ApiResponse<FocusSettings> settings(@RequestBody FocusSettingsCommand body,
                                                @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(service.updateFocusSettings(body, ifMatch));
    }

    @GetMapping("/focus/sessions") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listFocusSessions")
    public ApiResponse<List<FocusSession>> sessions(@RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.focusSessions(from, to));
    }

    @GetMapping("/focus/current") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getCurrentFocusSession")
    public ApiResponse<FocusSession> current() { return ApiResponse.ok(service.currentFocus()); }

    @PostMapping(value = "/focus/sessions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')") @Operation(operationId = "startFocusSession")
    public ApiResponse<FocusSession> start(@RequestBody FocusStartCommand body,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(service.startFocus(body, idempotencyKey));
    }

    @PostMapping(value = "/focus/sessions/{publicId}/finish", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')") @Operation(operationId = "finishFocusSession")
    public ApiResponse<FocusSession> finish(@PathVariable String publicId, @RequestBody FocusFinishCommand body,
                                            @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(service.finishFocus(publicId, body, ifMatch));
    }

    @DeleteMapping("/focus/sessions/{publicId}") @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "deleteFocusSession")
    public ApiResponse<Void> deleteFocus(@PathVariable String publicId, @RequestHeader("If-Match") String ifMatch) {
        service.deleteFocus(publicId, ifMatch); return ApiResponse.ok(null);
    }

    @GetMapping("/focus/stats") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getFocusStats")
    public ApiResponse<FocusStats> focusStats() { return ApiResponse.ok(service.focusStats()); }

    @GetMapping("/habits") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listHabits")
    public ApiResponse<List<Habit>> habits(@RequestParam(defaultValue = "false") boolean archived) {
        return ApiResponse.ok(service.habits(archived));
    }

    @PostMapping(value = "/habits", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')") @Operation(operationId = "createHabit")
    public ApiResponse<Habit> createHabit(@RequestBody HabitCommand body) { return ApiResponse.ok(service.createHabit(body)); }

    @PutMapping(value = "/habits/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')") @Operation(operationId = "updateHabit")
    public ApiResponse<Habit> updateHabit(@PathVariable String publicId, @RequestBody HabitCommand body,
                                          @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(service.updateHabit(publicId, body, ifMatch));
    }

    @DeleteMapping("/habits/{publicId}") @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "deleteHabit")
    public ApiResponse<Void> deleteHabit(@PathVariable String publicId, @RequestHeader("If-Match") String ifMatch) {
        service.deleteHabit(publicId, ifMatch); return ApiResponse.ok(null);
    }

    @PostMapping(value = "/habits/{publicId}/checkin", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')") @Operation(operationId = "checkinHabit")
    public ApiResponse<HabitCheckin> checkin(@PathVariable String publicId, @RequestBody HabitCheckinCommand body) {
        return ApiResponse.ok(service.checkin(publicId, body));
    }

    @GetMapping("/habits/{publicId}/stats") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "getHabitStats")
    public ApiResponse<HabitStats> habitStats(@PathVariable String publicId,
                                              @RequestParam(required = false) String from,
                                              @RequestParam(required = false) String to) {
        return ApiResponse.ok(service.habitStats(publicId, from, to));
    }

    @GetMapping("/countdowns") @PreAuthorize("hasAuthority('task:read')")
    @Operation(operationId = "listCountdowns")
    public ApiResponse<List<Countdown>> countdowns() { return ApiResponse.ok(service.countdowns()); }

    @PostMapping(value = "/countdowns", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')") @Operation(operationId = "createCountdown")
    public ApiResponse<Countdown> createCountdown(@RequestBody CountdownCommand body) {
        return ApiResponse.ok(service.createCountdown(body));
    }

    @PutMapping(value = "/countdowns/{publicId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('task:write')") @Operation(operationId = "updateCountdown")
    public ApiResponse<Countdown> updateCountdown(@PathVariable String publicId, @RequestBody CountdownCommand body,
                                                  @RequestHeader("If-Match") String ifMatch) {
        return ApiResponse.ok(service.updateCountdown(publicId, body, ifMatch));
    }

    @DeleteMapping("/countdowns/{publicId}") @PreAuthorize("hasAuthority('task:write')")
    @Operation(operationId = "deleteCountdown")
    public ApiResponse<Void> deleteCountdown(@PathVariable String publicId, @RequestHeader("If-Match") String ifMatch) {
        service.deleteCountdown(publicId, ifMatch); return ApiResponse.ok(null);
    }
}
