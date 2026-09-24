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

import com.salarytracker.worktime.WorktimeModels.Basis;
import com.salarytracker.worktime.WorktimeModels.MonthlySalary;
import com.salarytracker.worktime.WorktimeModels.LunchRecalculationScope;
import com.salarytracker.worktime.WorktimeModels.LunchUpdate;
import com.salarytracker.worktime.WorktimeModels.RecordCommand;
import com.salarytracker.worktime.WorktimeModels.Settings;
import com.salarytracker.worktime.WorktimeModels.SettingsUpdate;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorktimeCalculationIntegrationTest extends MySqlIntegrationTestSupport {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void appliesMonthlySalaryOverridesAndPreOrPostTaxBasisOnTheServer() {
        WorktimeService service = serviceForNewUser();
        Settings preTax = service.writeSettings(new SettingsUpdate(
                new BigDecimal("20000"), new BigDecimal("12000"), Basis.PRE, "09:00", "18:00", 60,
                new BigDecimal("20"), false,
                Map.of("2026-09", new MonthlySalary(new BigDecimal("16000"), new BigDecimal("10000")))), null);

        WorkRecord monthlyOverride = service.createRecord(record("2026-09-17"), "worktime-monthly-override");
        WorkRecord defaultPreTax = service.createRecord(record("2026-10-01"), "worktime-default-pre");

        assertDecimal("100.00", monthlyOverride.realHourlyWage());
        assertDecimal("125.00", defaultPreTax.realHourlyWage());
        assertEquals("phase0-v3-lunch-snapshot", monthlyOverride.calcVersion());
        assertEquals(60, monthlyOverride.lunchMin());
        assertEquals("Asia/Shanghai", monthlyOverride.timezone());
        assertEquals(1L, monthlyOverride.revision());

        service.writeSettings(new SettingsUpdate(new BigDecimal("20000"), new BigDecimal("12000"), Basis.POST,
                "09:00", "18:00", 60, new BigDecimal("20"), false, null),
                String.valueOf(preTax.revision()));
        WorkRecord postTax = service.createRecord(record("2026-11-02"), "worktime-default-post");
        assertDecimal("75.00", postTax.realHourlyWage());
    }

    @Test
    void recalculatesAllOrSelectedHistoryWithTheNewLunchSnapshot() {
        WorktimeService service = serviceForNewUser();
        Settings initial = service.writeSettings(new SettingsUpdate(
                new BigDecimal("16000"), new BigDecimal("10000"), Basis.POST,
                "09:00", "18:00", 90, new BigDecimal("20"), false, null), null);
        WorkRecord before = service.createRecord(record("2026-09-01"));
        WorkRecord after = service.createRecord(record("2026-09-20"));

        var result = service.updateLunch(new LunchUpdate(60, LunchRecalculationScope.FROM_DATE, "2026-09-15"),
                String.valueOf(initial.revision()));

        WorkRecord unchanged = service.record(before.id());
        WorkRecord recalculated = service.record(after.id());
        assertEquals(1, result.recalculatedRecords());
        assertEquals(90, unchanged.lunchMin());
        assertEquals(60, recalculated.lunchMin());
        assertDecimal("66.67", unchanged.realHourlyWage());
        assertDecimal("62.50", recalculated.realHourlyWage());
        assertEquals("phase0-v3-lunch-snapshot", recalculated.calcVersion());
        assertEquals(2L, recalculated.revision());
    }

    @Test
    void changingLunchWithoutRecalculationKeepsExistingRecordSnapshots() {
        WorktimeService service = serviceForNewUser();
        Settings initial = service.writeSettings(new SettingsUpdate(
                new BigDecimal("16000"), new BigDecimal("10000"), Basis.POST,
                "09:00", "18:00", 90, new BigDecimal("20"), false, null), null);
        WorkRecord existing = service.createRecord(record("2026-09-10"));

        var result = service.updateLunch(new LunchUpdate(60, LunchRecalculationScope.NONE, null),
                String.valueOf(initial.revision()));

        WorkRecord unchanged = service.record(existing.id());
        assertEquals(0, result.recalculatedRecords());
        assertEquals(60, result.settings().lunchMin());
        assertEquals(90, unchanged.lunchMin());
        assertDecimal("66.67", unchanged.realHourlyWage());
        assertEquals(1L, unchanged.revision());
    }

    @Test
    void distinguishesNaturalOffDaysFromStatutoryMakeupWorkdays() {
        WorktimeService service = serviceForNewUser();
        service.writeSettings(new SettingsUpdate(
                new BigDecimal("10000"), new BigDecimal("8000"), Basis.POST,
                "08:30", "17:30", 120, new BigDecimal("20"), false, null), null);

        WorkRecord saturday = service.createRecord(
                new RecordCommand("2026-09-19", "08:30", "22:00", 0, ""));
        WorkRecord makeupSunday = service.createRecord(
                new RecordCommand("2026-09-20", "08:30", "22:00", 0, ""));

        assertEquals(690, saturday.overtimeMin());
        assertEquals(270, makeupSunday.overtimeMin());
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

    private RecordCommand record(String date) {
        return new RecordCommand(date, "09:00", "18:00", 0, "");
    }

    private void assertDecimal(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(String.valueOf(actual))));
    }
}
