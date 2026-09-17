package com.salarytracker.ledger;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerScheduleCalculatorTest {
    @Test
    void calculatesFixedWeeklyDates() {
        Map<String, Object> rule = Map.of("dayOfWeek", 5);
        LocalDate first = LedgerScheduleCalculator.firstDate(LocalDate.of(2026, 9, 16), "CALENDAR", "WEEKLY", 1, rule);
        assertEquals(LocalDate.of(2026, 9, 18), first);
        assertEquals(LocalDate.of(2026, 9, 25), LedgerScheduleCalculator.nextDate(first, "CALENDAR", "WEEKLY", 1, rule));
    }

    @Test
    void clampsMonthlyDayToMonthEnd() {
        Map<String, Object> rule = Map.of("monthlyMode", "DAY_OF_MONTH", "dayOfMonth", 31);
        assertEquals(LocalDate.of(2027, 2, 28), LedgerScheduleCalculator.firstDate(
                LocalDate.of(2027, 2, 1), "CALENDAR", "MONTHLY", 1, rule));
    }

    @Test
    void calculatesNthWeekdayAndSkipsMonthsWithoutFifthOccurrence() {
        Map<String, Object> secondTuesday = Map.of("monthlyMode", "NTH_WEEKDAY", "weekOfMonth", 2, "dayOfWeek", 2);
        assertEquals(LocalDate.of(2026, 9, 8), LedgerScheduleCalculator.firstDate(
                LocalDate.of(2026, 9, 1), "CALENDAR", "MONTHLY", 1, secondTuesday));
        Map<String, Object> fifthMonday = Map.of("monthlyMode", "NTH_WEEKDAY", "weekOfMonth", 5, "dayOfWeek", 1);
        assertEquals(LocalDate.of(2026, 11, 30), LedgerScheduleCalculator.firstDate(
                LocalDate.of(2026, 9, 1), "CALENDAR", "MONTHLY", 1, fifthMonday));
    }

    @Test
    void calculatesFixedYearlyDateAndKeepsIntervalMode() {
        Map<String, Object> yearly = Map.of("month", 12, "dayOfMonth", 20);
        assertEquals(LocalDate.of(2026, 12, 20), LedgerScheduleCalculator.firstDate(
                LocalDate.of(2026, 9, 16), "CALENDAR", "YEARLY", 1, yearly));
        assertEquals(LocalDate.of(2026, 9, 30), LedgerScheduleCalculator.nextDate(
                LocalDate.of(2026, 9, 16), "INTERVAL", "DAILY", 14, Map.of()));
    }

    @Test
    void clampsLeapDayAndReturnsToLeapDayInTheNextLeapYear() {
        Map<String, Object> leapDay = Map.of("month", 2, "dayOfMonth", 29);
        LocalDate first = LedgerScheduleCalculator.firstDate(
                LocalDate.of(2027, 1, 1), "CALENDAR", "YEARLY", 1, leapDay);
        assertEquals(LocalDate.of(2027, 2, 28), first);
        assertEquals(LocalDate.of(2028, 2, 29), LedgerScheduleCalculator.nextDate(
                first, "CALENDAR", "YEARLY", 1, leapDay));
    }

    @Test
    void rejectsIncompleteCalendarRules() {
        assertThrows(IllegalArgumentException.class, () -> LedgerScheduleCalculator.validate(
                "CALENDAR", "MONTHLY", 1, Map.of("monthlyMode", "NTH_WEEKDAY", "weekOfMonth", 2)));
        assertThrows(IllegalArgumentException.class, () -> LedgerScheduleCalculator.validate(
                "CALENDAR", "ONCE", 1, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> LedgerScheduleCalculator.validate(
                "INTERVAL", "DAILY", 0, Map.of()));
    }
}
