package com.salarytracker.calendar;

import com.salarytracker.ledger.LedgerTransactionService;
import com.salarytracker.service.HolidayService;
import com.salarytracker.task.TaskModels;
import com.salarytracker.task.TaskService;
import com.salarytracker.worktime.WorktimeModels;
import com.salarytracker.worktime.WorktimeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskCalendarServiceTest {
    @Test
    void degradesOneOverlayWithoutBreakingTasksOrOtherLayers() {
        TaskService tasks = mock(TaskService.class);
        WorktimeService worktime = mock(WorktimeService.class);
        LedgerTransactionService ledger = mock(LedgerTransactionService.class);
        HolidayService holidays = mock(HolidayService.class);
        LunarCalendar lunar = mock(LunarCalendar.class);
        when(tasks.calendarItems(any(Instant.class), any(Instant.class))).thenReturn(List.of(
                new TaskModels.CalendarItem("task-1", "计划", "OPEN", "HIGH", null,
                        "2026-10-10T10:00:00Z", false, "Asia/Shanghai", null, 1)));
        when(tasks.settings()).thenReturn(new TaskModels.Settings("list-1", "INBOX", 1, "Asia/Shanghai", 1));
        when(worktime.calendarItems(any(LocalDate.class), any(LocalDate.class)))
                .thenThrow(new IllegalStateException("worktime unavailable"));
        when(ledger.calendarDailyTotals(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of(
                new com.salarytracker.ledger.LedgerModels.DailyTotal(LocalDate.of(2026, 10, 10),
                        BigDecimal.TEN, BigDecimal.ONE)));
        when(holidays.getHolidays(2026)).thenReturn(new HolidayService.HolidayResponse(true, 2026,
                java.util.Map.of(), HolidayService.HolidaySource.NONE));

        TaskCalendarModels.CalendarResponse result = new TaskCalendarService(tasks, worktime, ledger, holidays,
                lunar, false).calendar(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), null, true);

        assertEquals(1, result.tasks().size());
        assertEquals(BigDecimal.valueOf(9), result.ledger().get(0).net());
        assertFalse(result.layers().get("worktime").available());
        assertTrue(result.layers().get("ledger").available());
        assertTrue(result.lunar().isEmpty());
    }
}
