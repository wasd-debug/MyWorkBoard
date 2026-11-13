package com.salarytracker.ledger;

import com.salarytracker.platform.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.salarytracker.ledger.LedgerModels.*;

@RestController
@RequestMapping(value = "/api/v1/ledger/books/{bookId}/ai/category-rules", produces = MediaType.APPLICATION_JSON_VALUE)
@PreAuthorize("hasAuthority('ledger:read')")
public class LedgerAiCategoryRuleController {
    private final LedgerAiCategoryRuleService rules;

    public LedgerAiCategoryRuleController(LedgerAiCategoryRuleService rules) { this.rules = rules; }

    @GetMapping
    @Operation(operationId = "listLedgerAiCategoryRules")
    public ApiResponse<List<AiCategoryRule>> list(@PathVariable String bookId) {
        return ApiResponse.ok(rules.list(bookId));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "createLedgerAiCategoryRule")
    public ApiResponse<AiCategoryRule> create(@PathVariable String bookId, @RequestBody AiCategoryRuleCommand body) {
        return ApiResponse.ok(rules.create(bookId, body));
    }

    @RequestMapping(value = "/{id}", method = {RequestMethod.PUT, RequestMethod.PATCH}, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "updateLedgerAiCategoryRule")
    public ApiResponse<AiCategoryRule> update(@PathVariable String bookId, @PathVariable String id,
                                               @RequestBody AiCategoryRuleCommand body,
                                               @RequestHeader(value = "If-Match", required = false) String revision) {
        return ApiResponse.ok(rules.update(bookId, id, body, revision));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ledger:write')")
    @Operation(operationId = "deleteLedgerAiCategoryRule")
    public ApiResponse<DeleteCount> delete(@PathVariable String bookId, @PathVariable String id,
                                            @RequestHeader(value = "If-Match", required = false) String revision) {
        return ApiResponse.ok(rules.delete(bookId, id, revision));
    }
}
