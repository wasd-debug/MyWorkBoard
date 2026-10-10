package com.salarytracker.calendar;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LunarCalendarAdapterTest {
    private final LunarCalendarAdapter calendar = new LunarCalendarAdapter();

    @Test
    void supportsDocumentedBoundariesAndDegradesOutsideThem() {
        assertNotNull(calendar.day(LocalDate.of(1900, 1, 31)));
        assertNotNull(calendar.day(LocalDate.of(2100, 12, 31)));
        assertNull(calendar.day(LocalDate.of(1899, 12, 31)));
        assertNull(calendar.day(LocalDate.of(2101, 1, 1)));
    }

    @Test
    void exposesLeapMonthAndSolarTermWithoutLeakingLibraryTypes() {
        assertTrue(calendar.day(LocalDate.of(2023, 3, 22)).lunarDate().startsWith("闰二月"));
        assertEquals("寒露", calendar.day(LocalDate.of(2026, 10, 8)).solarTerm());
    }
}
