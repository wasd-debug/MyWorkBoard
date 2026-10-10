package com.salarytracker.calendar;

import com.nlf.calendar.Lunar;
import com.nlf.calendar.Solar;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.stream.Stream;

@Component
public class LunarCalendarAdapter implements LunarCalendar {
    static final int MIN_YEAR = 1900;
    static final int MAX_YEAR = 2100;

    @Override
    public LunarDay day(LocalDate date) {
        if (date == null || date.getYear() < MIN_YEAR || date.getYear() > MAX_YEAR) return null;
        Solar solar = Solar.fromYmd(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        Lunar lunar = solar.getLunar();
        String lunarDate = lunar.getMonthInChinese() + "月" + lunar.getDayInChinese();
        String festival = Stream.concat(solar.getFestivals().stream(), lunar.getFestivals().stream())
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
        String solarTerm = lunar.getJieQi();
        return new LunarDay(lunarDate, festival, solarTerm == null || solarTerm.isBlank() ? null : solarTerm);
    }
}
