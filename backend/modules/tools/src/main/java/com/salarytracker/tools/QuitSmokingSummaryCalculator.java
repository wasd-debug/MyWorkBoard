package com.salarytracker.tools;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 戒烟日记的纯计算部分：连续天数、最长连续、累计支数与省下金额。
 * <p>
 * 口径说明：
 * <ul>
 *   <li>「当天吸了烟」以 {@code smokedCount > 0} 判定，当天即断签；</li>
 *   <li>没有记录的日期视为「没有记录」而不是「复吸」，因此计入连续天数；</li>
 *   <li>为避免恶意起点（例如 1900 年）导致全表扫描，回溯与统计都限制在最近
 *       {@value #MAX_SCAN_DAYS} 天内；超出部分只体现在 {@code elapsedDays} 上。</li>
 * </ul>
 */
public final class QuitSmokingSummaryCalculator {
    public static final int MAX_SCAN_DAYS = 3650;

    private QuitSmokingSummaryCalculator() {
    }

    /**
     * @param start       戒烟起始自然日（账号时区）
     * @param today       账号时区的「今天」
     * @param smokedByDay 已记录的每日吸烟支数，key 为自然日
     */
    public static Totals totals(LocalDate start, LocalDate today, Map<LocalDate, Integer> smokedByDay) {
        if (start == null || today == null || start.isAfter(today)) return new Totals(0, 0, 0, 0, 0);
        int elapsedDays = (int) Math.min(Integer.MAX_VALUE, ChronoUnit.DAYS.between(start, today) + 1);

        int totalSmoked = 0;
        int relapseDays = 0;
        for (Map.Entry<LocalDate, Integer> entry : smokedByDay.entrySet()) {
            LocalDate day = entry.getKey();
            if (day == null || day.isBefore(start) || day.isAfter(today)) continue;
            int count = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
            totalSmoked += count;
            if (count > 0) relapseDays++;
        }

        LocalDate scanStart = start.isBefore(today.minusDays(MAX_SCAN_DAYS - 1L))
                ? today.minusDays(MAX_SCAN_DAYS - 1L) : start;

        int currentStreak = 0;
        for (LocalDate cursor = today; !cursor.isBefore(scanStart); cursor = cursor.minusDays(1)) {
            if (smoked(cursor, smokedByDay) > 0) break;
            currentStreak++;
        }

        int longestStreak = 0;
        int run = 0;
        for (LocalDate cursor = scanStart; !cursor.isAfter(today); cursor = cursor.plusDays(1)) {
            if (smoked(cursor, smokedByDay) > 0) {
                run = 0;
            } else {
                run++;
                if (run > longestStreak) longestStreak = run;
            }
        }

        return new Totals(elapsedDays, elapsedDays - relapseDays, currentStreak, longestStreak, totalSmoked);
    }

    /**
     * 省下金额 =（基线日消耗 × 已戒烟天数 − 实际吸掉的支数）× 单支价格。
     * 吸得比基线更多时返回负数，由前端提示「已超出基线」。
     */
    public static BigDecimal savedAmount(Totals totals, int baselinePerDay, BigDecimal pricePerPack,
                                         int cigarettesPerPack) {
        if (totals == null || pricePerPack == null || cigarettesPerPack <= 0) return BigDecimal.ZERO;
        long avoided = (long) totals.elapsedDays() * Math.max(0, baselinePerDay) - totals.totalSmoked();
        return BigDecimal.valueOf(avoided)
                .multiply(pricePerPack)
                .divide(BigDecimal.valueOf(cigarettesPerPack), 2, RoundingMode.HALF_UP);
    }

    /**
     * 焦油与一氧化碳口径：
     * <ul>
     *   <li>「今天吸入」= 今天吸烟支数 × 每支含量；</li>
     *   <li>「累计少吸入」=（基线日消耗 × 已戒烟天数 − 实际吸掉的支数）× 每支含量，
     *       吸得比基线更多时返回负数，与省钱额的约定一致。</li>
     * </ul>
     * 每支含量为 0（例如电子烟）时所有结果都是 0，不做特殊处理。
     */
    public static Substance substance(Totals totals, int baselinePerDay, BigDecimal tarPerCigarette,
                                      BigDecimal coPerCigarette, int todaySmoked) {
        BigDecimal tar = perCigarette(tarPerCigarette);
        BigDecimal co = perCigarette(coPerCigarette);
        int elapsedDays = totals == null ? 0 : totals.elapsedDays();
        int totalSmoked = totals == null ? 0 : totals.totalSmoked();
        long avoidedCount = (long) elapsedDays * Math.max(0, baselinePerDay) - totalSmoked;
        int today = Math.max(0, todaySmoked);
        return new Substance(tar, co,
                tar.multiply(BigDecimal.valueOf(today)).setScale(1, RoundingMode.HALF_UP),
                co.multiply(BigDecimal.valueOf(today)).setScale(1, RoundingMode.HALF_UP),
                tar.multiply(BigDecimal.valueOf(avoidedCount)).setScale(1, RoundingMode.HALF_UP),
                co.multiply(BigDecimal.valueOf(avoidedCount)).setScale(1, RoundingMode.HALF_UP));
    }

    private static BigDecimal perCigarette(BigDecimal value) {
        if (value == null || value.signum() < 0) return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private static int smoked(LocalDate day, Map<LocalDate, Integer> smokedByDay) {
        Integer count = smokedByDay.get(day);
        return count == null ? 0 : Math.max(0, count);
    }

    public record Totals(int elapsedDays, int cleanDays, int currentStreak, int longestStreak, int totalSmoked) {
    }

    public record Substance(BigDecimal tarPerCigarette, BigDecimal coPerCigarette, BigDecimal todayTarMg,
                            BigDecimal todayCoMg, BigDecimal avoidedTarMg, BigDecimal avoidedCoMg) {
    }
}
