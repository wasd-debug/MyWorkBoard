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
    }
}
