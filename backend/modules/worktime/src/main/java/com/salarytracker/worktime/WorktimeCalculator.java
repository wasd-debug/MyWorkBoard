package com.salarytracker.worktime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;

final class WorktimeCalculator {
    private WorktimeCalculator() {
    }

    static Result calculate(String startText, String endText, int lunchMin, int restMin,
                            String workStartText, String workEndText,
                            BigDecimal salary, BigDecimal daysPerMonth, boolean offDay) {
        LocalTime start = LocalTime.parse(normalizeTime(startText));
        int actualMin = 0;
        if (endText != null && !endText.isBlank()) {
            LocalTime end = LocalTime.parse(normalizeTime(endText));
            long elapsed = Duration.between(start, end).toMinutes();
            if (elapsed < 0) elapsed += 24 * 60;
            actualMin = Math.max(0, (int) elapsed - lunchMin - restMin);
        }

        LocalTime workStart = LocalTime.parse(normalizeTime(workStartText));
        LocalTime workEnd = LocalTime.parse(normalizeTime(workEndText));
        long standardElapsed = Duration.between(workStart, workEnd).toMinutes();
        if (standardElapsed < 0) standardElapsed += 24 * 60;
        int standardMin = Math.max(0, (int) standardElapsed - lunchMin);
        int overtimeMin = actualMin == 0 ? 0 : offDay ? actualMin : actualMin - standardMin;
        BigDecimal realHourlyWage = actualMin > 0 && salary.signum() > 0 && daysPerMonth.signum() > 0
                ? salary.divide(daysPerMonth, 8, RoundingMode.HALF_UP)
                        .divide(BigDecimal.valueOf(actualMin).divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new Result(actualMin, overtimeMin, realHourlyWage);
    }

    private static String normalizeTime(String value) {
        return value.length() == 5 ? value + ":00" : value;
    }

    record Result(int actualMin, int overtimeMin, BigDecimal realHourlyWage) {
    }
}
