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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

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

    @Test
    void addingRegisteredUserCreatesMembershipInOwnersBook() {
        when(jdbc.queryForList(
                "SELECT id FROM app_user WHERE username=? AND status='ACTIVE' FOR UPDATE", "registered"))
                .thenReturn(List.of(Map.of("id", 42L)));
        when(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_book_member WHERE book_id=? AND user_id=? AND deleted=FALSE",
                Integer.class, 12L, 42L)).thenReturn(0);
        when(jdbc.queryForObject(
                "SELECT id FROM ledger_role WHERE book_id=? AND code='MEMBER'", Long.class, 12L)).thenReturn(8L);
        when(jdbc.queryForMap(contains("WHERE m.user_id=? AND m.book_id=?"), eq(42L), eq(12L)))
                .thenReturn(Map.ofEntries(
                        Map.entry("public_id", "new-member"), Map.entry("user_id", 42L),
                        Map.entry("created_by", 3L), Map.entry("username", "registered"),
                        Map.entry("nickname", "新成员"), Map.entry("role_public_id", "role-member"),
                        Map.entry("role_code", "MEMBER"), Map.entry("role_name", "成员"),
                        Map.entry("icon", "user"), Map.entry("revision", 1L)));

        Map<String, Object> member = books.addMember("book-id", Map.of("username", "registered"), "invitation-op");

        assertEquals(42L, member.get("userId"));
        assertEquals("MEMBER", member.get("roleCode"));
        verify(jdbc).update(contains("INSERT INTO ledger_book_member"), anyString(), eq(12L),
                eq(42L), eq(8L), eq("user"), eq(3L));
    }
}
