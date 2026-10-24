package com.salarytracker.task;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskRecurrenceTest {
    @Test
    void validatesSupportedSubsetAndAdvancesDailySeries() {
        assertEquals("FREQ=DAILY;INTERVAL=1", TaskRecurrence.validate("RRULE:FREQ=DAILY;INTERVAL=1"));
        assertEquals("2026-10-11T09:00:00Z", TaskRecurrence.next("FREQ=DAILY", Instant.parse("2026-10-10T09:00:00Z"),
                Instant.parse("2026-10-10T09:01:00Z"), "DUE_DATE", "UTC", 2).toString());
    }

    @Test
    void completionAnchorWaitsForCompletionAndCountStops() {
        assertEquals("2026-10-12T09:00:00Z", TaskRecurrence.next("FREQ=DAILY", Instant.parse("2026-10-10T09:00:00Z"),
                Instant.parse("2026-10-11T10:30:00Z"), "COMPLETION_DATE", "UTC", 2).toString());
        assertNull(TaskRecurrence.next("FREQ=DAILY;COUNT=1", Instant.parse("2026-10-10T09:00:00Z"),
                Instant.parse("2026-10-10T09:01:00Z"), "DUE_DATE", "UTC", 2));
    }

    @Test
    void rejectsUnsupportedOrMalformedRules() {
        assertThrows(IllegalArgumentException.class, () -> TaskRecurrence.validate("FREQ=HOURLY"));
        assertThrows(IllegalArgumentException.class, () -> TaskRecurrence.validate("FREQ=WEEKLY;BYDAY=XX"));
        assertThrows(IllegalArgumentException.class, () -> TaskRecurrence.validate("FREQ=DAILY;BYHOUR=10"));
        assertThrows(IllegalArgumentException.class, () -> TaskRecurrence.validate("FREQ=MONTHLY;BYSETPOS=0"));
    }

    @Test
    void advancesMonthlyOrdinalWeekdayAndLastWorkdayRules() {
        assertEquals("2026-12-08T09:00:00Z", TaskRecurrence.next("FREQ=MONTHLY;BYDAY=2TU",
                Instant.parse("2026-11-10T09:00:00Z"), Instant.parse("2026-11-10T09:01:00Z"),
                "DUE_DATE", "UTC", 2).toString());
        assertEquals("2026-11-30T09:00:00Z", TaskRecurrence.next(
                "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1",
                Instant.parse("2026-10-30T09:00:00Z"), Instant.parse("2026-10-30T09:01:00Z"),
                "DUE_DATE", "UTC", 2).toString());
    }

    @Test
    void advancesYearlyMonthAndDayAndHonorsCompletionAnchor() {
        assertEquals("2027-08-15T09:00:00Z", TaskRecurrence.next("FREQ=YEARLY;BYMONTH=8;BYMONTHDAY=15",
                Instant.parse("2026-08-15T09:00:00Z"), Instant.parse("2026-08-15T09:01:00Z"),
                "DUE_DATE", "UTC", 2).toString());
        assertEquals("2026-12-08T09:00:00Z", TaskRecurrence.next("FREQ=MONTHLY;BYDAY=2TU",
                Instant.parse("2026-11-10T09:00:00Z"), Instant.parse("2026-11-10T10:00:00Z"),
                "COMPLETION_DATE", "UTC", 2).toString());
    }

    @Test
    void advancesLegalHolidayAndLunarRecurrenceWithCalendarContract() {
        TaskRecurrenceCalendar calendar = new TaskRecurrenceCalendar() {
            @Override public boolean isStatutoryHoliday(LocalDate date) { return date.equals(LocalDate.parse("2026-10-03")); }
            @Override public boolean isLegalWorkday(LocalDate date) { return date.equals(LocalDate.parse("2026-10-10")); }
            @Override public boolean isLunarDate(LocalDate date, int month, int day) {
                return date.equals(LocalDate.parse("2026-11-12")) && day == 8;
            }
            @Override public int lunarMonthIndex(LocalDate date) { return date.getYear() * 12 + (date.getMonthValue() == 11 ? 1 : 0); }
            @Override public int lunarYear(LocalDate date) { return date.getYear(); }
        };
        assertEquals("2026-10-03T09:00:00Z", TaskRecurrence.next("FREQ=DAILY;X-TASK-KIND=LEGAL_HOLIDAY",
                Instant.parse("2026-10-02T09:00:00Z"), Instant.parse("2026-10-02T09:01:00Z"),
                "DUE_DATE", "UTC", 2, calendar).toString());
        assertEquals("2026-10-10T09:00:00Z", TaskRecurrence.next("FREQ=DAILY;X-TASK-KIND=LEGAL_WORKDAY",
                Instant.parse("2026-10-09T09:00:00Z"), Instant.parse("2026-10-09T09:01:00Z"),
                "DUE_DATE", "UTC", 2, calendar).toString());
        assertEquals("2026-11-12T09:00:00Z", TaskRecurrence.next("FREQ=MONTHLY;X-TASK-KIND=LUNAR_MONTHLY;X-LUNAR-DAY=8",
                Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-01T09:01:00Z"),
                "DUE_DATE", "UTC", 2, calendar).toString());
    }

    @Test
    void validatesTaskSpecificRecurrenceExtensions() {
        assertEquals("FREQ=DAILY;X-TASK-KIND=LEGAL_HOLIDAY",
                TaskRecurrence.validate("FREQ=DAILY;X-TASK-KIND=LEGAL_HOLIDAY"));
        assertThrows(IllegalArgumentException.class,
                () -> TaskRecurrence.validate("FREQ=YEARLY;X-TASK-KIND=LUNAR_YEARLY;X-LUNAR-DAY=31"));
    }
}
