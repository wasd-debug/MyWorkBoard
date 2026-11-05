package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.salarytracker.task.TaskStatsModels.*;

/**
 * 统计页的真实数据聚合。
 *
 * 时区约定（很重要）：
 * 1. MySQL 容器时区是 Asia/Shanghai，`task.completed_at` / `focus_session.started_at` 这类
 *    TIMESTAMP 列在 SQL 里读出来就是**本地时间**，所以按日分组用
 *    `DATE(CONVERT_TZ(col,'+08:00',userOffset))`，同一时区时等于原值；
 * 2. 后端 JVM 跑在 UTC，JDBC 用 `Timestamp` 传参会按 UTC 序列化，导致边界整体偏 8 小时。
 *    因此所有时间边界一律用「DB 时区的本地时间字符串」传参（见 {@link #dayStart}），
 *    **不要**用 `Timestamp.from(...)`。
 */
@Service
public class TaskStatsService {
    /** 数据库时间列所在时区（容器 TZ=Asia/Shanghai）。 */
    private static final String DB_OFFSET = "+08:00";
    private static final ZoneId DB_ZONE = ZoneId.of(DB_OFFSET);
    private static final DateTimeFormatter SQL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("M/d");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("M月d日");
    private static final List<String> WEEK_LABELS = List.of("一", "二", "三", "四", "五", "六", "日");
    private static final List<String> SLICE_COLORS =
            List.of("#4d78db", "#f0a33c", "#38b2a3", "#e5678a", "#8b7cf0", "#59a14f", "#b48ead", "#8b93a3");

    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;

    public TaskStatsService(JdbcTemplate jdbc, CurrentUserResolver currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------ 总览

    public Overview overview() {
        long userId = currentUser.id();
        ZoneId zone = zone(userId);
        String offset = userOffset(zone);
        LocalDate today = LocalDate.now(zone);
        LocalDate trendStart = today.minusDays(6);
        String trendFrom = dayStart(trendStart, zone);
        String trendTo = dayStart(today.plusDays(1), zone);
        String todayFrom = dayStart(today, zone);

        long totalTasks = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0", userId);
        long completedTasks = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status='COMPLETED'", userId);
        long totalLists = count("SELECT COUNT(*) FROM task_list WHERE user_id=? AND deleted=0", userId);
        long useDays = useDays(userId, zone, today);

        long todayCompleted = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND completed_at>=? AND completed_at<?",
                userId, todayFrom, trendTo);
        long[] todayFocus = focusTotals(userId, todayFrom, null);
        long[] allFocus = focusTotals(userId, null, null);

        Map<LocalDate, Long> completedByDay = dailyTaskCompleted(userId, offset, trendFrom, trendTo);
        Map<LocalDate, Long> undoneByDay = dailyTaskUndoneDue(userId, offset, trendFrom, trendTo);
        Map<LocalDate, long[]> focusByDay = dailyFocus(userId, offset, trendFrom, trendTo);

        long baseCompleted = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status='COMPLETED' AND completed_at<?",
                userId, trendFrom);
        long baseFocus = sum("SELECT COALESCE(SUM(actual_minutes),0) FROM focus_session WHERE user_id=? AND status='COMPLETED' AND started_at<?",
                userId, trendFrom);

        List<Point> achievementTrend = new ArrayList<>();
        List<Point> completedTrend = new ArrayList<>();
        List<Point> rateTrend = new ArrayList<>();
        List<Point> pomodoroTrend = new ArrayList<>();
        List<Point> focusTrend = new ArrayList<>();
        long runningCompleted = baseCompleted;
        long runningFocus = baseFocus;
        for (int i = 0; i < 7; i += 1) {
            LocalDate day = trendStart.plusDays(i);
            long done = completedByDay.getOrDefault(day, 0L);
            long undone = undoneByDay.getOrDefault(day, 0L);
            long[] focus = focusByDay.getOrDefault(day, new long[]{0, 0});
            runningCompleted += done;
            runningFocus += focus[1];
            String label = day.format(SHORT_DATE);
            completedTrend.add(new Point(day.toString(), label, done));
            pomodoroTrend.add(new Point(day.toString(), label, focus[0]));
            focusTrend.add(new Point(day.toString(), label, focus[1]));
            rateTrend.add(new Point(day.toString(), label, done + undone == 0 ? 0 : round(done * 100d / (done + undone))));
            achievementTrend.add(new Point(day.toString(), label, achievement(runningCompleted, runningFocus)));
        }

