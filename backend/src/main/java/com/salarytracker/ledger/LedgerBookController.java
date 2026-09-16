package com.salarytracker.ledger;

import com.salarytracker.platform.ApiResponse;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ledger")
@PreAuthorize("hasAuthority('ledger:read')")
public class LedgerBookController {
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;
    private final LedgerAuditService audit;
    private final LedgerImportService imports;
    private final LedgerSyncService sync;
    private final LedgerAiService ai;
    private final LedgerReportAiService reportAi;
    private final LedgerScheduledTaskService scheduledTasks;

    public LedgerBookController(LedgerBookService books,
                                LedgerTransactionService transactions,
                                LedgerAuditService audit,
                                LedgerImportService imports,
                                LedgerSyncService sync,
                                LedgerAiService ai,
                                LedgerReportAiService reportAi,
                                LedgerScheduledTaskService scheduledTasks) {
        this.books = books;
        this.transactions = transactions;
        this.audit = audit;
        this.imports = imports;
        this.sync = sync;
        this.ai = ai;
        this.reportAi = reportAi;
        this.scheduledTasks = scheduledTasks;
    }

    @GetMapping("/books")
    public ApiResponse<List<Map<String, Object>>> books() {
        return ApiResponse.ok(books.books());
    }

    @PostMapping("/books")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> createBook(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(books.createBook(body));
    }

    @PatchMapping("/books/{bookId}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> updateBook(@PathVariable String bookId,
                                                       @RequestBody Map<String, Object> body,
                                                       @RequestHeader(value = "If-Match", required = false) String revision) {
        return ApiResponse.ok(books.updateBook(bookId, body, revision));
    }

    @DeleteMapping("/books/{bookId}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteBook(@PathVariable String bookId,
                                                       @RequestHeader(value = "If-Match", required = false) String revision) {
        return ApiResponse.ok(books.deleteBook(bookId, revision));
    }

    @GetMapping("/books/{bookId}/accounts")
    public ApiResponse<List<Map<String, Object>>> accounts(@PathVariable String bookId,
                                                           @RequestParam(defaultValue = "false") boolean includeHidden) {
        return ApiResponse.ok(books.accounts(bookId, includeHidden));
    }

