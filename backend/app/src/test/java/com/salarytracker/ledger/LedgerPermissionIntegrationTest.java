package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import com.salarytracker.platform.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerPermissionIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void preventsAnotherUserFromPullingOrPushingAnUnsharedBook() {
        long ownerId = createUser("owner");
        authenticate(ownerId, "owner");
        CurrentUserResolver currentUser = new CurrentUserResolver();
        LedgerBookAccess access = new LedgerBookAccess(jdbc, currentUser);
        String bookId = access.ensureDefaultBook();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        LedgerAuditService audit = new LedgerAuditService(jdbc, mapper, access);
        LedgerBookService books = new LedgerBookService(jdbc, mapper, access, audit);
        LedgerTransactionService transactions = new LedgerTransactionService(jdbc, mapper, access, books, audit);
        LedgerSyncService sync = new LedgerSyncService(jdbc, mapper, access, books, transactions);

        long outsiderId = createUser("outsider");
        authenticate(outsiderId, "outsider");

        assertThrows(ForbiddenException.class, () -> sync.pull(bookId, 0, 200));
        assertThrows(ForbiddenException.class, () -> sync.push(bookId, List.of()));
    }

    private long createUser(String prefix) {
        String username = prefix + "-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', ?)", username, prefix);
        return jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
    }

    private void authenticate(long userId, String username) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(userId, username, username, Set.of("ledger:read", "ledger:write")), null, List.of()));
    }
}
