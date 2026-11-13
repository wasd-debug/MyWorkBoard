package com.salarytracker.ledger;

import com.salarytracker.platform.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static com.salarytracker.ledger.LedgerModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LedgerAiCategoryRuleServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LedgerBookAccess access = mock(LedgerBookAccess.class);
    private final LedgerBookService books = mock(LedgerBookService.class);
    private final LedgerBookAccess.Context context = new LedgerBookAccess.Context(12, "book", 7, 2, 1, 1,
            "MEMBER", Set.of("TRANSACTION_OWN_WRITE"));
    private final LedgerAiCategoryRuleService service = new LedgerAiCategoryRuleService(jdbc, access, books);

    private void categories() {
        when(access.resolve("book")).thenReturn(context);
        when(books.categories("book", false)).thenReturn(List.of(
                new Category("parent", "餐饮", "", CategoryKind.EXPENSE, null, "", false, 1, null),
                new Category("breakfast", "早餐", "", CategoryKind.EXPENSE, "parent", "", false, 1, null)));
    }

    @Test
    void creatingRulesUsesCurrentUserInsteadOfBookOwnerAndValidatesSecondaryCategory() {
        categories();
        AiCategoryRule created = service.create("book", new AiCategoryRuleCommand("ＢＲＥＡＫＦＡＳＴ", "breakfast", true));
        assertEquals("breakfast", created.keyword());
        assertEquals(CategoryKind.EXPENSE, created.kind());
        verify(jdbc).update(contains("INSERT INTO ledger_ai_category_rule"), anyString(), eq(7L), eq(12L),
                eq("breakfast"), eq("breakfast"), eq("breakfast"), eq(12L), eq("EXPENSE"), eq(true), eq("MANUAL"));
        assertThrows(IllegalArgumentException.class, () -> service.create("book", new AiCategoryRuleCommand("早餐", "parent", true)));
        assertThrows(IllegalArgumentException.class, () -> service.create("book", new AiCategoryRuleCommand("早餐", "other-book-category", true)));
        assertThrows(IllegalArgumentException.class, () -> service.create("book", new AiCategoryRuleCommand("早", "breakfast", true)));
    }

    @Test
    void updateAndDeleteRequireMatchingRevisionWithinUserAndBook() {
        categories();
        when(jdbc.queryForList(contains("WHERE r.user_id=? AND r.book_id=?"), eq(7L), eq(12L))).thenReturn(List.of(Map.of(
                "public_id", "rule", "keyword", "早餐", "category_id", "breakfast", "kind", "EXPENSE",
                "enabled", true, "source", "MANUAL", "revision", 3L)));
        assertThrows(IllegalArgumentException.class, () -> service.update("book", "rule", new AiCategoryRuleCommand(null, null, false), null));
        assertThrows(ConflictException.class, () -> service.delete("book", "rule", "2"));
        verify(jdbc, never()).update(startsWith("DELETE"), any(Object[].class));
    }

    @Test
    void puttingUnchangedLearnedKeywordPreservesExactFingerprintSource() {
        categories();
        when(jdbc.queryForList(contains("WHERE r.user_id=? AND r.book_id=?"), eq(7L), eq(12L))).thenReturn(List.of(Map.of(
                "public_id", "rule", "keyword", "买豆浆", "category_id", "breakfast", "kind", "EXPENSE",
                "enabled", true, "source", "LEARNED", "revision", 3L)));
        String fingerprint = LedgerAiCategoryRuleService.fingerprint("买豆浆", "便利店");
        when(jdbc.queryForObject(startsWith("SELECT normalized_keyword"), eq(String.class), eq("rule"), eq(7L), eq(12L)))
                .thenReturn(fingerprint);
        when(jdbc.update(startsWith("UPDATE ledger_ai_category_rule"), eq("买豆浆"), eq(fingerprint), eq("breakfast"), eq(12L),
                eq("EXPENSE"), eq(false), eq("LEARNED"), eq("rule"), eq(7L), eq(12L), eq(3L))).thenReturn(1);
        AiCategoryRule updated = service.update("book", "rule", new AiCategoryRuleCommand("买豆浆", "breakfast", false), "3");
        assertEquals("LEARNED", updated.source());
        assertEquals(4, updated.revision());
    }

    @Test
    void correctionIsPersistedAndReusedOnlyForExactFingerprint() throws Exception {
        categories();
        AtomicReference<String> fingerprint = new AtomicReference<>();
        when(jdbc.update(contains("INSERT INTO ledger_ai_category_rule"), anyString(), eq(7L), eq(12L), anyString(),
                anyString(), eq("breakfast"), eq(12L), eq("EXPENSE"), eq(true), eq("LEARNED")))
                .thenAnswer(invocation -> { fingerprint.set(invocation.getArgument(5)); return 1; });
        when(jdbc.queryForList(contains("JOIN ledger_category p"), eq(7L), eq(12L))).thenAnswer(invocation -> List.of(Map.of(
                "category_id", "breakfast", "kind", "EXPENSE", "source", "LEARNED", "normalized_keyword", fingerprint.get())));
        AiDraft original = new AiDraft("draft", TransactionKind.EXPENSE, BigDecimal.TEN, LocalDate.now(), null, null, null,
                null, "wrong", "其他", "其他", "matched", null, "便利店", null, null, null, null, "买豆浆", List.of());
        TransactionCommand confirmed = new TransactionCommand("draft", null, null, "breakfast", null, null, null,
                TransactionKind.EXPENSE, BigDecimal.TEN, null, LocalDate.now(), "便利店", null, null, "买豆浆", null, null, null, null);
        Transaction created = new com.fasterxml.jackson.databind.ObjectMapper().readValue("{\"id\":\"created\"}", Transaction.class);

        service.feedback(context, original, confirmed, created, new AiCategoryReview("draft", true, null));
        var rules = service.matchingRules(context);
        assertEquals("breakfast", LedgerAiCategoryRuleService.match(rules, TransactionKind.EXPENSE, "买豆浆", "便利店").categoryId());
        assertNull(LedgerAiCategoryRuleService.match(rules, TransactionKind.EXPENSE, "买洗衣液", "便利店"));
        verify(jdbc).update(contains("INSERT INTO ledger_ai_category_feedback"), eq(7L), eq(12L), eq("draft"), eq(0L),
                eq("wrong"), eq(12L), eq("breakfast"), eq(12L), isNull());
    }
}
