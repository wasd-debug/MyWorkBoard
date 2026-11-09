package com.salarytracker.task;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 系统内置倒数日：随账户自动创建，可被用户删除。
 * 节假日按国务院公布的放假起始日维护；超出表格范围的年份退化为固定日期节日。
 */
final class BuiltinCountdowns {

    record BuiltinCountdown(String key, String title, LocalDate targetDate, String kind, String icon,
                            String color, int sortOrder) { }

    record Holiday(LocalDate date, String name) { }

    private static final List<Holiday> HOLIDAYS = List.of(
            new Holiday(LocalDate.of(2025, 1, 1), "元旦"),
            new Holiday(LocalDate.of(2025, 1, 29), "春节"),
            new Holiday(LocalDate.of(2025, 4, 4), "清明节"),
            new Holiday(LocalDate.of(2025, 5, 1), "劳动节"),
            new Holiday(LocalDate.of(2025, 5, 31), "端午节"),
            new Holiday(LocalDate.of(2025, 10, 1), "国庆节"),
            new Holiday(LocalDate.of(2025, 10, 6), "中秋节"),
            new Holiday(LocalDate.of(2026, 1, 1), "元旦"),
            new Holiday(LocalDate.of(2026, 2, 17), "春节"),
            new Holiday(LocalDate.of(2026, 4, 5), "清明节"),
            new Holiday(LocalDate.of(2026, 5, 1), "劳动节"),
            new Holiday(LocalDate.of(2026, 6, 19), "端午节"),
            new Holiday(LocalDate.of(2026, 9, 25), "中秋节"),
            new Holiday(LocalDate.of(2026, 10, 1), "国庆节"),
            new Holiday(LocalDate.of(2027, 1, 1), "元旦"),
            new Holiday(LocalDate.of(2027, 2, 6), "春节"),
            new Holiday(LocalDate.of(2027, 4, 5), "清明节"),
            new Holiday(LocalDate.of(2027, 5, 1), "劳动节"),
            new Holiday(LocalDate.of(2027, 6, 9), "端午节"),
            new Holiday(LocalDate.of(2027, 9, 15), "中秋节"),
            new Holiday(LocalDate.of(2027, 10, 1), "国庆节"),
            new Holiday(LocalDate.of(2028, 1, 1), "元旦"),
            new Holiday(LocalDate.of(2028, 1, 26), "春节"),
            new Holiday(LocalDate.of(2028, 4, 4), "清明节"),
            new Holiday(LocalDate.of(2028, 5, 1), "劳动节"),
            new Holiday(LocalDate.of(2028, 5, 28), "端午节"),
            new Holiday(LocalDate.of(2028, 10, 1), "国庆节"),
            new Holiday(LocalDate.of(2028, 10, 3), "中秋节")
    );

    private BuiltinCountdowns() { }

    static List<BuiltinCountdown> all(LocalDate today) {
        return all(today, Map.of());
    }

    /**
     * @param holidayTable 放假表：日期 → 是否放假（false 表示调休补班）。
     *                     发薪日要靠它判断「前一天是不是工作日」，表里没有的年份退化为只看周末。
     */
    static List<BuiltinCountdown> all(LocalDate today, Map<LocalDate, Boolean> holidayTable) {
        List<BuiltinCountdown> result = new ArrayList<>();
        Holiday holiday = nextHoliday(today);
        result.add(new BuiltinCountdown("weekend", "距离周末", nextWeekend(today), "COUNTDOWN", "🌤️", "#4d78db", 0));
        result.add(new BuiltinCountdown("holiday", "距离" + holiday.name(), holiday.date(),
                "FESTIVAL", holidayIcon(holiday.name()), "#e2685f", 1));
        result.add(new BuiltinCountdown("payday", "距离发薪日", nextPayday(today, holidayTable),
                "COUNTDOWN", "💰", "#d9a03c", 2));
        result.add(new BuiltinCountdown("month-end", "距离本月结束",
                today.with(TemporalAdjusters.lastDayOfMonth()), "COUNTDOWN", "📅", "#2fb39a", 3));
        result.add(new BuiltinCountdown("year-end", "距离本年结束",
                LocalDate.of(today.getYear(), Month.DECEMBER, 31), "COUNTDOWN", "🗓️", "#a568d4", 4));
        return result;
    }

    /** 每月 7 日发薪；7 日是非工作日就前移到之前最近的工作日（十月国庆连休 ⇒ 9/30 发）。 */
    static LocalDate nextPayday(LocalDate today, Map<LocalDate, Boolean> holidayTable) {
        LocalDate payday = paydayOf(today.getYear(), today.getMonthValue(), holidayTable);
        if (!payday.isBefore(today)) return payday;
        LocalDate next = today.plusMonths(1).withDayOfMonth(1);
        return paydayOf(next.getYear(), next.getMonthValue(), holidayTable);
    }

    static LocalDate paydayOf(int year, int month, Map<LocalDate, Boolean> holidayTable) {
        LocalDate day = LocalDate.of(year, month, Math.min(7, YearMonth.of(year, month).lengthOfMonth()));
        // 可以退到上个月（国庆连休时 10/7 一路退到 9/30），最多退 15 天兜底防死循环
        for (int guard = 0; isNonWorkingDay(day, holidayTable) && guard < 15; guard++) {
            day = day.minusDays(1);
        }
        return day;
    }

    static boolean isNonWorkingDay(LocalDate date, Map<LocalDate, Boolean> holidayTable) {
        Boolean off = holidayTable == null ? null : holidayTable.get(date);
        // 表里有记录就以公告为准（补班的周六算工作日）
        if (off != null) return off;
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }


    static LocalDate nextWeekend(LocalDate today) {
        int gap = (DayOfWeek.SATURDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        return today.plusDays(gap);
    }

    static Holiday nextHoliday(LocalDate today) {
        return HOLIDAYS.stream()
                .filter(holiday -> !holiday.date().isBefore(today))
                .min(Comparator.comparing(Holiday::date))
                .orElseGet(() -> fallbackHoliday(today));
    }

    /** 表格之外：元旦优先，否则取下一个国庆 */
    private static Holiday fallbackHoliday(LocalDate today) {
        LocalDate newYear = LocalDate.of(today.getYear() + 1, Month.JANUARY, 1);
        LocalDate nationalDay = LocalDate.of(today.getYear(), Month.OCTOBER, 1);
        return nationalDay.isBefore(today)
                ? new Holiday(newYear, "元旦")
                : new Holiday(nationalDay, "国庆节");
    }

    /** 每个节日一个图标，避免「距离元旦」还顶着派对礼花 */
    private static String holidayIcon(String name) {
        return switch (name) {
            case "元旦" -> "🎊";
            case "春节" -> "🧨";
            case "清明节" -> "🌿";
            case "劳动节" -> "🛠️";
            case "端午节" -> "🐲";
            case "中秋节" -> "🥮";
            case "国庆节" -> "🎉";
            default -> "🎉";
        };
    }
}
