package com.salarytracker.task;

import java.time.LocalDate;

public interface TaskRecurrenceCalendar {
    boolean isStatutoryHoliday(LocalDate date);

    boolean isLegalWorkday(LocalDate date);

    boolean isLunarDate(LocalDate date, int month, int day);

    int lunarMonthIndex(LocalDate date);

    int lunarYear(LocalDate date);
}