        return new Overview(totalTasks, completedTasks, totalLists, useDays, percentile(userId, completedTasks),
                todayCompleted, todayFocus[0], todayFocus[1],
                completedTasks, allFocus[0], allFocus[1],
                achievement(completedTasks, allFocus[1]),
                achievementTrend, completedTrend, rateTrend, pomodoroTrend, focusTrend,
                weekRings(userId, zone, today));
    }

    // ------------------------------------------------------------------ 任务

    public TaskPageStats taskPage(String mode, String date, String groupMode) {
        long userId = currentUser.id();
        ZoneId zone = zone(userId);
        String offset = userOffset(zone);
        String normalizedMode = mode == null ? "DAY" : mode.trim().toUpperCase();
        LocalDate anchor = parseDate(date, LocalDate.now(zone));
        LocalDate from;
        LocalDate to;
        String label;
        long span;
        switch (normalizedMode) {
            case "WEEK" -> {
                from = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                to = from.plusWeeks(1);
                label = from.format(SHORT_DATE) + " - " + to.minusDays(1).format(SHORT_DATE);
                span = 7;
            }
            case "MONTH" -> {
                from = anchor.withDayOfMonth(1);
                to = from.plusMonths(1);
                label = from.getYear() + "年" + from.getMonthValue() + "月";
                span = ChronoUnit.DAYS.between(from, to);
            }
            default -> {
                from = anchor;
                to = anchor.plusDays(1);
                label = anchor.format(DAY_LABEL) + (anchor.equals(LocalDate.now(zone)) ? "（今天）" : "");
                span = 1;
            }
        }
        LocalDate prevFrom = from.minusDays(span);
        LocalDate prevTo = from;

        String fromAt = dayStart(from, zone);
        String toAt = dayStart(to, zone);
        String prevFromAt = dayStart(prevFrom, zone);
        String prevToAt = dayStart(prevTo, zone);

        long completed = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status='COMPLETED' AND completed_at>=? AND completed_at<?",
                userId, fromAt, toAt);
        long overdueCompleted = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status='COMPLETED' " +
                "AND completed_at>=? AND completed_at<? AND due_at IS NOT NULL AND completed_at>due_at", userId, fromAt, toAt);
        long onTimeCompleted = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status='COMPLETED' " +
                "AND completed_at>=? AND completed_at<? AND due_at IS NOT NULL AND completed_at<=due_at", userId, fromAt, toAt);
        long noDueCompleted = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status='COMPLETED' " +
                "AND completed_at>=? AND completed_at<? AND due_at IS NULL", userId, fromAt, toAt);
        long undone = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status<>'COMPLETED' AND abandoned_at IS NULL " +
                "AND due_at>=? AND due_at<?", userId, fromAt, toAt);
        long total = completed + undone;
        long prevCompleted = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status='COMPLETED' AND completed_at>=? AND completed_at<?",
                userId, prevFromAt, prevToAt);
        long prevUndone = count("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=0 AND status<>'COMPLETED' AND abandoned_at IS NULL " +
                "AND due_at>=? AND due_at<?", userId, prevFromAt, prevToAt);
        long prevTotal = prevCompleted + prevUndone;

        String normalizedGroup = groupMode == null ? "LIST" : groupMode.trim().toUpperCase();
        return new TaskPageStats(normalizedMode, from.toString(), to.minusDays(1).toString(), label,
                completed, total, total == 0 ? 0 : (int) Math.round(completed * 100d / total),
                prevCompleted, prevTotal == 0 ? 0 : (int) Math.round(prevCompleted * 100d / prevTotal),
                overdueCompleted, onTimeCompleted, noDueCompleted, undone,
                normalizedGroup, completedGroups(userId, normalizedGroup, fromAt, toAt));
    }

    // ------------------------------------------------------------------ 专注

    public FocusPage focusPage(String date, String groupMode, String trendMode, String trendDate,
                               String timelineDate, String month, String year) {
        long userId = currentUser.id();
        ZoneId zone = zone(userId);
        String offset = userOffset(zone);
        LocalDate today = LocalDate.now(zone);

        LocalDate detailDay = parseDate(date, today);
        String detailLabel = detailDay.format(DAY_LABEL) + (detailDay.equals(today) ? "（今天）" : "");
        String detailFrom = dayStart(detailDay, zone);
        String detailTo = dayStart(detailDay.plusDays(1), zone);

        String normalizedTrend = trendMode == null ? "WEEK" : trendMode.trim().toUpperCase();
        LocalDate trendAnchor = parseDate(trendDate, today);
        LocalDate trendFrom;
        LocalDate trendTo;
        String trendLabel;
        if ("MONTH".equals(normalizedTrend)) {
            trendFrom = trendAnchor.withDayOfMonth(1);
            trendTo = trendFrom.plusMonths(1);
            trendLabel = trendFrom.getYear() + "年" + trendFrom.getMonthValue() + "月";
        } else if ("DAY".equals(normalizedTrend)) {
            trendFrom = trendAnchor;
            trendTo = trendAnchor.plusDays(1);
            trendLabel = trendAnchor.format(DAY_LABEL);
        } else {
            trendFrom = trendAnchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            trendTo = trendFrom.plusWeeks(1);
            trendLabel = "本周";
        }

        LocalDate currentMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate timelineWeek = parseDate(timelineDate, today).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate monthStart = parseDate(month, today).withDayOfMonth(1);
        int yearValue = parseYear(year, today.getYear());

        long[] todayFocus = focusTotals(userId, dayStart(today, zone), null);
        long[] allFocus = focusTotals(userId, null, null);
        String normalizedGroup = groupMode == null ? "LIST" : groupMode.trim().toUpperCase();

        return new FocusPage(todayFocus[0], todayFocus[1], allFocus[0], allFocus[1],
                detailDay.toString(), detailLabel,
                normalizedGroup, focusGroups(userId, normalizedGroup, detailFrom, detailTo),
                focusRecords(userId, detailFrom, detailTo),
                normalizedTrend, trendLabel, focusTrend(userId, offset, trendFrom, trendTo),
                timelineWeek.equals(currentMonday) ? "本周"
                        : timelineWeek.format(SHORT_DATE) + " - " + timelineWeek.plusDays(6).format(SHORT_DATE),
                focusTimeline(userId, offset, timelineWeek, zone),
                monthStart.getMonthValue() + "月",
                bestHours(userId, offset, monthStart, monthStart.plusMonths(1), zone),
                heatmap(userId, offset, LocalDate.of(yearValue, 1, 1), LocalDate.of(yearValue + 1, 1, 1), zone),
                yearValue);
    }

    // ------------------------------------------------------------------ 内部实现

    private List<Ring> weekRings(long userId, ZoneId zone, LocalDate today) {
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long activeHabits = count("SELECT COUNT(*) FROM habit WHERE user_id=? AND deleted_at IS NULL AND is_archived=0", userId);
        Map<LocalDate, Long> done = new HashMap<>();
        jdbc.query("SELECT checkin_date d, COUNT(*) c FROM habit_checkin WHERE user_id=? AND status='DONE' " +
                        "AND checkin_date>=? AND checkin_date<? GROUP BY checkin_date",
                row -> { done.put(row.getDate(1).toLocalDate(), row.getLong(2)); },
                userId, java.sql.Date.valueOf(weekStart), java.sql.Date.valueOf(weekStart.plusWeeks(1)));
        List<Ring> rings = new ArrayList<>();
        for (int i = 0; i < 7; i += 1) {
            LocalDate day = weekStart.plusDays(i);
            long hits = done.getOrDefault(day, 0L);
            int percent = activeHabits == 0 ? 0 : (int) Math.min(100, Math.round(hits * 100d / activeHabits));
            rings.add(new Ring(day.toString(), WEEK_LABELS.get(i), percent, day.equals(today), hits));
        }
        return rings;
    }

    private List<Slice> completedGroups(long userId, String groupMode, String from, String to) {
        List<Slice> slices = new ArrayList<>();
        if ("TAG".equals(groupMode)) {
            jdbc.query("SELECT g.public_id, g.name, g.color, COUNT(*) c FROM task_tag_link k " +
                            "JOIN task_tag g ON g.id=k.tag_id JOIN task t ON t.id=k.task_id " +
                            "WHERE t.user_id=? AND t.deleted=0 AND t.status='COMPLETED' AND t.completed_at>=? AND t.completed_at<? " +
                            "GROUP BY g.id, g.public_id, g.name, g.color ORDER BY c DESC",
                    row -> { slices.add(new Slice(row.getString(1), row.getString(2), color(row.getString(3), slices.size()), row.getLong(4))); },
                    userId, from, to);
        } else if ("PRIORITY".equals(groupMode)) {
            Map<String, String> labels = Map.of("HIGH", "高优先级", "MEDIUM", "中优先级", "LOW", "低优先级", "NONE", "无优先级");
            jdbc.query("SELECT priority, COUNT(*) c FROM task WHERE user_id=? AND deleted=0 AND status='COMPLETED' " +
                            "AND completed_at>=? AND completed_at<? GROUP BY priority ORDER BY c DESC",
                    row -> {
                        String key = row.getString(1) == null ? "NONE" : row.getString(1);
                        slices.add(new Slice(key, labels.getOrDefault(key, key), SLICE_COLORS.get(slices.size() % SLICE_COLORS.size()), row.getLong(2)));
                    }, userId, from, to);
        } else {
            jdbc.query("SELECT l.public_id, l.name, l.color, COUNT(*) c FROM task t " +
                            "JOIN task_list l ON l.id=t.list_id WHERE t.user_id=? AND t.deleted=0 " +
                            "AND t.status='COMPLETED' AND t.completed_at>=? AND t.completed_at<? " +
                            "GROUP BY l.id, l.public_id, l.name, l.color ORDER BY c DESC",
                    row -> { slices.add(new Slice(row.getString(1), row.getString(2), color(row.getString(3), slices.size()), row.getLong(4))); },
                    userId, from, to);
        }
        return slices;
    }

    private List<Slice> focusGroups(long userId, String groupMode, String from, String to) {
        List<Slice> slices = new ArrayList<>();
        if ("TASK".equals(groupMode)) {
            jdbc.query("SELECT COALESCE(t.public_id,''), COALESCE(t.title,'未关联任务'), COUNT(*) c " +
                            "FROM focus_session f LEFT JOIN task t ON t.id=f.task_id " +
                            "WHERE f.user_id=? AND f.status='COMPLETED' AND f.started_at>=? AND f.started_at<? " +
                            "GROUP BY t.id, t.public_id, t.title ORDER BY c DESC LIMIT 12",
                    row -> { slices.add(new Slice(row.getString(1), row.getString(2), SLICE_COLORS.get(slices.size() % SLICE_COLORS.size()), row.getLong(3))); },
                    userId, from, to);
        } else {
            jdbc.query("SELECT COALESCE(l.public_id,''), COALESCE(l.name,'其他'), COALESCE(l.color,''), COUNT(*) c " +
                            "FROM focus_session f LEFT JOIN task t ON t.id=f.task_id LEFT JOIN task_list l ON l.id=t.list_id " +
                            "WHERE f.user_id=? AND f.status='COMPLETED' AND f.started_at>=? AND f.started_at<? " +
                            "GROUP BY l.id, l.public_id, l.name, l.color ORDER BY c DESC LIMIT 12",
                    row -> { slices.add(new Slice(row.getString(1), row.getString(2), color(row.getString(3), slices.size()), row.getLong(4))); },
                    userId, from, to);
        }
        return slices;
    }

    /** 时间直接由 SQL 按会话时区格式化，避开 JVM(UTC) 与 DB(+08:00) 的时区错位。 */
    private List<FocusRecord> focusRecords(long userId, String from, String to) {
        return jdbc.query("SELECT f.public_id, DATE_FORMAT(f.started_at,'%Y-%m-%d'), DATE_FORMAT(f.started_at,'%H:%i'), " +
                        "COALESCE(DATE_FORMAT(f.ended_at,'%H:%i'),''), f.actual_minutes, " +
                        "t.title, h.name FROM focus_session f " +
                        "LEFT JOIN task t ON t.id=f.task_id LEFT JOIN habit h ON h.id=f.habit_id " +
                        "WHERE f.user_id=? AND f.status='COMPLETED' AND f.started_at>=? AND f.started_at<? " +
                        "ORDER BY f.started_at DESC LIMIT 60",
                (row, index) -> {
                    String title = row.getString(6);
                    String targetType = "TASK";
                    if (title == null && row.getString(7) != null) {
                        title = row.getString(7);
                        targetType = "HABIT";
                    }
                    return new FocusRecord(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                            title == null ? "未关联任务" : title, row.getLong(5), targetType);
                },
                userId, from, to);
    }

    private List<Point> focusTrend(long userId, String offset, LocalDate from, LocalDate to) {
        ZoneId zone = ZoneId.of(offset);
        Map<LocalDate, long[]> byDay = dailyFocus(userId, offset, dayStart(from, zone), dayStart(to, zone));
        List<Point> points = new ArrayList<>();
        long span = ChronoUnit.DAYS.between(from, to);
        for (int i = 0; i < span; i += 1) {
            LocalDate day = from.plusDays(i);
            long[] values = byDay.getOrDefault(day, new long[]{0, 0});
            points.add(new Point(day.toString(), day.format(SHORT_DATE), values[1]));
        }
        return points;
    }

    private List<TimelineDay> focusTimeline(long userId, String offset, LocalDate weekStart, ZoneId zone) {
        double[][] grid = new double[7][24];
        LocalDate weekEnd = weekStart.plusWeeks(1);
        jdbc.query("SELECT DATE(CONVERT_TZ(started_at,? ,?)) d, HOUR(CONVERT_TZ(started_at,?,?)) h, SUM(actual_minutes) m " +
                        "FROM focus_session WHERE user_id=? AND status='COMPLETED' AND started_at>=? AND started_at<? " +
                        "GROUP BY d, h",
                row -> {
                    LocalDate day = row.getDate(1).toLocalDate();
                    int index = (int) ChronoUnit.DAYS.between(weekStart, day);
                    int hour = row.getInt(2);
                    if (index >= 0 && index < 7 && hour >= 0 && hour < 24) grid[index][hour] = row.getDouble(3);
                }, DB_OFFSET, offset, DB_OFFSET, offset, userId,
                dayStart(weekStart, zone), dayStart(weekEnd, zone));
        LocalDate today = LocalDate.now(zone);
        List<TimelineDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i += 1) {
            LocalDate day = weekStart.plusDays(i);
            List<Double> hours = new ArrayList<>();
            for (int h = 0; h < 24; h += 1) hours.add(grid[i][h]);
            days.add(new TimelineDay(day.toString(), WEEK_LABELS.get(i), day.equals(today), hours));
        }
        return days;
    }

    private List<Point> bestHours(long userId, String offset, LocalDate from, LocalDate to, ZoneId zone) {
        double[] hours = new double[24];
        jdbc.query("SELECT HOUR(CONVERT_TZ(started_at,?,?)) h, SUM(actual_minutes) m FROM focus_session " +
                        "WHERE user_id=? AND status='COMPLETED' AND started_at>=? AND started_at<? GROUP BY h",
                row -> {
                    int hour = row.getInt(1);
                    if (hour >= 0 && hour < 24) hours[hour] = row.getDouble(2);
                }, DB_OFFSET, offset, userId, dayStart(from, zone), dayStart(to, zone));
        List<Point> points = new ArrayList<>();
        for (int h = 0; h < 24; h += 1) points.add(new Point(String.valueOf(h), String.format("%02d:00", h), hours[h]));
        return points;
    }

    private List<HeatDay> heatmap(long userId, String offset, LocalDate from, LocalDate to, ZoneId zone) {
        Map<LocalDate, Long> minutes = new LinkedHashMap<>();
        jdbc.query("SELECT DATE(CONVERT_TZ(started_at,?,?)) d, SUM(actual_minutes) m FROM focus_session " +
                        "WHERE user_id=? AND status='COMPLETED' AND started_at>=? AND started_at<? GROUP BY d",
                row -> { minutes.put(row.getDate(1).toLocalDate(), row.getLong(2)); },
                DB_OFFSET, offset, userId, dayStart(from, zone), dayStart(to, zone));
        List<HeatDay> days = new ArrayList<>();
        long span = ChronoUnit.DAYS.between(from, to);
        for (int i = 0; i < span; i += 1) {
            LocalDate day = from.plusDays(i);
            days.add(new HeatDay(day.toString(), minutes.getOrDefault(day, 0L)));
        }
        return days;
    }

    private Map<LocalDate, Long> dailyTaskCompleted(long userId, String offset, String from, String to) {
        Map<LocalDate, Long> map = new HashMap<>();
        jdbc.query("SELECT DATE(CONVERT_TZ(completed_at,?,?)) d, COUNT(*) c FROM task " +
                        "WHERE user_id=? AND deleted=0 AND status='COMPLETED' AND completed_at>=? AND completed_at<? GROUP BY d",
                row -> { map.put(row.getDate(1).toLocalDate(), row.getLong(2)); },
                DB_OFFSET, offset, userId, from, to);
        return map;
    }

    private Map<LocalDate, Long> dailyTaskUndoneDue(long userId, String offset, String from, String to) {
        Map<LocalDate, Long> map = new HashMap<>();
        jdbc.query("SELECT DATE(CONVERT_TZ(due_at,?,?)) d, COUNT(*) c FROM task " +
                        "WHERE user_id=? AND deleted=0 AND status<>'COMPLETED' AND abandoned_at IS NULL " +
                        "AND due_at>=? AND due_at<? GROUP BY d",
                row -> { map.put(row.getDate(1).toLocalDate(), row.getLong(2)); },
                DB_OFFSET, offset, userId, from, to);
        return map;
    }

    private Map<LocalDate, long[]> dailyFocus(long userId, String offset, String from, String to) {
        Map<LocalDate, long[]> map = new HashMap<>();
        jdbc.query("SELECT DATE(CONVERT_TZ(started_at,?,?)) d, COUNT(*) c, COALESCE(SUM(actual_minutes),0) m " +
                        "FROM focus_session WHERE user_id=? AND status='COMPLETED' AND started_at>=? AND started_at<? GROUP BY d",
                row -> { map.put(row.getDate(1).toLocalDate(), new long[]{row.getLong(2), row.getLong(3)}); },
                DB_OFFSET, offset, userId, from, to);
        return map;
    }

    private long useDays(long userId, ZoneId zone, LocalDate today) {
        java.sql.Timestamp first = jdbc.queryForObject(
                "SELECT MIN(created_at) FROM task WHERE user_id=? AND deleted=0", java.sql.Timestamp.class, userId);
        if (first == null) return 1;
        LocalDate start = first.toInstant().atZone(zone).toLocalDate();
        return Math.max(1, ChronoUnit.DAYS.between(start, today) + 1);
    }

    /** 真实排名：已完成任务数在全站有任务数据的用户里的百分比位置（含 0 完成的用户）。 */
    private int percentile(long userId, long completed) {
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT user_id) FROM task WHERE deleted=0", Long.class);
        if (total == null || total <= 1) return 50;
        Long below = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT user_id, SUM(status='COMPLETED') c " +
                        "FROM task WHERE deleted=0 GROUP BY user_id) u WHERE u.c < ?",
                Long.class, completed);
        return (int) Math.max(1, Math.min(99, Math.round((below == null ? 0 : below) * 100d / total)));
    }

    /** 与滴答一致的量级：完成任务计 1 分，专注每 10 分钟计 1 分。 */
    private long achievement(long completed, long focusMinutes) {
        return completed + Math.round(focusMinutes / 10d);
    }

    private long[] focusTotals(long userId, String from, String to) {
        if (from != null && to != null) {
            return jdbc.queryForObject("SELECT COUNT(*), COALESCE(SUM(actual_minutes),0) FROM focus_session " +
                            "WHERE user_id=? AND status='COMPLETED' AND started_at>=? AND started_at<?",
                    (row, index) -> new long[]{row.getLong(1), row.getLong(2)}, userId, from, to);
        }
        if (from != null) {
            return jdbc.queryForObject("SELECT COUNT(*), COALESCE(SUM(actual_minutes),0) FROM focus_session " +
                            "WHERE user_id=? AND status='COMPLETED' AND started_at>=?",
                    (row, index) -> new long[]{row.getLong(1), row.getLong(2)}, userId, from);
        }
        return jdbc.queryForObject("SELECT COUNT(*), COALESCE(SUM(actual_minutes),0) FROM focus_session " +
                        "WHERE user_id=? AND status='COMPLETED'",
                (row, index) -> new long[]{row.getLong(1), row.getLong(2)}, userId);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long sum(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private ZoneId zone(long userId) {
        List<String> rows = jdbc.query("SELECT timezone FROM task_setting WHERE user_id=?", (row, index) -> row.getString(1), userId);
        try {
            return ZoneId.of(rows.isEmpty() || rows.get(0) == null ? "Asia/Shanghai" : rows.get(0));
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private String userOffset(ZoneId zone) {
        return LocalDateTime.now(zone).atZone(zone).getOffset().getId();
    }

    /**
     * 某天 00:00 在「数据库时区」下的本地时间字符串。
     * 用户时区与 DB 时区一致时就是 date + " 00:00:00"；不一致时按瞬时换算。
     */
    private String dayStart(LocalDate date, ZoneId zone) {
        return LocalDateTime.of(date, LocalTime.MIN).atZone(zone)
                .withZoneSameInstant(DB_ZONE).toLocalDateTime().format(SQL_DATETIME);
    }

    private int parseYear(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim().substring(0, 4));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return LocalDate.parse(raw.trim().substring(0, 10));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String color(String raw, int index) {
        return raw == null || raw.isBlank() ? SLICE_COLORS.get(index % SLICE_COLORS.size()) : raw;
    }

    private long round(double value) {
        return Math.round(value);
    }
}
