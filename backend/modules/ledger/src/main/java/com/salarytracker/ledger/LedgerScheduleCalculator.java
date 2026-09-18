package com.salarytracker.ledger;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

import com.salarytracker.ledger.LedgerModels.CalendarRule;

final class LedgerScheduleCalculator {
    static final String INTERVAL = "INTERVAL";
    static final String CALENDAR = "CALENDAR";
    private static final Set<String> MODES = Set.of(INTERVAL, CALENDAR);

    private LedgerScheduleCalculator() {}

    static void validate(String mode, String frequency, int interval, CalendarRule rule) {
        if (!MODES.contains(mode)) throw new IllegalArgumentException("执行方式不正确");
        if (interval < 1) throw new IllegalArgumentException("间隔时间必须大于等于 1");
        if (INTERVAL.equals(mode)) return;
        switch (frequency) {
            case "WEEKLY" -> dayOfWeek(rule);
            case "MONTHLY" -> validateMonthly(rule);
            case "YEARLY" -> {
                int month = integer(rule, "month", 1, 12, "月份");
                int day = integer(rule, "dayOfMonth", 1, java.time.Month.of(month).maxLength(), "日期");
                if (month == 2 && day > 29) throw new IllegalArgumentException("二月日期不正确");
            }
            default -> throw new IllegalArgumentException("固定日期仅支持每周、每月或每年");
        }
    }

    static LocalDate firstDate(LocalDate start, String mode, String frequency, int interval, CalendarRule rule) {
        validate(mode, frequency, interval, rule);
        if (INTERVAL.equals(mode)) return start;
        return switch (frequency) {
            case "WEEKLY" -> start.with(TemporalAdjusters.nextOrSame(dayOfWeek(rule)));
            case "MONTHLY" -> monthlyOnOrAfter(start, rule);
            case "YEARLY" -> yearlyOnOrAfter(start, rule);
            default -> start;
        };
    }

    static LocalDate nextDate(LocalDate current, String mode, String frequency, int interval, CalendarRule rule) {
        validate(mode, frequency, interval, rule);
        if (INTERVAL.equals(mode)) {
            return switch (frequency) {
                case "ONCE" -> current;
                case "DAILY" -> current.plusDays(interval);
                case "WEEKLY" -> current.plusWeeks(interval);
                case "YEARLY" -> current.plusYears(interval);
                default -> current.plusMonths(interval);
            };
        }
        return switch (frequency) {
            case "WEEKLY" -> current.with(TemporalAdjusters.next(dayOfWeek(rule)));
            case "MONTHLY" -> monthlyOnOrAfter(current.plusDays(1), rule);
            case "YEARLY" -> yearlyOnOrAfter(current.plusDays(1), rule);
            default -> current;
        };
    }

    private static void validateMonthly(CalendarRule rule) {
        String monthlyMode = text(rule == null ? null : rule.monthlyMode(), "DAY_OF_MONTH").toUpperCase();
        if ("DAY_OF_MONTH".equals(monthlyMode)) integer(rule, "dayOfMonth", 1, 31, "日期");
        else if ("NTH_WEEKDAY".equals(monthlyMode)) {
            integer(rule, "weekOfMonth", 1, 5, "周次");
            dayOfWeek(rule);
        } else throw new IllegalArgumentException("每月固定规则不正确");
    }

    private static LocalDate monthlyOnOrAfter(LocalDate start, CalendarRule rule) {
        String monthlyMode = text(rule == null ? null : rule.monthlyMode(), "DAY_OF_MONTH").toUpperCase();
        YearMonth month = YearMonth.from(start);
        for (int i = 0; i < 240; i++, month = month.plusMonths(1)) {
            LocalDate candidate;
            if ("NTH_WEEKDAY".equals(monthlyMode)) {
                candidate = nthWeekday(month, integer(rule, "weekOfMonth", 1, 5, "周次"), dayOfWeek(rule));
                if (candidate == null) continue;
            } else {
                int day = Math.min(integer(rule, "dayOfMonth", 1, 31, "日期"), month.lengthOfMonth());
                candidate = month.atDay(day);
            }
            if (!candidate.isBefore(start)) return candidate;
        }
        throw new IllegalArgumentException("无法计算下一次执行日期");
    }

    private static LocalDate yearlyOnOrAfter(LocalDate start, CalendarRule rule) {
        int month = integer(rule, "month", 1, 12, "月份");
        int day = integer(rule, "dayOfMonth", 1, java.time.Month.of(month).maxLength(), "日期");
        for (int year = start.getYear(); year < start.getYear() + 20; year++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            LocalDate candidate = yearMonth.atDay(Math.min(day, yearMonth.lengthOfMonth()));
            if (!candidate.isBefore(start)) return candidate;
        }
        throw new IllegalArgumentException("无法计算下一次执行日期");
    }

    private static LocalDate nthWeekday(YearMonth month, int week, DayOfWeek dayOfWeek) {
        LocalDate first = month.atDay(1).with(TemporalAdjusters.nextOrSame(dayOfWeek));
        LocalDate candidate = first.plusWeeks(week - 1L);
        return YearMonth.from(candidate).equals(month) ? candidate : null;
    }

    private static DayOfWeek dayOfWeek(CalendarRule rule) {
        return DayOfWeek.of(integer(rule, "dayOfWeek", 1, 7, "星期"));
    }

    private static int integer(CalendarRule rule, String key, int min, int max, String label) {
        Object value = rule == null ? null : switch (key) {
            case "weekOfMonth" -> rule.weekOfMonth();
            case "dayOfWeek" -> rule.dayOfWeek();
            case "dayOfMonth" -> rule.dayOfMonth();
            case "month" -> rule.month();
            default -> null;
        };
        int number;
        try { number = Integer.parseInt(String.valueOf(value)); }
        catch (Exception ignored) { throw new IllegalArgumentException(label + "不能为空"); }
        if (number < min || number > max) throw new IllegalArgumentException(label + "超出允许范围");
        return number;
    }

    private static String text(Object value, String fallback) {
        String result = value == null ? "" : String.valueOf(value).trim();
        return result.isBlank() ? fallback : result;
    }
}
