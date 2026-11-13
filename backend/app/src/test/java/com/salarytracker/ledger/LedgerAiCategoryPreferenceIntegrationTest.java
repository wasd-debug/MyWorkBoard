package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.ai.LlmGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static com.salarytracker.ledger.LedgerModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LedgerAiCategoryPreferenceIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void migratesV64AndScopesRuleCrudAndRevisionsToCurrentMember() {
        Fixture fixture = fixture("rules");
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='64' AND success=TRUE", Long.class));
        AiCategoryRule first = fixture.inTransaction(() -> fixture.rules.create(fixture.bookId,
                new AiCategoryRuleCommand("咖啡", fixture.mealId, true)));
        assertEquals(1, first.revision());
        assertEquals(CategoryKind.EXPENSE, first.kind());
        AiCategoryRule updated = fixture.inTransaction(() -> fixture.rules.update(fixture.bookId, first.id(),
                new AiCategoryRuleCommand(null, fixture.otherId, false), "1"));
        assertEquals(2, updated.revision());
        assertFalse(updated.enabled());
        assertEquals(fixture.otherId, updated.categoryId());
        assertThrows(ConflictException.class, () -> fixture.inTransaction(() -> fixture.rules.update(fixture.bookId, first.id(),
                new AiCategoryRuleCommand(null, null, true), "1")));
        assertThrows(ConflictException.class, () -> fixture.inTransaction(() -> fixture.rules.delete(fixture.bookId, first.id(), "1")));
        assertEquals(List.of(updated), fixture.rules.list(fixture.bookId));

        long member = addMember(fixture);
        authenticate(member, "member");
        assertTrue(fixture.rules.list(fixture.bookId).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> fixture.rules.update(fixture.bookId, first.id(),
                new AiCategoryRuleCommand(null, null, true), "2")));
        assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> fixture.rules.delete(fixture.bookId, first.id(), "2")));
        AiCategoryRule memberRule = fixture.inTransaction(() -> fixture.rules.create(fixture.bookId,
                new AiCategoryRuleCommand("咖啡", fixture.mealId, true)));
        assertNotEquals(first.id(), memberRule.id());
        authenticate(fixture.userId, "owner");
        assertEquals(List.of(first.id()), fixture.rules.list(fixture.bookId).stream().map(AiCategoryRule::id).toList());
        assertEquals(1, fixture.inTransaction(() -> fixture.rules.delete(fixture.bookId, first.id(), "2")).deleted());
        assertTrue(fixture.rules.list(fixture.bookId).isEmpty());
        authenticate(member, "member");
        assertEquals(List.of(memberRule.id()), fixture.rules.list(fixture.bookId).stream().map(AiCategoryRule::id).toList());
    }

    @Test
    void changedCertainCategoryRequiresConfirmedReviewBeforeAnyWrite() {
        Fixture fixture = fixture("review");
        AiPreview preview = fixture.preview("拿铁", "Coffee", fixture.otherId);
        AiDraft draft = preview.drafts().get(0);
        assertFalse(draft.categoryReviewRequired());
        TransactionCommand changed = fixture.command(draft, fixture.mealId);
        for (AiConfirmCommand command : List.of(new AiConfirmCommand(List.of(changed)),
                new AiConfirmCommand(List.of(changed), List.of(new AiCategoryReview(draft.id(), false, null))))) {
            assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> fixture.ai.confirm(
                    fixture.bookId, preview.draftId(), command, "requires-review")));
            assertEquals(0L, fixture.count("ledger_transaction"));
            assertEquals(0L, fixture.count("ledger_ai_category_feedback"));
            assertEquals(0L, fixture.count("ledger_ai_category_rule"));
            assertEquals("PREVIEW", jdbc.queryForObject("SELECT status FROM ledger_ai_draft WHERE id=?", String.class, preview.draftId()));
        }
        AiConfirm confirmed = fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId, preview.draftId(),
                new AiConfirmCommand(List.of(changed), List.of(new AiCategoryReview(draft.id(), true, null))), "requires-review"));
        assertEquals(fixture.mealId, confirmed.created().get(0).categoryId());
        assertEquals(1L, fixture.count("ledger_ai_category_feedback"));
    }

    @Test
    void manualOverrideClearsOnlyCategoryWarningsInRealPreview() {
        Fixture fixture = fixture("warnings");
        AiDraft unmatched = fixture.preview("豆浆早餐", "便利店", "missing-category").drafts().get(0);
        assertTrue(unmatched.categoryReviewRequired());
        assertTrue(unmatched.warnings().stream().anyMatch(warning -> warning.contains("分类")));
        assertTrue(unmatched.warnings().stream().anyMatch(warning -> warning.contains("商家")));
        fixture.inTransaction(() -> fixture.rules.create(fixture.bookId,
                new AiCategoryRuleCommand("早餐", fixture.mealId, true)));
        AiDraft matched = fixture.preview("豆浆早餐", "便利店", "missing-category").drafts().get(0);
        assertEquals(fixture.mealId, matched.categoryId());
        assertEquals("MANUAL", matched.categorySource());
        assertFalse(matched.categoryReviewRequired());
        assertFalse(matched.warnings().stream().anyMatch(warning -> warning.contains("分类")));
        assertTrue(matched.warnings().stream().anyMatch(warning -> warning.contains("商家")));
    }

    @Test
    void confirmationLearnsExactFingerprintAndReplaysWithoutDuplicateWrites() {
        Fixture fixture = fixture("feedback");
        AiPreview preview = fixture.preview("拿铁", "Coffee", fixture.otherId);
        AiDraft original = preview.drafts().get(0);
        AiConfirmCommand command = new AiConfirmCommand(List.of(fixture.command(original, fixture.mealId)),
                List.of(new AiCategoryReview(original.id(), true, null)));
        AiConfirm confirmed = fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId, preview.draftId(), command, "confirm-exact"));
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM ledger_ai_draft WHERE id=?", String.class, preview.draftId()));
        assertEquals(1, fixture.rules.list(fixture.bookId).get(0).revision());
        AiConfirm replay = fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId, preview.draftId(), command, "confirm-exact"));
        jdbc.update("UPDATE ledger_ai_draft SET expires_at=CURRENT_TIMESTAMP-INTERVAL 1 DAY WHERE id=?", preview.draftId());
        AiConfirm expiredReplay = fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId, preview.draftId(), null, "another-key"));
        assertEquals(confirmed.created().stream().map(Transaction::id).toList(), replay.created().stream().map(Transaction::id).toList());
        assertEquals(confirmed.created().stream().map(Transaction::id).toList(), expiredReplay.created().stream().map(Transaction::id).toList());
        assertEquals(1, fixture.rules.list(fixture.bookId).get(0).revision());
        assertEquals(fixture.otherId, jdbc.queryForObject("SELECT c.public_id FROM ledger_ai_category_feedback f " +
                "JOIN ledger_category c ON c.id=f.original_category_id WHERE f.draft_id=?", String.class, original.id()));
        assertEquals(fixture.mealId, jdbc.queryForObject("SELECT c.public_id FROM ledger_ai_category_feedback f " +
                "JOIN ledger_category c ON c.id=f.confirmed_category_id WHERE f.draft_id=?", String.class, original.id()));
        assertEquals(1L, fixture.count("ledger_transaction"));
        assertEquals(1L, fixture.count("ledger_ai_category_feedback"));
        assertEquals(1L, fixture.count("ledger_ai_category_rule"));
        assertEquals("LEARNED", fixture.rules.list(fixture.bookId).get(0).source());
        AiDraft exact = fixture.preview("拿铁", "ＣＯＦＦＥＥ", fixture.otherId).drafts().get(0);
        assertEquals(fixture.mealId, exact.categoryId());
        assertEquals("LEARNED", exact.categorySource());
        assertFalse(exact.categoryReviewRequired());
        assertEquals(fixture.otherId, fixture.preview("拿铁加蛋糕", "Coffee", fixture.otherId).drafts().get(0).categoryId());
        assertEquals(fixture.otherId, fixture.preview("拿铁", "Other", fixture.otherId).drafts().get(0).categoryId());
        long member = addMember(fixture);
        authenticate(member, "member");
        assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> fixture.ai.confirm(
                fixture.bookId, preview.draftId(), command, "confirm-exact")));
        assertEquals(fixture.otherId, fixture.preview("拿铁", "Coffee", fixture.otherId).drafts().get(0).categoryId());
    }

    @Test
    void invalidRememberKeywordRollsBackTransactionFeedbackAndDraftStatus() {
        Fixture fixture = fixture("rollback");
        AiPreview preview = fixture.preview("豆浆早餐", "便利店", fixture.otherId, 2);
        AiDraft first = preview.drafts().get(0);
        AiDraft second = preview.drafts().get(1);
        long syncBefore = fixture.count("ledger_sync_oplog");
        long auditBefore = fixture.count("ledger_audit_log");
        assertThrows(IllegalArgumentException.class, () -> fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId,
                preview.draftId(), new AiConfirmCommand(List.of(fixture.command(first, fixture.mealId), fixture.command(second, fixture.mealId)),
                        List.of(new AiCategoryReview(first.id(), true, null), new AiCategoryReview(second.id(), true, "不在原文"))), "confirm-rollback")));
        assertEquals(syncBefore, fixture.count("ledger_sync_oplog"));
        assertEquals(auditBefore, fixture.count("ledger_audit_log"));
        assertEquals(0L, fixture.count("ledger_transaction"));
        assertEquals(0L, fixture.count("ledger_ai_category_feedback"));
        assertEquals(0L, fixture.count("ledger_ai_category_rule"));
        assertEquals("PREVIEW", jdbc.queryForObject("SELECT status FROM ledger_ai_draft WHERE id=?", String.class, preview.draftId()));
        AiConfirm retry = fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId, preview.draftId(),
                new AiConfirmCommand(List.of(fixture.command(first, fixture.mealId), fixture.command(second, fixture.mealId)),
                        List.of(new AiCategoryReview(first.id(), true, null), new AiCategoryReview(second.id(), true, null))), "confirm-rollback"));
        assertEquals(2, retry.created().size());
        assertEquals(2L, fixture.count("ledger_transaction"));
        assertEquals(2L, fixture.count("ledger_ai_category_feedback"));
        assertEquals(2L, fixture.count("ledger_ai_category_rule"));
    }

    @Test
    void explicitRememberReenablesManualRuleButAutomaticLearningRespectsDisabledRules() {
        Fixture fixture = fixture("disabled");
        AiCategoryRule manual = fixture.inTransaction(() -> fixture.rules.create(fixture.bookId,
                new AiCategoryRuleCommand("早餐", fixture.otherId, false)));
        AiPreview preview = fixture.preview("豆浆早餐", "便利店", fixture.otherId);
        AiDraft draft = preview.drafts().get(0);
        fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId, preview.draftId(), new AiConfirmCommand(
                List.of(fixture.command(draft, fixture.mealId)), List.of(new AiCategoryReview(draft.id(), true, "早餐"))), "manual-remember"));
        AiCategoryRule remembered = fixture.rules.list(fixture.bookId).get(0);
        assertEquals(manual.id(), remembered.id());
        assertTrue(remembered.enabled());
        assertEquals(fixture.mealId, remembered.categoryId());
        fixture.inTransaction(() -> fixture.rules.delete(fixture.bookId, manual.id(), String.valueOf(remembered.revision())));
        AiPreview learnedPreview = fixture.preview("豆浆早餐", "便利店", fixture.otherId);
        AiDraft learnedDraft = learnedPreview.drafts().get(0);
        fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId, learnedPreview.draftId(), new AiConfirmCommand(
                List.of(fixture.command(learnedDraft, fixture.mealId)), List.of(new AiCategoryReview(learnedDraft.id(), true, null))), "auto-remember"));
        AiCategoryRule learned = fixture.rules.list(fixture.bookId).get(0);
        fixture.inTransaction(() -> fixture.rules.update(fixture.bookId, learned.id(), new AiCategoryRuleCommand(null, null, false), "1"));
        AiPreview correction = fixture.preview("豆浆早餐", "便利店", fixture.otherId);
        AiDraft correctionDraft = correction.drafts().get(0);
        fixture.inTransaction(() -> fixture.ai.confirm(fixture.bookId, correction.draftId(), new AiConfirmCommand(
                List.of(fixture.command(correctionDraft, fixture.mealId)), List.of(new AiCategoryReview(correctionDraft.id(), true, null))), "auto-disabled"));
        assertFalse(fixture.rules.list(fixture.bookId).get(0).enabled());
    }

    private Fixture fixture(String prefix) {
        long userId = createUser(prefix);
        authenticate(userId, prefix);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LedgerBookAccess access = new LedgerBookAccess(jdbc, new CurrentUserResolver());
        String bookId = access.ensureDefaultBook();
        LedgerAuditService audit = new LedgerAuditService(jdbc, mapper, access);
        LedgerBookService books = new LedgerBookService(jdbc, mapper, access, audit);
        LedgerTransactionService transactions = new LedgerTransactionService(jdbc, mapper, access, books, audit);
        LedgerAiCategoryRuleService rules = new LedgerAiCategoryRuleService(jdbc, access, books);
        LlmGateway gateway = mock(LlmGateway.class);
        when(gateway.configured()).thenReturn(true);
        LedgerAiService ai = new LedgerAiService(jdbc, mapper, gateway, access, books, transactions, rules);
        Account cash = books.createAccount(bookId, new AccountCommand(null, "现金", "wallet", "cash", "CNY", BigDecimal.ZERO, false), prefix + "-cash");
        Category parent = books.createCategory(bookId, new CategoryCommand(null, "餐饮", "food", CategoryKind.EXPENSE, null, "", false), prefix + "-parent");
        Category meals = books.createCategory(bookId, new CategoryCommand(null, "早餐", "food", CategoryKind.EXPENSE, parent.id(), "", false), prefix + "-meal");
        Category other = books.createCategory(bookId, new CategoryCommand(null, "其他", "food", CategoryKind.EXPENSE, parent.id(), "", false), prefix + "-other");
        return new Fixture(userId, bookId, cash.id(), meals.id(), other.id(), rules, ai, gateway, mapper,
                new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())));
    }

    private long addMember(Fixture fixture) {
        long memberId = createUser("member");
        long bookId = jdbc.queryForObject("SELECT id FROM ledger_book WHERE public_id=?", Long.class, fixture.bookId);
        long roleId = jdbc.queryForObject("SELECT id FROM ledger_role WHERE book_id=? AND code='MEMBER'", Long.class, bookId);
        jdbc.update("INSERT INTO ledger_book_member(public_id,book_id,user_id,role_id,created_by) VALUES(?,?,?,?,?)",
                UUID.randomUUID().toString(), bookId, memberId, roleId, fixture.userId);
        return memberId;
    }

    private long createUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?,'!',?)", username, prefix);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private void authenticate(long id, String name) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(id, name, name, Set.of("ledger:read", "ledger:write")), null, List.of()));
    }

    private record Fixture(long userId, String bookId, String accountId, String mealId, String otherId,
                           LedgerAiCategoryRuleService rules, LedgerAiService ai, LlmGateway gateway,
                           ObjectMapper mapper, TransactionTemplate tx) {
        <T> T inTransaction(Supplier<T> operation) {
            return tx.execute(status -> {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return operation.get();
            });
        }
        AiPreview preview(String note, String merchant, String categoryId) { return preview(note, merchant, categoryId, 1); }
        AiPreview preview(String note, String merchant, String categoryId, int count) {
            try {
                List<java.util.Map<String, Object>> drafts = java.util.stream.IntStream.range(0, count)
                        .mapToObj(index -> java.util.Map.<String, Object>of("kind", "EXPENSE", "amount", "12",
                                "categoryId", categoryId, "note", count == 1 ? note : note + index, "merchantName", merchant,
                                "accountId", accountId, "categoryConfidence", .95))
                        .toList();
                String json = mapper.writeValueAsString(java.util.Map.of("transactions", drafts));
                when(gateway.structured(anyString(), eq("preview"), isNull(), isNull())).thenReturn(json);
                return ai.previewText(bookId, "preview");
            } catch (Exception exception) { throw new IllegalStateException(exception); }
        }
        TransactionCommand command(AiDraft draft, String categoryId) {
            return new TransactionCommand(draft.id(), accountId, null, categoryId, null, draft.memberId(), null,
                    draft.kind(), draft.amount(), "CNY", draft.occurredOn(), draft.merchantName(), draft.member(), null,
                    draft.note(), "ai", null, null, null);
        }
        long count(String table) {
            return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE book_id=(SELECT id FROM ledger_book WHERE public_id=?)", Long.class, bookId);
        }
    }
}
