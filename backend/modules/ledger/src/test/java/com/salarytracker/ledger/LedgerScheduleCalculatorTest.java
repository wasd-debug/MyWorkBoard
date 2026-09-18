package com.salarytracker.ledger;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import com.salarytracker.ledger.LedgerModels.CalendarRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerScheduleCalculatorTest {
    @Test
    void calculatesFixedWeeklyDates() {
        CalendarRule rule = new CalendarRule(null, null, 5, null, null);
        LocalDate first = LedgerScheduleCalculator.firstDate(LocalDate.of(2026, 9, 16), "CALENDAR", "WEEKLY", 1, rule);
        assertEquals(LocalDate.of(2026, 9, 18), first);
        assertEquals(LocalDate.of(2026, 9, 25), LedgerScheduleCalculator.nextDate(first, "CALENDAR", "WEEKLY", 1, rule));
    }

    @Test
    void clampsMonthlyDayToMonthEnd() {
        CalendarRule rule = new CalendarRule("DAY_OF_MONTH", null, null, 31, null);
        assertEquals(LocalDate.of(2027, 2, 28), LedgerScheduleCalculator.firstDate(
                LocalDate.of(2027, 2, 1), "CALENDAR", "MONTHLY", 1, rule));
    }

    @Test
    void calculatesNthWeekdayAndSkipsMonthsWithoutFifthOccurrence() {
        CalendarRule secondTuesday = new CalendarRule("NTH_WEEKDAY", 2, 2, null, null);
        assertEquals(LocalDate.of(2026, 9, 8), LedgerScheduleCalculator.firstDate(
                LocalDate.of(2026, 9, 1), "CALENDAR", "MONTHLY", 1, secondTuesday));
        CalendarRule fifthMonday = new CalendarRule("NTH_WEEKDAY", 5, 1, null, null);
        assertEquals(LocalDate.of(2026, 11, 30), LedgerScheduleCalculator.firstDate(
                LocalDate.of(2026, 9, 1), "CALENDAR", "MONTHLY", 1, fifthMonday));
    }

    @Test
    void calculatesFixedYearlyDateAndKeepsIntervalMode() {
        CalendarRule yearly = new CalendarRule(null, null, null, 20, 12);
        assertEquals(LocalDate.of(2026, 12, 20), LedgerScheduleCalculator.firstDate(
                LocalDate.of(2026, 9, 16), "CALENDAR", "YEARLY", 1, yearly));
        assertEquals(LocalDate.of(2026, 9, 30), LedgerScheduleCalculator.nextDate(
                LocalDate.of(2026, 9, 16), "INTERVAL", "DAILY", 14, null));
    }

    @Test
    void clampsLeapDayAndReturnsToLeapDayInTheNextLeapYear() {
        CalendarRule leapDay = new CalendarRule(null, null, null, 29, 2);
        LocalDate first = LedgerScheduleCalculator.firstDate(
                LocalDate.of(2027, 1, 1), "CALENDAR", "YEARLY", 1, leapDay);
        assertEquals(LocalDate.of(2027, 2, 28), first);
        assertEquals(LocalDate.of(2028, 2, 29), LedgerScheduleCalculator.nextDate(
                first, "CALENDAR", "YEARLY", 1, leapDay));
    }

    @Test
    void rejectsIncompleteCalendarRules() {
        assertThrows(IllegalArgumentException.class, () -> LedgerScheduleCalculator.validate(
                "CALENDAR", "MONTHLY", 1, new CalendarRule("NTH_WEEKDAY", 2, null, null, null)));
        assertThrows(IllegalArgumentException.class, () -> LedgerScheduleCalculator.validate(
                "CALENDAR", "ONCE", 1, null));
        assertThrows(IllegalArgumentException.class, () -> LedgerScheduleCalculator.validate(
                "INTERVAL", "DAILY", 0, null));
    }
}
