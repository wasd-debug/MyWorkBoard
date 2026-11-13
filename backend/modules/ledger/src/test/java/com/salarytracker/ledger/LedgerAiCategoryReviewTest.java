package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ai.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.salarytracker.ledger.LedgerModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LedgerAiCategoryReviewTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LedgerBookAccess access = mock(LedgerBookAccess.class);
    private final LedgerBookService books = mock(LedgerBookService.class);
    private final LedgerTransactionService transactions = mock(LedgerTransactionService.class);
    private final LedgerAiCategoryRuleService rules = mock(LedgerAiCategoryRuleService.class);
    private final LedgerBookAccess.Context context = new LedgerBookAccess.Context(12, "book", 7, 7, 1, 1, "OWNER", Set.of());
    private final LedgerAiService service = new LedgerAiService(jdbc, mapper, mock(LlmGateway.class), access, books, transactions, rules);

    private AiDraft original(boolean uncertain) {
        return new AiDraft("draft", TransactionKind.EXPENSE, BigDecimal.TEN, LocalDate.now(),
                "account", "现金", null, null, "category", "早餐", "餐饮", "matched", null,
                "便利店", null, null, null, null, "买早餐", List.of(), "MODEL", uncertain ? .5 : .95, uncertain, "依据");
    }

    private TransactionCommand command(String id) {
        return new TransactionCommand(id, "account", null, "category", null, null, null,
                TransactionKind.EXPENSE, BigDecimal.TEN, "CNY", LocalDate.now(), "便利店", null, null,
                "买早餐", null, null, null, null);
    }

    private void stored(AiDraft original, String status) throws Exception {
        when(access.resolve("book")).thenReturn(context);
        when(jdbc.queryForList(contains("FROM ledger_ai_draft"), eq("preview"), eq(12L), eq(7L)))
                .thenReturn(List.of(Map.of("payload_json", mapper.writeValueAsString(new AiStoredDraft(List.of(original), false, List.of())),
                        "status", status)));
    }

    @Test
    void uncertainMatchedCategoryRequiresExplicitReviewBeforeAnyWrite() throws Exception {
        stored(original(true), "PREVIEW");
        assertThrows(IllegalArgumentException.class, () -> service.confirm("book", "preview",
                new AiConfirmCommand(List.of(command("draft"))), "key"));
        verifyNoInteractions(transactions);
        verify(jdbc).queryForList(contains("FOR UPDATE"), eq("preview"), eq(12L), eq(7L));
    }

    @Test
    void wrongAndDuplicateTransactionIdsCannotBypassReview() throws Exception {
        stored(original(true), "PREVIEW");
        var review = List.of(new AiCategoryReview("draft", true, null));
        assertThrows(IllegalArgumentException.class, () -> service.confirm("book", "preview",
                new AiConfirmCommand(List.of(command("other")), review), "key"));
        assertThrows(IllegalArgumentException.class, () -> service.confirm("book", "preview",
                new AiConfirmCommand(List.of(command("draft"), command("draft")), review), "key"));
        verifyNoInteractions(transactions);
    }

    @Test
    void changingHighConfidenceCategoryRequiresExplicitConfirmation() throws Exception {
        stored(original(false), "PREVIEW");
        TransactionCommand changed = new TransactionCommand("draft", "account", null, "other-category", null, null, null,
                TransactionKind.EXPENSE, BigDecimal.TEN, "CNY", LocalDate.now(), "便利店", null, null,
                "买早餐", null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> service.confirm("book", "preview",
                new AiConfirmCommand(List.of(changed)), "key"));
        assertThrows(IllegalArgumentException.class, () -> service.confirm("book", "preview",
                new AiConfirmCommand(List.of(changed), List.of(new AiCategoryReview("draft", false, null))), "key"));
        verifyNoInteractions(transactions);
        Transaction created = mapper.readValue("{\"id\":\"created\"}", Transaction.class);
        when(transactions.create(eq("book"), any(), eq("key-draft"))).thenReturn(created);
        assertEquals(1, service.confirm("book", "preview", new AiConfirmCommand(List.of(changed),
                List.of(new AiCategoryReview("draft", true, null))), "key").created().size());
    }

    @Test
    void confirmedDraftReplaysWithoutCreatingTransactionsOrFeedback() throws Exception {
        stored(original(true), "CONFIRMED");
        assertEquals("CONFIRMED", service.confirm("book", "preview", null, "key").status());
        verifyNoInteractions(transactions, rules);
    }

    @Test
    void explicitReviewRecordsFeedbackAndCertainDraftNeedsNoReview() throws Exception {
        stored(original(true), "PREVIEW");
        Transaction created = mapper.readValue("{\"id\":\"created\"}", Transaction.class);
        when(transactions.create(eq("book"), any(), eq("key-draft"))).thenReturn(created);
        var review = new AiCategoryReview("draft", true, "早餐");
        assertEquals(1, service.confirm("book", "preview",
                new AiConfirmCommand(List.of(command("draft")), List.of(review)), "key").created().size());
        verify(rules).feedback(eq(context), any(), any(), eq(created), eq(review));
        stored(original(false), "PREVIEW");
        assertEquals(1, service.confirm("book", "preview", new AiConfirmCommand(List.of(command("draft"))), "key").created().size());
    }
}
