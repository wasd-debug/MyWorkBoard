package com.salarytracker.calendar;

import com.nlf.calendar.Solar;
import com.salarytracker.service.HolidayService;
import com.salarytracker.task.TaskRecurrenceCalendar;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskRecurrenceCalendarAdapter implements TaskRecurrenceCalendar {
    private final HolidayService holidays;
    private final Map<Integer, Map<String, HolidayService.HolidayDay>> holidayYears = new ConcurrentHashMap<>();

    public TaskRecurrenceCalendarAdapter(HolidayService holidays) {
        this.holidays = holidays;
    }

    @Override
    public boolean isStatutoryHoliday(LocalDate date) {
        return holiday(date) != null && holiday(date).off();
    }

    @Override
    public boolean isLegalWorkday(LocalDate date) {
        HolidayService.HolidayDay override = holiday(date);
        return override == null ? isWeekday(date) : !override.off();
    }

    @Override
    public boolean isLunarDate(LocalDate date, int month, int day) {
        if (date == null || date.getYear() < 1900 || date.getYear() > 2100) return false;
        var lunar = Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar();
        return (month <= 0 || lunar.getMonth() == month) && lunar.getDay() == day;
    }

    @Override
    public int lunarMonthIndex(LocalDate date) {
        if (date == null || date.getYear() < 1900 || date.getYear() > 2100) return Integer.MIN_VALUE;
        var lunar = Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar();
        return lunar.getYear() * 12 + Math.abs(lunar.getMonth());
    }

    @Override
    public int lunarYear(LocalDate date) {
        if (date == null || date.getYear() < 1900 || date.getYear() > 2100) return Integer.MIN_VALUE;
        return Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth()).getLunar().getYear();
    }

    private HolidayService.HolidayDay holiday(LocalDate date) {
        if (date == null) return null;
        return holidayYears.computeIfAbsent(date.getYear(), year -> holidays.getHolidays(year).days())
                .get(date.toString());
    }

    private static boolean isWeekday(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }
}
