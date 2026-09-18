package com.salarytracker.worktime;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
import com.salarytracker.worktime.WorktimeModels.DeletedResource;
import com.salarytracker.worktime.WorktimeModels.RecordCommand;
import com.salarytracker.worktime.WorktimeModels.Settings;
import com.salarytracker.worktime.WorktimeModels.SettingsUpdate;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping(value = "/api/v1/worktime", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Worktime")
public class WorktimeController {
    private final WorktimeService worktimeService;

    public WorktimeController(WorktimeService worktimeService) {
        this.worktimeService = worktimeService;
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('worktime:read')")
    @Operation(operationId = "getWorktimeSettings")
    public ApiResponse<Settings> settings() {
        return ApiResponse.ok(worktimeService.readSettings());
    }

    @PutMapping(value = "/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "settings.update", targetType = "work_setting")
    @Operation(operationId = "updateWorktimeSettings")
    public ApiResponse<Settings> updateSettings(@RequestBody SettingsUpdate body,
                                                @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(worktimeService.writeSettings(body, ifMatch));
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('worktime:read')")
    @Operation(operationId = "listWorktimeRecords")
    public ApiResponse<List<WorkRecord>> records(@RequestParam(required = false) String from,
                                                 @RequestParam(required = false) String to,
                                                 @RequestParam(defaultValue = "50") int limit,
                                                 @RequestParam(defaultValue = "0") int offset) {
        return ApiResponse.ok(worktimeService.listRecords(from, to, limit, offset));
    }

    @PostMapping(value = "/records", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "record.create", targetType = "work_record")
    @Operation(operationId = "createWorktimeRecord")
    public ApiResponse<WorkRecord> create(@RequestBody RecordCommand body,
                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(worktimeService.createRecord(body, idempotencyKey));
    }

    @PatchMapping(value = "/records/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "record.update", targetType = "work_record")
    @Operation(operationId = "updateWorktimeRecord")
    public ApiResponse<WorkRecord> update(@PathVariable long id, @RequestBody RecordCommand body,
                                          @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(worktimeService.updateRecord(id, body, ifMatch));
    }

    @DeleteMapping("/records/{id}")
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "record.delete", targetType = "work_record")
    @Operation(operationId = "deleteWorktimeRecord")
    public ApiResponse<DeletedResource> delete(@PathVariable long id,
                                               @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(worktimeService.deleteRecord(id, ifMatch));
    }
}
