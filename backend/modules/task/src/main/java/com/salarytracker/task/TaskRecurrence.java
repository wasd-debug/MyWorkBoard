package com.salarytracker.task;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class TaskRecurrence {
    private static final Map<String, DayOfWeek> DAYS = Map.of("MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY,
            "WE", DayOfWeek.WEDNESDAY, "TH", DayOfWeek.THURSDAY, "FR", DayOfWeek.FRIDAY,
            "SA", DayOfWeek.SATURDAY, "SU", DayOfWeek.SUNDAY);

    private TaskRecurrence() {
    }

    static String validate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Map<String, String> rule = parse(raw);
        if (!Set.of("DAILY", "WEEKLY", "MONTHLY", "YEARLY").contains(rule.get("FREQ"))) {
            throw new IllegalArgumentException("RRULE FREQ 仅支持 DAILY/WEEKLY/MONTHLY/YEARLY");
        }
        for (String key : rule.keySet()) {
            if (!Set.of("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "COUNT", "UNTIL").contains(key)) {
                throw new IllegalArgumentException("RRULE 字段不支持: " + key);
            }
        }
        positive(rule, "INTERVAL", 1);
        positive(rule, "COUNT", null);
        if (rule.containsKey("BYDAY")) for (String day : rule.get("BYDAY").split(",")) parseDay(day);
        integers(rule, "BYMONTHDAY", -31, 31, false);
        integers(rule, "BYMONTH", 1, 12, true);
        until(rule.get("UNTIL"));
        return rule.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(";"));
    }

    static Instant next(String raw, Instant planned, Instant completed, String anchor, String timezone, int nextSequence) {
        Map<String, String> rule = parse(validate(raw));
        Integer count = positive(rule, "COUNT", null);
        if (count != null && nextSequence > count) return null;
        ZoneId zone = ZoneId.of(timezone);
        ZonedDateTime base = ("COMPLETION_DATE".equals(anchor) ? completed : planned).atZone(zone);
        LocalDate origin = planned.atZone(zone).toLocalDate();
        int interval = positive(rule, "INTERVAL", 1);
        ZonedDateTime candidate = base.plusDays(1).withHour(planned.atZone(zone).getHour())
                .withMinute(planned.atZone(zone).getMinute()).withSecond(planned.atZone(zone).getSecond()).withNano(0);
        for (int i = 0; i < 3700; i++, candidate = candidate.plusDays(1)) {
            if (matches(rule, origin, candidate.toLocalDate(), interval)) {
                Instant result = candidate.toInstant();
                Instant until = until(rule.get("UNTIL"));
                return until == null || !result.isAfter(until) ? result : null;
            }
        }
        throw new IllegalArgumentException("RRULE 在十年内没有下一实例");
    }

    private static boolean matches(Map<String, String> rule, LocalDate origin, LocalDate date, int interval) {
        long units = switch (rule.get("FREQ")) {
            case "DAILY" -> java.time.temporal.ChronoUnit.DAYS.between(origin, date);
            case "WEEKLY" -> java.time.temporal.ChronoUnit.WEEKS.between(
                    origin.with(DayOfWeek.MONDAY), date.with(DayOfWeek.MONDAY));
            case "MONTHLY" -> java.time.temporal.ChronoUnit.MONTHS.between(origin.withDayOfMonth(1), date.withDayOfMonth(1));
            default -> java.time.temporal.ChronoUnit.YEARS.between(origin.withDayOfYear(1), date.withDayOfYear(1));
        };
        if (units < interval || units % interval != 0) return false;
        Set<String> days = values(rule.get("BYDAY"));
        if (!days.isEmpty() && days.stream().noneMatch(value -> matchesDay(value, date))) return false;
        Set<Integer> monthDays = ints(rule.get("BYMONTHDAY"));
        if (!monthDays.isEmpty() && monthDays.stream().noneMatch(value -> value > 0
                ? date.getDayOfMonth() == value : date.getDayOfMonth() == date.lengthOfMonth() + value + 1)) return false;
        Set<Integer> months = ints(rule.get("BYMONTH"));
        if (!months.isEmpty() && !months.contains(date.getMonthValue())) return false;
        if (days.isEmpty() && monthDays.isEmpty()) {
            if ("WEEKLY".equals(rule.get("FREQ")) && date.getDayOfWeek() != origin.getDayOfWeek()) return false;
            if (("MONTHLY".equals(rule.get("FREQ")) || "YEARLY".equals(rule.get("FREQ")))
                    && date.getDayOfMonth() != Math.min(origin.getDayOfMonth(), date.lengthOfMonth())) return false;
        }
        return !"YEARLY".equals(rule.get("FREQ")) || rule.containsKey("BYMONTH") || date.getMonth() == origin.getMonth();
    }

    private static boolean matchesDay(String value, LocalDate date) {
        DayOfWeek day = parseDay(value);
        if (date.getDayOfWeek() != day) return false;
        String ordinal = value.substring(0, value.length() - 2);
        if (ordinal.isEmpty()) return true;
        int number = Integer.parseInt(ordinal);
        int occurrence = number > 0 ? (date.getDayOfMonth() - 1) / 7 + 1
                : -((date.lengthOfMonth() - date.getDayOfMonth()) / 7 + 1);
        return occurrence == number;
    }

    private static DayOfWeek parseDay(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        String suffix = normalized.substring(Math.max(0, normalized.length() - 2));
        if (!DAYS.containsKey(suffix)) throw new IllegalArgumentException("RRULE BYDAY 无效");
        String prefix = normalized.substring(0, normalized.length() - 2);
        if (!prefix.isEmpty()) {
            int ordinal;
            try { ordinal = Integer.parseInt(prefix); } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("RRULE BYDAY 无效");
            }
            if (ordinal == 0 || ordinal < -5 || ordinal > 5) throw new IllegalArgumentException("RRULE BYDAY 无效");
        }
        return DAYS.get(suffix);
    }

    private static Map<String, String> parse(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String item : raw.toUpperCase(Locale.ROOT).replaceFirst("^RRULE:", "").split(";")) {
            String[] pair = item.split("=", 2);
            if (pair.length != 2 || pair[1].isBlank() || result.put(pair[0], pair[1]) != null) {
                throw new IllegalArgumentException("RRULE 格式无效");
            }
        }
        return result;
    }

    private static Integer positive(Map<String, String> rule, String key, Integer fallback) {
        if (!rule.containsKey(key)) return fallback;
        try {
            int value = Integer.parseInt(rule.get(key));
            if (value < 1) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException exception) { throw new IllegalArgumentException("RRULE " + key + " 无效"); }
    }

    private static void integers(Map<String, String> rule, String key, int min, int max, boolean allowZero) {
        for (int value : ints(rule.get(key))) if (value < min || value > max || (!allowZero && value == 0)) {
            throw new IllegalArgumentException("RRULE " + key + " 无效");
        }
    }

    private static Set<Integer> ints(String raw) {
        if (raw == null) return Set.of();
        try { return Arrays.stream(raw.split(",")).map(Integer::parseInt).collect(Collectors.toSet()); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("RRULE 数值无效"); }
    }

    private static Set<String> values(String raw) {
        return raw == null ? Set.of() : Arrays.stream(raw.split(",")).collect(Collectors.toSet());
    }

    private static Instant until(String raw) {
        if (raw == null) return null;
        try {
            if (raw.matches("\\d{8}")) return LocalDate.parse(raw, DateTimeFormatter.BASIC_ISO_DATE)
                    .plusDays(1).atStartOfDay(ZoneId.of("UTC")).minusNanos(1).toInstant();
            return ZonedDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")).toInstant();
        } catch (Exception exception) { throw new IllegalArgumentException("RRULE UNTIL 无效"); }
    }
}
