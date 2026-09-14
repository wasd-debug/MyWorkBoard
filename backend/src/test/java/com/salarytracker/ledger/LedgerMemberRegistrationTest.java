package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerMemberRegistrationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LedgerBookAccess access = mock(LedgerBookAccess.class);
    private final LedgerBookService books = new LedgerBookService(jdbc, new ObjectMapper(), access,
            mock(LedgerAuditService.class));

    @BeforeEach
    void permittedBook() {
        when(access.resolve("book-id")).thenReturn(new LedgerBookAccess.Context(
                12L, "book-id", 3L, 3L, 4L, 5L, "OWNER", Set.of("MEMBER_MANAGE")));
    }

    @Test
    void rejectsUnknownOrInactiveUsernameBeforeCreatingMember() {
        when(jdbc.queryForList(
                "SELECT id FROM app_user WHERE username=? AND status='ACTIVE' FOR UPDATE", "missing"))
                .thenReturn(List.of());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> books.addMember("book-id", Map.of("username", " missing "), null));

        assertEquals("该用户名未注册或账号不可用，请先确认用户名", error.getMessage());
    }

    @Test
    void rejectsUserAlreadyInThisBook() {
        when(jdbc.queryForList(
                "SELECT id FROM app_user WHERE username=? AND status='ACTIVE' FOR UPDATE", "registered"))
                .thenReturn(List.of(Map.of("id", 42L)));
        when(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_book_member WHERE book_id=? AND user_id=? AND deleted=FALSE",
                Integer.class, 12L, 42L)).thenReturn(1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> books.addMember("book-id", Map.of("username", "registered"), null));

        assertEquals("该用户已是当前账本成员", error.getMessage());
    }
}
