package com.salarytracker.ledger;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/ledger/recurring", "/api/ledger/recurring"})
public class RecurringBillController {
    private final RecurringBillService service;

    public RecurringBillController(RecurringBillService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('ledger:read')")
    public ApiResponse<List<Map<String, Object>>> list() { return ApiResponse.ok(service.list()); }

    @PostMapping
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "recurring.create", targetType = "recurring_bill")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) { return ApiResponse.ok(service.create(body)); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "recurring.update", targetType = "recurring_bill")
    public ApiResponse<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body) { return ApiResponse.ok(service.update(id, body)); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "recurring.delete", targetType = "recurring_bill")
    public ApiResponse<Map<String, Object>> delete(@PathVariable long id) { return ApiResponse.ok(service.delete(id)); }

    @PostMapping("/run")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> run() { return ApiResponse.ok(Map.of("generated", service.runDue())); }
}
