package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.platform.ai.LlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static com.salarytracker.ledger.LedgerModels.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerAiServiceTest {
    @Test
    void acceptsCategoryPathReturnedByModel() {
        assertArrayEquals(new String[]{"餐饮", "早餐"},
                LedgerAiService.categoryReference("餐饮 / 早餐", ""));
        assertArrayEquals(new String[]{"餐饮", "早餐"},
                LedgerAiService.categoryReference("早餐", "餐饮"));
    }

    @Test
    void keepsUnqualifiedCategoryNameForAmbiguityResolution() {
        assertArrayEquals(new String[]{"", "交通"},
                LedgerAiService.categoryReference("交通", ""));
    }

    @Test
    void categoryRulesUseNormalizedContainsAndManualLongestPriority() {
        var learned = new LedgerAiCategoryRuleService.MatchRule("learned-id", CategoryKind.EXPENSE,
                "LEARNED", LedgerAiCategoryRuleService.fingerprint("麦当劳午餐", "麦当劳"));
        var general = new LedgerAiCategoryRuleService.MatchRule("general-id", CategoryKind.EXPENSE,
                "MANUAL", LedgerAiCategoryRuleService.normalize("午餐"));
        var specific = new LedgerAiCategoryRuleService.MatchRule("specific-id", CategoryKind.EXPENSE,
                "MANUAL", LedgerAiCategoryRuleService.normalize("麦当劳午餐"));

        var match = LedgerAiCategoryRuleService.match(List.of(learned, general, specific),
                TransactionKind.EXPENSE, "麦当劳午餐", "麦当劳");

        assertEquals("specific-id", match.categoryId());
        assertEquals("MANUAL", match.source());
        assertEquals(false, match.conflict());
    }

    @Test
    void conflictingRulesAtSamePriorityRemainUncertain() {
        var first = new LedgerAiCategoryRuleService.MatchRule("first", CategoryKind.INCOME,
                "MANUAL", LedgerAiCategoryRuleService.normalize("兼职收入"));
        var second = new LedgerAiCategoryRuleService.MatchRule("second", CategoryKind.INCOME,
                "MANUAL", LedgerAiCategoryRuleService.normalize("兼职收入"));

        var match = LedgerAiCategoryRuleService.match(List.of(first, second),
                TransactionKind.INCOME, "兼职收入", "");

        assertEquals(null, match.categoryId());
        assertEquals(true, match.conflict());
    }

    @Test
    void defaultsAiDraftMemberToCurrentUser() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LlmGateway gateway = mock(LlmGateway.class);
        LedgerBookAccess access = mock(LedgerBookAccess.class);
        LedgerBookService books = mock(LedgerBookService.class);
        LedgerBookAccess.Context context = new LedgerBookAccess.Context(
                12L, "book-id", 3L, 3L, 4L, 5L, "OWNER", Set.of("TRANSACTION_OWN_WRITE"));
        when(access.resolve("book-id")).thenReturn(context);
        when(gateway.configured()).thenReturn(false);
        when(books.accounts("book-id", false)).thenReturn(List.of());
        when(books.categories("book-id", false)).thenReturn(List.of());
        when(books.merchants("book-id", false)).thenReturn(List.of());
        when(books.projects("book-id", false)).thenReturn(List.of());
        when(books.members("book-id")).thenReturn(List.of(new Member(
                "member-id", 3L, null, "tester", null, "当前用户", null,
                "OWNER", "账本主人", "user", 1L, null)));
        LedgerAiService service = new LedgerAiService(jdbc, new ObjectMapper().findAndRegisterModules(), gateway, access,
                books, mock(LedgerTransactionService.class));

        AiPreview preview = service.previewText("book-id", "今天买冰淇淋花了26元");
        AiDraft draft = preview.drafts().get(0);

        assertEquals("member-id", draft.memberId());
        assertEquals("当前用户", draft.member());
        assertEquals(true, draft.categoryReviewRequired());
        assertEquals("LOCAL", draft.categorySource());
    }

    @Test
    void modelConfidenceMustBeValidAndAtLeastPointEightyFive() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LlmGateway gateway = mock(LlmGateway.class);
        LedgerBookAccess access = mock(LedgerBookAccess.class);
        LedgerBookService books = mock(LedgerBookService.class);
        LedgerBookAccess.Context context = new LedgerBookAccess.Context(12, "book", 7, 7, 1, 1, "OWNER", Set.of());
        when(access.resolve("book")).thenReturn(context);
        when(gateway.configured()).thenReturn(true);
        when(books.categories("book", false)).thenReturn(List.of(
                new Category("parent", "餐饮", "", CategoryKind.EXPENSE, null, "", false, 1, null),
                new Category("breakfast", "早餐", "", CategoryKind.EXPENSE, "parent", "", false, 1, null)));
        var service = new LedgerAiService(jdbc, new ObjectMapper().findAndRegisterModules(), gateway, access, books,
                mock(LedgerTransactionService.class));
        for (String confidence : List.of("null", "\"unknown\"", "0.84", "1.2", "-1")) {
            when(gateway.structured(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("买早餐10元"),
                    org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                    .thenReturn("{\"transactions\":[{\"kind\":\"EXPENSE\",\"amount\":\"10\",\"categoryId\":\"breakfast\",\"categoryConfidence\":" + confidence + "}]}");
            assertEquals(true, service.previewText("book", "买早餐10元").drafts().get(0).categoryReviewRequired());
        }
        when(gateway.structured(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("买早餐10元"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("{\"transactions\":[{\"kind\":\"EXPENSE\",\"amount\":\"10\",\"categoryId\":\"breakfast\",\"categoryConfidence\":0.85}]}");
        assertEquals(false, service.previewText("book", "买早餐10元").drafts().get(0).categoryReviewRequired());
    }

    @Test
    void manualRuleReplacesAllModelCategoryDiagnosticsIncludingPrimaryOnly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LlmGateway gateway = mock(LlmGateway.class);
        LedgerBookAccess access = mock(LedgerBookAccess.class);
        LedgerBookService books = mock(LedgerBookService.class);
        LedgerAiCategoryRuleService rules = mock(LedgerAiCategoryRuleService.class);
        LedgerBookAccess.Context context = new LedgerBookAccess.Context(12, "book", 7, 7, 1, 1, "OWNER", Set.of());
        when(access.resolve("book")).thenReturn(context);
        when(gateway.configured()).thenReturn(true);
        when(books.categories("book", false)).thenReturn(List.of(
                new Category("parent", "餐饮", "", CategoryKind.EXPENSE, null, "", false, 1, null),
                new Category("breakfast", "早餐", "", CategoryKind.EXPENSE, "parent", "", false, 1, null)));
        when(rules.matchingRules(context)).thenReturn(List.of(new LedgerAiCategoryRuleService.MatchRule(
                "breakfast", CategoryKind.EXPENSE, "MANUAL", "早餐")));
        var service = new LedgerAiService(jdbc, new ObjectMapper().findAndRegisterModules(), gateway, access, books,
                mock(LedgerTransactionService.class), rules);
        for (String fields : List.of("\"parentCategoryName\":\"餐饮\"", "\"categoryName\":\"未知\"", "\"categoryName\":\"\"")) {
            when(gateway.structured(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq("早餐10元"),
                    org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
                    .thenReturn("{\"transactions\":[{\"kind\":\"EXPENSE\",\"amount\":\"10\",\"note\":\"早餐\"," + fields + "}]}");
            AiDraft draft = service.previewText("book", "早餐10元").drafts().get(0);
            assertEquals("breakfast", draft.categoryId());
            assertEquals("MANUAL", draft.categorySource());
            assertEquals(false, draft.categoryReviewRequired());
            assertEquals("已匹配个人分类规则", draft.classificationReason());
            assertEquals(false, draft.warnings().stream().anyMatch(warning -> warning.contains("分类")));
        }
    }

    @Test
    void learnedRuleRequiresExactNoteAndPayeeAndDoesNotLearnMerchantAlone() {
        var rule = new LedgerAiCategoryRuleService.MatchRule("meals", CategoryKind.EXPENSE, "LEARNED",
                LedgerAiCategoryRuleService.fingerprint("咖啡拿铁", "Coffee"));
        assertEquals("meals", LedgerAiCategoryRuleService.match(List.of(rule), TransactionKind.EXPENSE,
                "咖啡拿铁", "ＣＯＦＦＥＥ").categoryId());
        assertEquals(null, LedgerAiCategoryRuleService.match(List.of(rule), TransactionKind.EXPENSE,
                "咖啡拿铁和蛋糕", "Coffee"));
        assertEquals(null, LedgerAiCategoryRuleService.match(List.of(rule), TransactionKind.EXPENSE,
                "咖啡拿铁", "其他商家"));
        assertEquals("", LedgerAiCategoryRuleService.fingerprint("", "Coffee"));
    }
}
