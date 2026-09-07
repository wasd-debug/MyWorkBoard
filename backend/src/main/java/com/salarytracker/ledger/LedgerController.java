package com.salarytracker.ledger;

import com.salarytracker.platform.ApiResponse;
import com.salarytracker.platform.Audit;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/ledger", "/api/ledger"})
public class LedgerController {
    private final LedgerService service;

    public LedgerController(LedgerService service) { this.service = service; }

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('ledger:read')")
    public ApiResponse<List<Map<String, Object>>> accounts() { return ApiResponse.ok(service.accounts()); }

    @PostMapping("/accounts")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "account.create", targetType = "ledger_account")
    public ApiResponse<Map<String, Object>> createAccount(@RequestBody Map<String, Object> body) { return ApiResponse.ok(service.createAccount(body)); }

    @PatchMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "account.update", targetType = "ledger_account")
    public ApiResponse<Map<String, Object>> updateAccount(@PathVariable long id, @RequestBody Map<String, Object> body, @RequestHeader(value = "If-Match", required = false) String revision) { return ApiResponse.ok(service.updateAccount(id, body, revision)); }

    @DeleteMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "account.delete", targetType = "ledger_account")
    public ApiResponse<Map<String, Object>> deleteAccount(@PathVariable long id, @RequestHeader(value = "If-Match", required = false) String revision) { return ApiResponse.ok(service.deleteAccount(id, revision)); }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ledger:read')")
    public ApiResponse<List<Map<String, Object>>> categories() { return ApiResponse.ok(service.categories()); }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "category.create", targetType = "ledger_category")
    public ApiResponse<Map<String, Object>> createCategory(@RequestBody Map<String, Object> body) { return ApiResponse.ok(service.createCategory(body)); }

    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "category.update", targetType = "ledger_category")
    public ApiResponse<Map<String, Object>> updateCategory(@PathVariable long id, @RequestBody Map<String, Object> body, @RequestHeader(value = "If-Match", required = false) String revision) { return ApiResponse.ok(service.updateCategory(id, body, revision)); }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "category.delete", targetType = "ledger_category")
    public ApiResponse<Map<String, Object>> deleteCategory(@PathVariable long id, @RequestHeader(value = "If-Match", required = false) String revision) { return ApiResponse.ok(service.deleteCategory(id, revision)); }

    @GetMapping("/transactions")
    @PreAuthorize("hasAuthority('ledger:read')")
    public ApiResponse<List<Map<String, Object>>> transactions(@RequestParam(required = false) String from, @RequestParam(required = false) String to, @RequestParam(defaultValue = "100") int limit) { return ApiResponse.ok(service.transactions(from, to, limit)); }

    @PostMapping("/transactions")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "transaction.create", targetType = "ledger_transaction")
    public ApiResponse<Map<String, Object>> createTransaction(@RequestBody Map<String, Object> body, @RequestHeader(value = "Idempotency-Key", required = false) String opId) { return ApiResponse.ok(service.createTransaction(body, opId)); }

    @PatchMapping("/transactions/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "transaction.update", targetType = "ledger_transaction")
    public ApiResponse<Map<String, Object>> updateTransaction(@PathVariable long id, @RequestBody Map<String, Object> body, @RequestHeader(value = "If-Match", required = false) String revision) { return ApiResponse.ok(service.updateTransaction(id, body, revision)); }

    @DeleteMapping("/transactions/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "transaction.delete", targetType = "ledger_transaction")
    public ApiResponse<Map<String, Object>> deleteTransaction(@PathVariable long id, @RequestHeader(value = "If-Match", required = false) String revision) { return ApiResponse.ok(service.deleteTransaction(id, revision)); }

    @GetMapping("/reports")
    @PreAuthorize("hasAuthority('ledger:read')")
    public ApiResponse<Map<String, Object>> reports(@RequestParam(required = false) String from, @RequestParam(required = false) String to) { return ApiResponse.ok(service.reports(from, to)); }

    @GetMapping("/budgets")
    @PreAuthorize("hasAuthority('ledger:read')")
    public ApiResponse<List<Map<String, Object>>> budgets(@RequestParam(required = false) String month) { return ApiResponse.ok(service.budgets(month)); }

    @PostMapping("/budgets")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "budget.create", targetType = "ledger_budget")
    public ApiResponse<Map<String, Object>> createBudget(@RequestBody Map<String, Object> body) { return ApiResponse.ok(service.createBudget(body)); }

    @DeleteMapping("/budgets/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Audit(module = "ledger", action = "budget.delete", targetType = "ledger_budget")
    public ApiResponse<Map<String, Object>> deleteBudget(@PathVariable long id) { return ApiResponse.ok(service.deleteBudget(id)); }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ledger:import')")
    @Audit(module = "ledger", action = "import", targetType = "ledger_transaction")
    public ApiResponse<List<Map<String, Object>>> importCsv(@RequestPart("file") MultipartFile file) throws Exception { return ApiResponse.ok(service.importCsv(file)); }

    @PostMapping(value = "/import/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ledger:import')")
    @Audit(module = "ledger", action = "import.excel", targetType = "ledger_transaction")
    public ApiResponse<List<Map<String, Object>>> importExcel(@RequestPart("file") MultipartFile file) throws Exception { return ApiResponse.ok(service.importExcel(file)); }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('ledger:read')")
    public ResponseEntity<ByteArrayResource> export(@RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        byte[] data = service.exportCsv(from, to);
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8)); headers.setContentDisposition(ContentDisposition.attachment().filename("ledger.csv", StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).contentLength(data.length).body(new ByteArrayResource(data));
    }

    @PostMapping("/sync/push")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> syncPush(@RequestBody List<Map<String, Object>> operations) { return ApiResponse.ok(service.syncPush(operations)); }

    @GetMapping("/sync/pull")
    @PreAuthorize("hasAuthority('ledger:read')")
    public ApiResponse<Map<String, Object>> syncPull(@RequestParam(defaultValue = "0") long cursor, @RequestParam(defaultValue = "200") int limit) { return ApiResponse.ok(service.syncPull(cursor, limit)); }

    @PostMapping("/materialize")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> materialize(@RequestParam(required = false) String month) { String target = month == null || month.isBlank() ? YearMonth.now().minusMonths(1).toString() : month; return ApiResponse.ok(Map.of("month", target, "accounts", service.materializeMonthEnd(target))); }

    @PostMapping("/ai/preview")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> aiPreview(@RequestBody Map<String, Object> body) { return ApiResponse.ok(service.aiPreview(String.valueOf(body.getOrDefault("text", "")))); }

    @PostMapping("/ai/confirm")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> aiConfirm(@RequestBody Map<String, Object> body) { return ApiResponse.ok(service.aiConfirm(body)); }
}
