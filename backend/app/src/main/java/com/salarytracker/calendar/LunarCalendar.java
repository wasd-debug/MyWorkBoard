package com.salarytracker.calendar;

import java.time.LocalDate;

public interface LunarCalendar {
    LunarDay day(LocalDate date);

    record LunarDay(String lunarDate, String festival, String solarTerm) {
    }
}
