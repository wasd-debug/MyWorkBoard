package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class LedgerScheduledTaskServiceTest {
    private final LedgerScheduledTaskService service = new LedgerScheduledTaskService(
            mock(JdbcTemplate.class),
            new ObjectMapper(),
            mock(LedgerBookAccess.class),
            mock(LedgerTransactionService.class));

    @Test
    void keepsMaterializedPayloadWhenExecutingTask() {
        Map<String, Object> source = Map.of(
                "kind", "INCOME",
                "amount", 12000,
                "accountId", "account-id");

        Map<String, Object> result = service.payload(source);

        assertEquals("INCOME", result.get("kind"));
        assertEquals(12000, result.get("amount"));
        assertEquals("account-id", result.get("accountId"));
    }

    @Test
    void parsesStoredJsonPayload() {
        Map<String, Object> result = service.payload(
                "{\"kind\":\"EXPENSE\",\"amount\":26,\"accountId\":\"account-id\"}");

        assertEquals("EXPENSE", result.get("kind"));
        assertEquals(26, result.get("amount"));
        assertEquals("account-id", result.get("accountId"));
    }
}