    @PostMapping("/books/{bookId}/accounts")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> createAccount(@PathVariable String bookId,
                                                          @RequestBody Map<String, Object> body,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.createAccount(bookId, body, opId));
    }

    @PatchMapping("/books/{bookId}/accounts/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> updateAccount(@PathVariable String bookId,
                                                          @PathVariable String id,
                                                          @RequestBody Map<String, Object> body,
                                                          @RequestHeader(value = "If-Match", required = false) String revision,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateAccount(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/accounts/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteAccount(@PathVariable String bookId,
                                                          @PathVariable String id,
                                                          @RequestHeader(value = "If-Match", required = false) String revision,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteAccount(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/categories")
    public ApiResponse<List<Map<String, Object>>> categories(@PathVariable String bookId,
                                                             @RequestParam(defaultValue = "false") boolean includeHidden) {
        return ApiResponse.ok(books.categories(bookId, includeHidden));
    }

    @PostMapping("/books/{bookId}/categories")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> createCategory(@PathVariable String bookId,
                                                           @RequestBody Map<String, Object> body,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.createCategory(bookId, body, opId));
    }

    @PatchMapping("/books/{bookId}/categories/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> updateCategory(@PathVariable String bookId,
                                                           @PathVariable String id,
                                                           @RequestBody Map<String, Object> body,
                                                           @RequestHeader(value = "If-Match", required = false) String revision,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateCategory(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/categories/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteCategory(@PathVariable String bookId,
                                                           @PathVariable String id,
                                                           @RequestHeader(value = "If-Match", required = false) String revision,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteCategory(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/{type:merchants|projects}")
    public ApiResponse<List<Map<String, Object>>> namedResources(@PathVariable String bookId,
                                                                 @PathVariable String type,
                                                                 @RequestParam(defaultValue = "false") boolean includeHidden) {
        return ApiResponse.ok("merchants".equals(type)
                ? books.merchants(bookId, includeHidden)
                : books.projects(bookId, includeHidden));
    }

    @PostMapping("/books/{bookId}/{type:merchants|projects}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> createNamedResource(@PathVariable String bookId,
                                                                @PathVariable String type,
                                                                @RequestBody Map<String, Object> body,
                                                                @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.createNamedResource(bookId, singular(type), body, opId));
    }

    @PatchMapping("/books/{bookId}/{type:merchants|projects}/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> updateNamedResource(@PathVariable String bookId,
                                                                @PathVariable String type,
                                                                @PathVariable String id,
                                                                @RequestBody Map<String, Object> body,
                                                                @RequestHeader(value = "If-Match", required = false) String revision,
                                                                @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateNamedResource(bookId, singular(type), id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/{type:merchants|projects}/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteNamedResource(@PathVariable String bookId,
                                                                @PathVariable String type,
                                                                @PathVariable String id,
                                                                @RequestHeader(value = "If-Match", required = false) String revision,
                                                                @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteNamedResource(bookId, singular(type), id, revision, opId));
    }

    @GetMapping("/books/{bookId}/members")
    public ApiResponse<List<Map<String, Object>>> members(@PathVariable String bookId) {
        return ApiResponse.ok(books.members(bookId));
    }

    @PostMapping("/books/{bookId}/members")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> addMember(@PathVariable String bookId,
                                                      @RequestBody Map<String, Object> body,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.addMember(bookId, body, opId));
    }

    @PatchMapping("/books/{bookId}/members/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> updateMember(@PathVariable String bookId,
                                                         @PathVariable String id,
                                                         @RequestBody Map<String, Object> body,
                                                         @RequestHeader(value = "If-Match", required = false) String revision,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateMember(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/members/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteMember(@PathVariable String bookId,
                                                         @PathVariable String id,
                                                         @RequestHeader(value = "If-Match", required = false) String revision,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteMember(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/roles")
    public ApiResponse<List<Map<String, Object>>> roles(@PathVariable String bookId) {
        return ApiResponse.ok(books.roles(bookId));
    }

    @PostMapping("/books/{bookId}/roles")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> createRole(@PathVariable String bookId,
                                                       @RequestBody Map<String, Object> body,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.createRole(bookId, body, opId));
    }

    @PatchMapping("/books/{bookId}/roles/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> updateRole(@PathVariable String bookId,
                                                       @PathVariable String id,
                                                       @RequestBody Map<String, Object> body,
                                                       @RequestHeader(value = "If-Match", required = false) String revision,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateRole(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/roles/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteRole(@PathVariable String bookId,
                                                       @PathVariable String id,
                                                       @RequestHeader(value = "If-Match", required = false) String revision,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteRole(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/budgets")
    public ApiResponse<List<Map<String, Object>>> budgets(@PathVariable String bookId,
                                                          @RequestParam(required = false) String month) {
        return ApiResponse.ok(books.budgets(bookId, month));
    }

    @PutMapping("/books/{bookId}/budgets")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> upsertBudget(@PathVariable String bookId,
                                                         @RequestBody Map<String, Object> body,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.upsertBudget(bookId, body, opId));
    }

    @DeleteMapping("/books/{bookId}/budgets/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteBudget(@PathVariable String bookId,
                                                         @PathVariable String id,
                                                         @RequestHeader(value = "If-Match", required = false) String revision,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteBudget(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/scheduled-tasks")
    public ApiResponse<List<Map<String, Object>>> scheduledTasks(@PathVariable String bookId,
                                                                  @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return ApiResponse.ok(scheduledTasks.list(bookId, includeDeleted).stream().map(this::safe).toList());
    }

    @PostMapping("/books/{bookId}/scheduled-tasks")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> createScheduledTask(@PathVariable String bookId,
                                                                @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(safe(scheduledTasks.create(bookId, body)));
    }

    @PatchMapping("/books/{bookId}/scheduled-tasks/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> updateScheduledTask(@PathVariable String bookId,
                                                                @PathVariable String id,
                                                                @RequestBody Map<String, Object> body,
                                                                @RequestHeader(value = "If-Match", required = false) String revision) {
        return ApiResponse.ok(safe(scheduledTasks.update(bookId, id, body, revision)));
    }

    @DeleteMapping("/books/{bookId}/scheduled-tasks/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteScheduledTask(@PathVariable String bookId,
                                                                @PathVariable String id) {
        return ApiResponse.ok(scheduledTasks.delete(bookId, id));
    }

    @PostMapping("/books/{bookId}/scheduled-tasks/{id}/run")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> runScheduledTask(@PathVariable String bookId,
                                                             @PathVariable String id) {
        return ApiResponse.ok(safe(scheduledTasks.run(bookId, id)));
    }

    @GetMapping("/books/{bookId}/transactions")
    public ApiResponse<Map<String, Object>> transactionList(@PathVariable String bookId,
                                                            @RequestParam Map<String, String> filters) {
        return ApiResponse.ok(safe(transactions.list(bookId, filters)));
    }

    @GetMapping("/books/{bookId}/transactions/recent")
    public ApiResponse<List<Map<String, Object>>> recent(@PathVariable String bookId,
                                                         @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(safeList(transactions.recent(bookId, limit)));
    }

    @GetMapping("/books/{bookId}/overview")
    public ApiResponse<Map<String, Object>> overview(@PathVariable String bookId,
                                                     @RequestParam(required = false) String from,
                                                     @RequestParam(required = false) String to) {
        return ApiResponse.ok(transactions.overview(bookId, from, to));
    }

    @PostMapping("/books/{bookId}/transactions")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> createTransaction(@PathVariable String bookId,
                                                              @RequestBody Map<String, Object> body,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(safe(transactions.create(bookId, body, opId)));
    }

    @PatchMapping("/books/{bookId}/transactions/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> updateTransaction(@PathVariable String bookId,
                                                              @PathVariable String id,
                                                              @RequestBody Map<String, Object> body,
                                                              @RequestHeader(value = "If-Match", required = false) String revision,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(safe(transactions.update(bookId, id, body, revision, opId)));
    }

    @DeleteMapping("/books/{bookId}/transactions/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> deleteTransaction(@PathVariable String bookId,
                                                              @PathVariable String id,
                                                              @RequestHeader(value = "If-Match", required = false) String revision,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(transactions.delete(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/transactions/{id}/history")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable String bookId,
                                                          @PathVariable String id) {
        return ApiResponse.ok(safeList(transactions.history(bookId, id)));
    }

    @PostMapping("/books/{bookId}/transactions/{id}/copy")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> copy(@PathVariable String bookId,
                                                 @PathVariable String id,
                                                 @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(safe(transactions.copy(bookId, id, body == null ? Map.of() : body)));
    }

    @GetMapping("/books/{bookId}/recycle")
    public ApiResponse<Map<String, Object>> recycle(@PathVariable String bookId,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(books.recycle(bookId, page, pageSize));
    }

    @PostMapping("/books/{bookId}/recycle/{type}/{id}/restore")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> restore(@PathVariable String bookId,
                                                    @PathVariable String type,
                                                    @PathVariable String id,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok("transaction".equals(type)
                ? safe(transactions.restore(bookId, id, opId))
                : books.restoreResource(bookId, type, id, opId));
    }

    @DeleteMapping("/books/{bookId}/recycle/{type}/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> purge(@PathVariable String bookId,
                                                  @PathVariable String type,
                                                  @PathVariable String id) {
        return ApiResponse.ok("transaction".equals(type)
                ? transactions.purge(bookId, id)
                : books.purgeResource(bookId, type, id));
    }

    @GetMapping("/books/{bookId}/audit-logs")
    public ApiResponse<Map<String, Object>> auditLogs(@PathVariable String bookId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(audit.list(bookId, page, pageSize));
    }

    @DeleteMapping("/books/{bookId}/audit-logs")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> clearAuditLogs(@PathVariable String bookId,
                                                           @RequestBody(required = false) Map<String, List<Long>> body) {
        return ApiResponse.ok(audit.clear(bookId, body == null ? List.of() : body.get("ids")));
    }

    @PostMapping("/books/{bookId}/materialize")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> materialize(@PathVariable String bookId,
                                                        @RequestParam(required = false) String month) {
        String target = month == null || month.isBlank() ? YearMonth.now().minusMonths(1).toString() : month;
        return ApiResponse.ok(Map.of("month", target, "accounts", transactions.materialize(bookId, target)));
    }

    @PostMapping(value = "/books/{bookId}/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ledger:import')")
    public ApiResponse<Map<String, Object>> importPreview(@PathVariable String bookId,
                                                          @RequestPart("file") MultipartFile file,
                                                          @RequestParam(required = false) String template) throws Exception {
        return ApiResponse.ok(imports.preview(bookId, file, template));
    }

    @PostMapping("/books/{bookId}/imports/{batchId}/confirm")
    @PreAuthorize("hasAuthority('ledger:import')")
    public ApiResponse<Map<String, Object>> importConfirm(@PathVariable String bookId,
                                                          @PathVariable String batchId) {
        return ApiResponse.ok(imports.confirm(bookId, batchId));
    }

    @GetMapping("/books/{bookId}/export")
    public ResponseEntity<ByteArrayResource> export(@PathVariable String bookId,
                                                    @RequestParam(defaultValue = "csv") String format,
                                                    @RequestParam(required = false) String from,
                                                    @RequestParam(required = false) String to) {
        byte[] data = imports.export(bookId, format, from, to);
        boolean excel = "xlsx".equalsIgnoreCase(format) || "excel".equalsIgnoreCase(format);
        String filename = excel ? "ledger.xlsx" : "ledger.csv";
        MediaType type = excel
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : new MediaType("text", "csv", StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).contentLength(data.length).body(new ByteArrayResource(data));
    }

    @PostMapping("/books/{bookId}/sync/push")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> syncPush(@PathVariable String bookId,
                                                     @RequestBody List<Map<String, Object>> operations) {
        return ApiResponse.ok(sync.push(bookId, operations));
    }

    @GetMapping("/books/{bookId}/sync/pull")
    public ApiResponse<Map<String, Object>> syncPull(@PathVariable String bookId,
                                                     @RequestParam(defaultValue = "0") long cursor,
                                                     @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(sync.pull(bookId, cursor, limit));
    }

    @PostMapping("/books/{bookId}/ai/preview")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> aiPreview(@PathVariable String bookId,
                                                      @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(ai.previewText(bookId, String.valueOf(body.getOrDefault("text", ""))));
    }

    @PostMapping("/books/{bookId}/ai/monthly-analysis")
    public ApiResponse<Map<String, Object>> aiMonthlyAnalysis(@PathVariable String bookId,
                                                              @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(reportAi.analyzeMonth(bookId, body));
    }

    @PostMapping(value = "/books/{bookId}/ai/image-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> aiImagePreview(@PathVariable String bookId,
                                                           @RequestPart("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(ai.previewImage(bookId, file));
    }

    @PostMapping("/books/{bookId}/ai/{draftId}/confirm")
    @PreAuthorize("hasAuthority('ledger:write')")
    public ApiResponse<Map<String, Object>> aiConfirm(@PathVariable String bookId,
                                                      @PathVariable String draftId,
                                                      @RequestBody Map<String, Object> body,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(ai.confirm(bookId, draftId, body, opId));
    }

    private String singular(String type) {
        return "merchants".equals(type) ? "merchant" : "project";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> safe(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if ("internalId".equals(key)) return;
            if (value instanceof Map<?, ?> map) {
                result.put(key, safe((Map<String, Object>) map));
            } else if (value instanceof List<?> list) {
                result.put(key, list.stream().map(item -> item instanceof Map<?, ?> map
                        ? safe((Map<String, Object>) map) : item).toList());
            } else {
                result.put(key, value);
            }
        });
        return result;
    }

    private List<Map<String, Object>> safeList(List<Map<String, Object>> source) {
        return source.stream().map(this::safe).toList();
    }
}
