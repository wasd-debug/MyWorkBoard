package com.salarytracker.worktime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUser;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.integration.MySqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorktimeCalculationIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appliesMonthlySalaryOverridesAndPreOrPostTaxBasisOnTheServer() {
        WorktimeService service = serviceForNewUser();
        Map<String, Object> preTax = service.writeSettings(Map.ofEntries(
                Map.entry("salaryPre", 20000),
                Map.entry("salaryPost", 12000),
                Map.entry("basis", "pre"),
                Map.entry("workStart", "09:00"),
                Map.entry("workEnd", "18:00"),
                Map.entry("lunchMin", 60),
                Map.entry("daysPerMonth", 20),
                Map.entry("autoDays", false),
                Map.entry("salaries", Map.of("2026-09", Map.of("pre", 16000, "post", 10000)))),
                null);

        Map<String, Object> monthlyOverride = service.createRecord(record("2026-09-17"), "worktime-monthly-override");
        Map<String, Object> defaultPreTax = service.createRecord(record("2026-10-01"), "worktime-default-pre");

        assertDecimal("100.00", monthlyOverride.get("realHourlyWage"));
        assertDecimal("125.00", defaultPreTax.get("realHourlyWage"));
        assertEquals("phase0-v1", monthlyOverride.get("calcVersion"));
        assertEquals("Asia/Shanghai", monthlyOverride.get("timezone"));
        assertEquals(1L, monthlyOverride.get("revision"));

        long revision = ((Number) preTax.get("revision")).longValue();
        service.writeSettings(Map.ofEntries(
                Map.entry("salaryPre", 20000),
                Map.entry("salaryPost", 12000),
                Map.entry("basis", "post"),
                Map.entry("workStart", "09:00"),
                Map.entry("workEnd", "18:00"),
                Map.entry("lunchMin", 60),
                Map.entry("daysPerMonth", 20),
                Map.entry("autoDays", false)),
                String.valueOf(revision));
        Map<String, Object> postTax = service.createRecord(record("2026-11-02"), "worktime-default-post");
        assertDecimal("75.00", postTax.get("realHourlyWage"));
    }

    private WorktimeService serviceForNewUser() {
        String username = "worktime-calc-" + UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(username,password_hash,nickname) VALUES(?, '!', 'worktime')", username);
        long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username=?", Long.class, username);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new CurrentUser(userId, username, "worktime", Set.of("worktime:read", "worktime:write")),
                null, List.of()));
        return new WorktimeService(jdbc, new ObjectMapper().findAndRegisterModules(), new CurrentUserResolver());
    }

    private Map<String, Object> record(String date) {
        return Map.of("date", date, "start", "09:00", "end", "18:00", "rest", 0);
    }

    private void assertDecimal(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(String.valueOf(actual))));
    }
}
