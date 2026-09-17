package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerSharedBookAccessTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CurrentUserResolver currentUser = mock(CurrentUserResolver.class);
    private final LedgerBookAccess access = new LedgerBookAccess(jdbc, currentUser);

    @Test
    void invitedMemberSeesSharedBookWithOwnRoleAndPermissions() {
        when(currentUser.id()).thenReturn(42L);
        when(jdbc.queryForList(anyString(), eq(42L)))
                .thenReturn(List.of(Map.of("public_id", "own-book")));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("WHERE b.public_id=?"),
                eq("own-book"), eq(42L))).thenReturn(List.of(Map.of(
                "book_id", 11L, "public_id", "own-book", "owner_user_id", 42L,
                "member_id", 6L, "role_id", 7L, "role_code", "OWNER")));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(7L))).thenReturn(List.of());
        LedgerBookService books = new LedgerBookService(jdbc, new ObjectMapper(), access,
                mock(LedgerAuditService.class));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("WHERE m.user_id=?"), eq(42L)))
                .thenReturn(List.of(
                        Map.of("public_id", "own-book", "name", "默认账本", "owner_user_id", 42L,
                                "revision", 1L, "role_code", "OWNER", "role_name", "账本主人",
                                "member_count", 1L, "transaction_count", 0L),
                        Map.of("public_id", "shared-book", "name", "家庭账本", "owner_user_id", 3L,
                                "revision", 1L, "role_code", "MEMBER", "role_name", "成员",
                                "role_permissions", "TRANSACTION_OWN_WRITE,RECYCLE_SELF",
                                "member_count", 2L, "transaction_count", 5L)));

        List<Map<String, Object>> visible = books.books();
        assertEquals(List.of("own-book", "shared-book"), visible.stream().map(book -> book.get("id")).toList());
        assertEquals("MEMBER", visible.get(1).get("roleCode"));
        assertEquals(List.of("TRANSACTION_OWN_WRITE", "RECYCLE_SELF"), visible.get(1).get("permissions"));
        verify(jdbc, times(2)).queryForList(org.mockito.ArgumentMatchers.contains("b.owner_user_id=m.user_id"), eq(42L));
    }

    @Test
    void sharedMemberCanWriteOwnTransactionsButNotManageResourcesOrOtherMembersTransactions() {
        when(currentUser.id()).thenReturn(42L);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.contains("WHERE b.public_id=?"),
                eq("shared-book"), eq(42L))).thenReturn(List.of(Map.of(
                "book_id", 12L, "public_id", "shared-book", "owner_user_id", 3L,
                "member_id", 7L, "role_id", 8L, "role_code", "MEMBER")));
        when(jdbc.queryForList(anyString(), eq(String.class), eq(8L)))
                .thenReturn(List.of("TRANSACTION_OWN_WRITE", "RECYCLE_SELF"));

        LedgerBookAccess.Context member = access.resolve("shared-book");
        assertFalse(member.isOwner());
        assertEquals(42L, member.userId());
        access.requireTransactionWrite(member, 42L);
        assertThrows(ForbiddenException.class, () -> access.requireTransactionWrite(member, 3L));
        assertThrows(ForbiddenException.class, () -> access.require(member, "RESOURCE_MANAGE"));
    }

    @Test
    void nonMemberCannotResolveAnotherUsersBook() {
        when(currentUser.id()).thenReturn(99L);
        assertThrows(ForbiddenException.class, () -> access.resolve("shared-book"));
    }
}
