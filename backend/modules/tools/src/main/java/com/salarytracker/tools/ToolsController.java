package com.salarytracker.tools;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.tools.ToolsModels.DeletedResource;
import com.salarytracker.tools.ToolsModels.QuitSmokingCraving;
import com.salarytracker.tools.ToolsModels.QuitSmokingCravingCreate;
import com.salarytracker.tools.ToolsModels.QuitSmokingCravingUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingDay;
import com.salarytracker.tools.ToolsModels.QuitSmokingDayUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingSettings;
import com.salarytracker.tools.ToolsModels.QuitSmokingSettingsUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingSummary;
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
@RequestMapping(value = "/api/v1/tools", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Tools")
public class ToolsController {
    private final QuitSmokingService quitSmoking;

    public ToolsController(QuitSmokingService quitSmoking) {
        this.quitSmoking = quitSmoking;
    }

    @GetMapping("/quit-smoking/settings") @PreAuthorize("hasAuthority('tools:read')")
    @Operation(operationId = "getQuitSmokingSettings")
    public ApiResponse<QuitSmokingSettings> quitSmokingSettings() {
        return ApiResponse.ok(quitSmoking.settings());
    }

    @PutMapping(value = "/quit-smoking/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('tools:write')") @Operation(operationId = "updateQuitSmokingSettings")
    public ApiResponse<QuitSmokingSettings> updateQuitSmokingSettings(
            @RequestBody QuitSmokingSettingsUpdate body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(quitSmoking.updateSettings(body, ifMatch));
    }

    @GetMapping("/quit-smoking/days") @PreAuthorize("hasAuthority('tools:read')")
    @Operation(operationId = "listQuitSmokingDays")
    public ApiResponse<List<QuitSmokingDay>> quitSmokingDays(@RequestParam(required = false) String from,
                                                             @RequestParam(required = false) String to) {
        return ApiResponse.ok(quitSmoking.days(from, to));
    }

    @PutMapping(value = "/quit-smoking/days/{date}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('tools:write')") @Operation(operationId = "putQuitSmokingDay")
    public ApiResponse<QuitSmokingDay> putQuitSmokingDay(@PathVariable("date") String date,
                                                         @RequestBody QuitSmokingDayUpdate body,
                                                         @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(quitSmoking.putDay(date, body, ifMatch));
    }

    @GetMapping("/quit-smoking/cravings") @PreAuthorize("hasAuthority('tools:read')")
    @Operation(operationId = "listQuitSmokingCravings")
    public ApiResponse<List<QuitSmokingCraving>> quitSmokingCravings(@RequestParam(required = false) String from,
                                                                     @RequestParam(required = false) String to) {
        return ApiResponse.ok(quitSmoking.cravings(from, to));
    }

    @PostMapping(value = "/quit-smoking/cravings", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('tools:write')") @Operation(operationId = "createQuitSmokingCraving")
    public ApiResponse<QuitSmokingCraving> createQuitSmokingCraving(
            @RequestBody QuitSmokingCravingCreate body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok(quitSmoking.createCraving(body, idempotencyKey));
    }

    @PutMapping(value = "/quit-smoking/cravings/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('tools:write')") @Operation(operationId = "updateQuitSmokingCraving")
    public ApiResponse<QuitSmokingCraving> updateQuitSmokingCraving(
            @PathVariable String id, @RequestBody QuitSmokingCravingUpdate body,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(quitSmoking.updateCraving(id, body, ifMatch));
    }

    @DeleteMapping("/quit-smoking/cravings/{id}")
    @PreAuthorize("hasAuthority('tools:write')") @Operation(operationId = "deleteQuitSmokingCraving")
    public ApiResponse<DeletedResource> deleteQuitSmokingCraving(
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        return ApiResponse.ok(quitSmoking.deleteCraving(id, ifMatch));
    }

    @GetMapping("/quit-smoking/summary") @PreAuthorize("hasAuthority('tools:read')")
    @Operation(operationId = "getQuitSmokingSummary")
    public ApiResponse<QuitSmokingSummary> quitSmokingSummary() {
        return ApiResponse.ok(quitSmoking.summary());
    }
}
