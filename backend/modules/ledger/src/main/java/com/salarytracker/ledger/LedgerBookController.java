package com.salarytracker.ledger;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
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

import static com.salarytracker.ledger.LedgerModels.*;

@RestController
@RequestMapping(value = "/api/v1/ledger", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('ledger:read')")
@Tag(name = "Ledger")
public class LedgerBookController {
    private final LedgerBookService books;
    private final LedgerTransactionService transactions;
    private final LedgerAuditService audit;
    private final LedgerImportService imports;
    private final LedgerSyncService sync;
    private final LedgerAiService ai;
    private final LedgerReportAiService reportAi;
    private final LedgerScheduledTaskService scheduledTasks;

    public LedgerBookController(LedgerBookService books, LedgerTransactionService transactions,
                                LedgerAuditService audit, LedgerImportService imports,
                                LedgerSyncService sync, LedgerAiService ai,
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
    @Operation(operationId = "listLedgerBooks")
    public ApiResponse<List<Book>> books() { return ApiResponse.ok(books.books()); }

    @PostMapping(value = "/books", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerBook")
    public ApiResponse<Book> createBook(@RequestBody BookCommand body) {
        return ApiResponse.ok(books.createBook(body));
    }

    @PatchMapping(value = "/books/{bookId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerBook")
    public ApiResponse<Book> updateBook(@PathVariable String bookId, @RequestBody BookCommand body,
                                        @RequestHeader(value = "If-Match", required = false) String revision) {
        return ApiResponse.ok(books.updateBook(bookId, body, revision));
    }

    @DeleteMapping("/books/{bookId}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerBook")
    public ApiResponse<DeletedResource> deleteBook(@PathVariable String bookId,
                                                    @RequestHeader(value = "If-Match", required = false) String revision) {
        return ApiResponse.ok(books.deleteBook(bookId, revision));
    }

    @GetMapping("/books/{bookId}/accounts")
    @Operation(operationId = "listLedgerAccounts")
    public ApiResponse<List<Account>> accounts(@PathVariable String bookId,
                                                @RequestParam(defaultValue = "false") boolean includeHidden) {
        return ApiResponse.ok(books.accounts(bookId, includeHidden));
    }

    @PostMapping(value = "/books/{bookId}/accounts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerAccount")
    public ApiResponse<Account> createAccount(@PathVariable String bookId, @RequestBody AccountCommand body,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.createAccount(bookId, body, opId));
    }

    @PatchMapping(value = "/books/{bookId}/accounts/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerAccount")
    public ApiResponse<Account> updateAccount(@PathVariable String bookId, @PathVariable String id,
                                               @RequestBody AccountCommand body,
                                               @RequestHeader(value = "If-Match", required = false) String revision,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateAccount(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/accounts/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerAccount")
    public ApiResponse<DeletedResource> deleteAccount(@PathVariable String bookId, @PathVariable String id,
                                                       @RequestHeader(value = "If-Match", required = false) String revision,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteAccount(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/categories")
    @Operation(operationId = "listLedgerCategories")
    public ApiResponse<List<Category>> categories(@PathVariable String bookId,
                                                   @RequestParam(defaultValue = "false") boolean includeHidden) {
        return ApiResponse.ok(books.categories(bookId, includeHidden));
    }

    @PostMapping(value = "/books/{bookId}/categories", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerCategory")
    public ApiResponse<Category> createCategory(@PathVariable String bookId, @RequestBody CategoryCommand body,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.createCategory(bookId, body, opId));
    }

    @PatchMapping(value = "/books/{bookId}/categories/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerCategory")
    public ApiResponse<Category> updateCategory(@PathVariable String bookId, @PathVariable String id,
                                                 @RequestBody CategoryCommand body,
                                                 @RequestHeader(value = "If-Match", required = false) String revision,
                                                 @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateCategory(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/categories/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerCategory")
    public ApiResponse<DeletedResource> deleteCategory(@PathVariable String bookId, @PathVariable String id,
                                                        @RequestHeader(value = "If-Match", required = false) String revision,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteCategory(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/{type:merchants|projects}")
    @Operation(operationId = "listLedgerNamedResources")
    public ApiResponse<List<NamedResource>> namedResources(@PathVariable String bookId, @PathVariable String type,
                                                            @RequestParam(defaultValue = "false") boolean includeHidden) {
        return ApiResponse.ok("merchants".equals(type)
                ? books.merchants(bookId, includeHidden) : books.projects(bookId, includeHidden));
    }

    @PostMapping(value = "/books/{bookId}/{type:merchants|projects}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerNamedResource")
    public ApiResponse<NamedResource> createNamedResource(@PathVariable String bookId, @PathVariable String type,
                                                           @RequestBody NamedResourceCommand body,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.createNamedResource(bookId, singular(type), body, opId));
    }

    @PatchMapping(value = "/books/{bookId}/{type:merchants|projects}/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerNamedResource")
    public ApiResponse<NamedResource> updateNamedResource(@PathVariable String bookId, @PathVariable String type,
                                                           @PathVariable String id, @RequestBody NamedResourceCommand body,
                                                           @RequestHeader(value = "If-Match", required = false) String revision,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateNamedResource(bookId, singular(type), id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/{type:merchants|projects}/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerNamedResource")
    public ApiResponse<DeletedResource> deleteNamedResource(@PathVariable String bookId, @PathVariable String type,
                                                             @PathVariable String id,
                                                             @RequestHeader(value = "If-Match", required = false) String revision,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteNamedResource(bookId, singular(type), id, revision, opId));
    }

    @GetMapping("/books/{bookId}/members")
    @Operation(operationId = "listLedgerMembers")
    public ApiResponse<List<Member>> members(@PathVariable String bookId) { return ApiResponse.ok(books.members(bookId)); }

    @PostMapping(value = "/books/{bookId}/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerMember")
    public ApiResponse<Member> addMember(@PathVariable String bookId, @RequestBody MemberCommand body,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.addMember(bookId, body, opId));
    }

    @PatchMapping(value = "/books/{bookId}/members/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerMember")
    public ApiResponse<Member> updateMember(@PathVariable String bookId, @PathVariable String id,
                                             @RequestBody MemberCommand body,
                                             @RequestHeader(value = "If-Match", required = false) String revision,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateMember(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/members/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerMember")
    public ApiResponse<DeletedResource> deleteMember(@PathVariable String bookId, @PathVariable String id,
                                                      @RequestHeader(value = "If-Match", required = false) String revision,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteMember(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/roles")
    @Operation(operationId = "listLedgerRoles")
    public ApiResponse<List<Role>> roles(@PathVariable String bookId) { return ApiResponse.ok(books.roles(bookId)); }

    @PostMapping(value = "/books/{bookId}/roles", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerRole")
    public ApiResponse<Role> createRole(@PathVariable String bookId, @RequestBody RoleCommand body,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.createRole(bookId, body, opId));
    }

    @PatchMapping(value = "/books/{bookId}/roles/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerRole")
    public ApiResponse<Role> updateRole(@PathVariable String bookId, @PathVariable String id,
                                        @RequestBody RoleCommand body,
                                        @RequestHeader(value = "If-Match", required = false) String revision,
                                        @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.updateRole(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/roles/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerRole")
    public ApiResponse<DeletedResource> deleteRole(@PathVariable String bookId, @PathVariable String id,
                                                    @RequestHeader(value = "If-Match", required = false) String revision,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteRole(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/budgets")
    @Operation(operationId = "listLedgerBudgets")
    public ApiResponse<List<Budget>> budgets(@PathVariable String bookId,
                                              @RequestParam(required = false) String month) {
        return ApiResponse.ok(books.budgets(bookId, month));
    }

    @PutMapping(value = "/books/{bookId}/budgets", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "upsertLedgerBudget")
    public ApiResponse<Budget> upsertBudget(@PathVariable String bookId, @RequestBody BudgetCommand body,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.upsertBudget(bookId, body, opId));
    }

    @DeleteMapping("/books/{bookId}/budgets/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerBudget")
    public ApiResponse<DeletedResource> deleteBudget(@PathVariable String bookId, @PathVariable String id,
                                                      @RequestHeader(value = "If-Match", required = false) String revision,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(books.deleteBudget(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/scheduled-tasks")
    @Operation(operationId = "listLedgerScheduledTasks")
    public ApiResponse<List<ScheduledTask>> scheduledTasks(@PathVariable String bookId,
                                                            @RequestParam(defaultValue = "false") boolean includeDeleted) {
        return ApiResponse.ok(scheduledTasks.list(bookId, includeDeleted));
    }

    @PostMapping(value = "/books/{bookId}/scheduled-tasks", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerScheduledTask")
    public ApiResponse<ScheduledTask> createScheduledTask(@PathVariable String bookId,
                                                           @RequestBody ScheduledTaskCommand body) {
        return ApiResponse.ok(scheduledTasks.create(bookId, body));
    }

    @PatchMapping(value = "/books/{bookId}/scheduled-tasks/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerScheduledTask")
    public ApiResponse<ScheduledTask> updateScheduledTask(@PathVariable String bookId, @PathVariable String id,
                                                           @RequestBody ScheduledTaskCommand body,
                                                           @RequestHeader(value = "If-Match", required = false) String revision) {
        return ApiResponse.ok(scheduledTasks.update(bookId, id, body, revision));
    }

    @DeleteMapping("/books/{bookId}/scheduled-tasks/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerScheduledTask")
    public ApiResponse<DeletedResource> deleteScheduledTask(@PathVariable String bookId, @PathVariable String id) {
        return ApiResponse.ok(scheduledTasks.delete(bookId, id));
    }

    @PostMapping("/books/{bookId}/scheduled-tasks/{id}/run")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "runLedgerScheduledTask")
    public ApiResponse<ScheduledTaskRun> runScheduledTask(@PathVariable String bookId, @PathVariable String id) {
        return ApiResponse.ok(scheduledTasks.run(bookId, id));
    }

    @GetMapping("/books/{bookId}/transactions")
    @Operation(operationId = "listLedgerTransactions")
    public ApiResponse<TransactionPage> transactionList(@PathVariable String bookId,
                                                         @ParameterObject TransactionQuery filters) {
        return ApiResponse.ok(transactions.list(bookId, filters));
    }

    @GetMapping("/books/{bookId}/transactions/recent")
    @Operation(operationId = "listRecentLedgerTransactions")
    public ApiResponse<List<Transaction>> recent(@PathVariable String bookId,
                                                  @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(transactions.recent(bookId, limit));
    }

    @GetMapping("/books/{bookId}/overview")
    @Operation(operationId = "getLedgerOverview")
    public ApiResponse<Overview> overview(@PathVariable String bookId,
                                          @RequestParam(required = false) String from,
                                          @RequestParam(required = false) String to) {
        return ApiResponse.ok(transactions.overview(bookId, from, to));
    }

    @PostMapping(value = "/books/{bookId}/transactions", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerTransaction")
    public ApiResponse<Transaction> createTransaction(@PathVariable String bookId,
                                                       @RequestBody TransactionCommand body,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(transactions.create(bookId, body, opId));
    }

    @PatchMapping(value = "/books/{bookId}/transactions/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerTransaction")
    public ApiResponse<Transaction> updateTransaction(@PathVariable String bookId, @PathVariable String id,
                                                       @RequestBody TransactionCommand body,
                                                       @RequestHeader(value = "If-Match", required = false) String revision,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(transactions.update(bookId, id, body, revision, opId));
    }

    @DeleteMapping("/books/{bookId}/transactions/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerTransaction")
    public ApiResponse<DeletedResource> deleteTransaction(@PathVariable String bookId, @PathVariable String id,
                                                           @RequestHeader(value = "If-Match", required = false) String revision,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(transactions.delete(bookId, id, revision, opId));
    }

    @GetMapping("/books/{bookId}/transactions/{id}/history")
    @Operation(operationId = "getLedgerTransactionHistory")
    public ApiResponse<List<TransactionVersion>> history(@PathVariable String bookId, @PathVariable String id) {
        return ApiResponse.ok(transactions.history(bookId, id));
    }

    @PostMapping(value = "/books/{bookId}/transactions/{id}/copy", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "copyLedgerTransaction")
    public ApiResponse<Transaction> copy(@PathVariable String bookId, @PathVariable String id,
                                         @RequestBody(required = false) CopyTransactionCommand body) {
        return ApiResponse.ok(transactions.copy(bookId, id, body));
    }

    @GetMapping("/books/{bookId}/recycle")
    @Operation(operationId = "listLedgerRecycle")
    public ApiResponse<RecyclePage> recycle(@PathVariable String bookId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(books.recycle(bookId, page, pageSize));
    }

    @PostMapping("/books/{bookId}/recycle/{type}/{id}/restore")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "restoreLedgerRecycleItem")
    public ApiResponse<RestoreView> restore(@PathVariable String bookId, @PathVariable String type,
                                            @PathVariable String id,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok("transaction".equals(type)
                ? transactions.restore(bookId, id, opId) : books.restoreResource(bookId, type, id, opId));
    }

    @DeleteMapping("/books/{bookId}/recycle/{type}/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "purgeLedgerRecycleItem")
    public ApiResponse<DeletedResource> purge(@PathVariable String bookId, @PathVariable String type,
                                              @PathVariable String id) {
        return ApiResponse.ok("transaction".equals(type)
                ? transactions.purge(bookId, id) : books.purgeResource(bookId, type, id));
    }

    @GetMapping("/books/{bookId}/audit-logs")
    @Operation(operationId = "listLedgerAuditLogs")
    public ApiResponse<AuditPage> auditLogs(@PathVariable String bookId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(audit.list(bookId, page, pageSize));
    }

    @DeleteMapping(value = "/books/{bookId}/audit-logs", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "clearLedgerAuditLogs")
    public ApiResponse<DeleteCount> clearAuditLogs(@PathVariable String bookId,
                                                    @RequestBody(required = false) AuditClearCommand body) {
        return ApiResponse.ok(audit.clear(bookId, body == null ? List.of() : body.ids()));
    }

    @PostMapping("/books/{bookId}/materialize")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "materializeLedgerBalances")
    public ApiResponse<MaterializeResult> materialize(@PathVariable String bookId,
                                                       @RequestParam(required = false) String month) {
        String target = month == null || month.isBlank() ? YearMonth.now().minusMonths(1).toString() : month;
        return ApiResponse.ok(new MaterializeResult(target, transactions.materialize(bookId, target)));
    }

    @PostMapping(value = "/books/{bookId}/imports/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ledger:import')")
    @Operation(operationId = "previewLedgerImport")
    public ApiResponse<ImportPreview> importPreview(@PathVariable String bookId,
                                                     @RequestPart("file") MultipartFile file,
                                                     @RequestParam(required = false) String template) throws Exception {
        return ApiResponse.ok(imports.preview(bookId, file, template));
    }

    @PostMapping("/books/{bookId}/imports/{batchId}/confirm")
    @PreAuthorize("hasAuthority('ledger:import')")
    @Operation(operationId = "confirmLedgerImport")
    public ApiResponse<ImportConfirm> importConfirm(@PathVariable String bookId, @PathVariable String batchId) {
        return ApiResponse.ok(imports.confirm(bookId, batchId));
    }

    @GetMapping(value = "/books/{bookId}/export", produces = {
            "text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})
    @Operation(operationId = "exportLedgerTransactions")
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

    @PostMapping(value = "/books/{bookId}/sync/push", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "pushLedgerSync")
    public ApiResponse<SyncPushResponse> syncPush(@PathVariable String bookId,
                                                   @RequestBody List<SyncOperationRequest> operations) {
        return ApiResponse.ok(sync.push(bookId, operations));
    }

    @GetMapping("/books/{bookId}/sync/pull")
    @Operation(operationId = "pullLedgerSync")
    public ApiResponse<SyncPullResponse> syncPull(@PathVariable String bookId,
                                                   @RequestParam(defaultValue = "0") long cursor,
                                                   @RequestParam(defaultValue = "200") int limit) {
        return ApiResponse.ok(sync.pull(bookId, cursor, limit));
    }

    @PostMapping(value = "/books/{bookId}/ai/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "previewLedgerAiText")
    public ApiResponse<AiPreview> aiPreview(@PathVariable String bookId, @RequestBody AiPreviewCommand body) {
        return ApiResponse.ok(ai.previewText(bookId, body.text()));
    }

    @PostMapping(value = "/books/{bookId}/ai/monthly-analysis", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "analyzeLedgerMonth")
    public ApiResponse<MonthlyAnalysis> aiMonthlyAnalysis(@PathVariable String bookId,
                                                           @RequestBody MonthlyAnalysisCommand body) {
        return ApiResponse.ok(reportAi.analyzeMonth(bookId, body));
    }

    @PostMapping(value = "/books/{bookId}/ai/image-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "previewLedgerAiImage")
    public ApiResponse<AiPreview> aiImagePreview(@PathVariable String bookId,
                                                  @RequestPart("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(ai.previewImage(bookId, file));
    }

    @PostMapping(value = "/books/{bookId}/ai/{draftId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "confirmLedgerAiDraft")
    public ApiResponse<AiConfirm> aiConfirm(@PathVariable String bookId, @PathVariable String draftId,
                                            @RequestBody AiConfirmCommand body,
                                            @RequestHeader(value = "Idempotency-Key", required = false) String opId) {
        return ApiResponse.ok(ai.confirm(bookId, draftId, body, opId));
    }

    private String singular(String type) { return "merchants".equals(type) ? "merchant" : "project"; }
}
