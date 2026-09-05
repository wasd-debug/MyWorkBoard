package com.salarytracker.worktime;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
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
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/worktime", "/api/worktime"})
public class WorktimeController {
    private final WorktimeService worktimeService;

    public WorktimeController(WorktimeService worktimeService) {
        this.worktimeService = worktimeService;
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('worktime:read')")
    public ApiResponse<Map<String, Object>> settings() {
        return ApiResponse.ok(worktimeService.readSettings());
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "settings.update", targetType = "work_setting")
    public ApiResponse<Map<String, Object>> updateSettings(@RequestBody Map<String, Object> body,
                                                           @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(worktimeService.writeSettings(body, ifMatch));
    }

    @GetMapping("/records")
    @PreAuthorize("hasAuthority('worktime:read')")
    public ApiResponse<List<Map<String, Object>>> records(@RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to,
                                                          @RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(worktimeService.listRecords(from, to, limit));
    }

    @PostMapping("/records")
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "record.create", targetType = "work_record")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(worktimeService.createRecord(body, idempotencyKey));
    }

    @PatchMapping("/records/{id}")
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "record.update", targetType = "work_record")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body,
                                                   @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(worktimeService.updateRecord(id, body, ifMatch));
    }

    @DeleteMapping("/records/{id}")
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "record.delete", targetType = "work_record")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id,
                                                   @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(worktimeService.deleteRecord(id, ifMatch));
    }

    @GetMapping("/snapshot")
    @PreAuthorize("hasAuthority('worktime:read')")
    public ApiResponse<Map<String, Object>> snapshot() {
        return ApiResponse.ok(worktimeService.readSnapshot());
    }

    @PutMapping("/snapshot")
    @PreAuthorize("hasAuthority('worktime:write')")
    @Audit(module = "worktime", action = "snapshot.replace", targetType = "worktime")
    public ApiResponse<Map<String, Object>> replaceSnapshot(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(worktimeService.replaceSnapshot(body));
    }
}
