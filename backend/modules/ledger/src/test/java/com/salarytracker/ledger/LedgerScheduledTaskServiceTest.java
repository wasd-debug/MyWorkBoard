package com.salarytracker.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static com.salarytracker.ledger.LedgerModels.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class LedgerScheduledTaskServiceTest {
    private final LedgerScheduledTaskService service = new LedgerScheduledTaskService(
            mock(JdbcTemplate.class),
            new ObjectMapper().findAndRegisterModules(),
            mock(LedgerBookAccess.class),
            mock(LedgerTransactionService.class));

    @Test
    void keepsMaterializedPayloadWhenExecutingTask() {
        TransactionCommand source = new TransactionCommand(
                null, "account-id", null, null, null, null, null,
                TransactionKind.INCOME, new BigDecimal("12000"), null, null, null,
                null, null, null, null, null, null, null);

        TransactionCommand result = service.payload(source);

        assertEquals(TransactionKind.INCOME, result.kind());
        assertEquals(new BigDecimal("12000"), result.amount());
        assertEquals("account-id", result.accountId());
    }

    @Test
    void parsesStoredJsonPayload() {
        TransactionCommand result = service.payload(
                "{\"kind\":\"EXPENSE\",\"amount\":26,\"accountId\":\"account-id\"}");

        assertEquals(TransactionKind.EXPENSE, result.kind());
        assertEquals(new BigDecimal("26"), result.amount());
        assertEquals("account-id", result.accountId());
    }
}
