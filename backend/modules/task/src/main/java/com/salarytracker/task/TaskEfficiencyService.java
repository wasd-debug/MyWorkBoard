package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.task.TaskEfficiencyModels.Countdown;
import com.salarytracker.task.TaskEfficiencyModels.CountdownCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusDaily;
import com.salarytracker.task.TaskEfficiencyModels.FocusFinishCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusSession;
import com.salarytracker.task.TaskEfficiencyModels.FocusSettings;
import com.salarytracker.task.TaskEfficiencyModels.FocusSettingsCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusStartCommand;
import com.salarytracker.task.TaskEfficiencyModels.FocusStats;
import com.salarytracker.task.TaskEfficiencyModels.Habit;
import com.salarytracker.task.TaskEfficiencyModels.HabitCheckin;
import com.salarytracker.task.TaskEfficiencyModels.HabitCheckinCommand;
import com.salarytracker.task.TaskEfficiencyModels.HabitCalendarDay;
import com.salarytracker.task.TaskEfficiencyModels.HabitCommand;
import com.salarytracker.task.TaskEfficiencyModels.HabitStats;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TaskEfficiencyService {
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;
    private final TaskService tasks;
    private final TaskChangeLog changeLog;

    public TaskEfficiencyService(JdbcTemplate jdbc, CurrentUserResolver currentUser, TaskService tasks,
                                 TaskChangeLog changeLog) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.tasks = tasks;
        this.changeLog = changeLog;
    }

    @Transactional
    public FocusSettings focusSettings() {
        long userId = currentUser.id();
        ensureFocusSettings(userId);
        return jdbc.queryForObject("SELECT focus_minutes,short_break_minutes,long_break_minutes,long_break_interval," +
                        "auto_start_break,auto_start_next,revision FROM focus_setting WHERE user_id=?",
                (row, index) -> new FocusSettings(row.getInt(1), row.getInt(2), row.getInt(3), row.getInt(4),
                        row.getBoolean(5), row.getBoolean(6), row.getLong(7)), userId);
    }

    @Transactional
    public FocusSettings updateFocusSettings(FocusSettingsCommand command, String ifMatch) {
        FocusSettings before = focusSettings();
        long expected = revision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("番茄钟设置版本已变化", before.revision());
        int focus = bounded(command.focusMinutes(), before.focusMinutes(), 1, 180, "专注时长");
        int shortBreak = bounded(command.shortBreakMinutes(), before.shortBreakMinutes(), 1, 60, "短休息时长");
        int longBreak = bounded(command.longBreakMinutes(), before.longBreakMinutes(), 1, 120, "长休息时长");
        int interval = bounded(command.longBreakInterval(), before.longBreakInterval(), 2, 12, "长休息间隔");
        int changed = jdbc.update("UPDATE focus_setting SET focus_minutes=?,short_break_minutes=?,long_break_minutes=?," +
                        "long_break_interval=?,auto_start_break=?,auto_start_next=?,revision=revision+1 WHERE user_id=? AND revision=?",
                focus, shortBreak, longBreak, interval, value(command.autoStartBreak(), before.autoStartBreak()),
                value(command.autoStartNext(), before.autoStartNext()), currentUser.id(), expected);
        if (changed == 0) throw new ConflictException("番茄钟设置版本已变化", before.revision());
        return focusSettings();
    }

    public List<FocusSession> focusSessions(String from, String to) {
        long userId = currentUser.id();
        ZoneId zone = zone(userId);
        Instant start = parseDate(from, LocalDate.now(zone).minusDays(30)).atStartOfDay(zone).toInstant();
        Instant end = parseDate(to, LocalDate.now(zone).plusDays(1)).atStartOfDay(zone).toInstant();
        return jdbc.query("SELECT f.*,t.public_id task_public_id,t.title task_title FROM focus_session f " +
                        "LEFT JOIN task t ON t.id=f.task_id WHERE f.user_id=? AND f.started_at>=? AND f.started_at<? " +
                        "ORDER BY f.started_at DESC",
                (row, index) -> focusSession(row), userId, Timestamp.from(start), Timestamp.from(end));
    }

    public FocusSession currentFocus() {
        List<FocusSession> rows = jdbc.query("SELECT f.*,t.public_id task_public_id,t.title task_title FROM focus_session f " +
                        "LEFT JOIN task t ON t.id=f.task_id WHERE f.user_id=? AND f.status='RUNNING'",
                (row, index) -> focusSession(row), currentUser.id());
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public FocusSession startFocus(FocusStartCommand command, String idempotencyKey) {
        long userId = currentUser.id();
        String opKey = text(idempotencyKey, "Idempotency-Key", 120);
        List<FocusSession> replay = jdbc.query("SELECT f.*,t.public_id task_public_id,t.title task_title FROM focus_session f " +
                        "LEFT JOIN task t ON t.id=f.task_id WHERE f.user_id=? AND f.client_op_key=?",
                (row, index) -> focusSession(row), userId, opKey);
        if (!replay.isEmpty()) return replay.get(0);
        if (currentFocus() != null) throw new ConflictException("已有进行中的专注", currentFocus().revision());
        int planned = bounded(command == null ? null : command.plannedMinutes(), focusSettings().focusMinutes(), 1, 180, "专注时长");
        Long taskId = taskId(userId, command == null ? null : command.taskId());
        String publicId = UUID.randomUUID().toString();
        ZoneId zone = zone(userId);
        LocalDate requested = parseDate(command == null ? null : command.requestedDate(), LocalDate.now(zone));
        boolean manual = !requested.equals(LocalDate.now(zone));
        if (requested.isAfter(LocalDate.now(zone))) throw new IllegalArgumentException("不能新增未来日期的专注记录");
        Instant started = manual ? requested.atTime(LocalTime.NOON).atZone(zone).toInstant() : dbNow();
        Instant ended = manual ? started.plusSeconds(planned * 60L) : null;
        String status = manual ? "COMPLETED" : "RUNNING";
        try {
            jdbc.update("INSERT INTO focus_session(public_id,user_id,task_id,planned_minutes,actual_minutes,started_at,ended_at,status,device_label,client_op_key) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?)", publicId, userId, taskId, planned, manual ? planned : 0,
                    Timestamp.from(started), ended == null ? null : Timestamp.from(ended), status,
                    optional(command == null ? null : command.deviceLabel(), 120), opKey);
        } catch (DuplicateKeyException exception) {
            FocusSession running = currentFocus();
            if (running != null) throw new ConflictException("已有进行中的专注", running.revision());
            throw exception;
        }
        if (manual) applyFocusCompletion(userId, publicId, taskId, planned);
        return focusById(userId, publicId);
    }

    @Transactional
    public FocusSession finishFocus(String publicId, FocusFinishCommand command, String ifMatch) {
        long userId = currentUser.id();
        FocusSession before = focusById(userId, publicId);
        long expected = revision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("专注记录版本已变化", before.revision());
        if (!"RUNNING".equals(before.status())) return before;
        String status = enumValue(command == null ? null : command.status(), "COMPLETED", List.of("COMPLETED", "ABORTED"), "status");
        Instant ended = dbNow();
        int actual = Math.max(0, (int) ((ended.toEpochMilli() - Instant.parse(before.startedAt()).toEpochMilli()) / 60000));
        if ("COMPLETED".equals(status)) actual = Math.max(1, Math.min(before.plannedMinutes(), actual));
        int changed = jdbc.update("UPDATE focus_session SET actual_minutes=?,ended_at=?,status=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND status='RUNNING' AND revision=?", actual,
                Timestamp.from(ended), status, publicId, userId, expected);
        if (changed == 0) throw new ConflictException("专注记录版本已变化", before.revision());
        if ("COMPLETED".equals(status)) {
            Long linkedTask = jdbc.queryForObject("SELECT task_id FROM focus_session WHERE public_id=?", Long.class, publicId);
            applyFocusCompletion(userId, publicId, linkedTask, actual);
        }
        return focusById(userId, publicId);
    }

    @Transactional
    public void deleteFocus(String publicId, String ifMatch) {
        long userId = currentUser.id();
        FocusSession before = focusById(userId, publicId);
        long expected = revision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("专注记录版本已变化", before.revision());
        if ("RUNNING".equals(before.status())) throw new IllegalArgumentException("请先结束进行中的专注");
        jdbc.update("DELETE FROM focus_session WHERE public_id=? AND user_id=? AND revision=?", publicId, userId, expected);
    }

    public FocusStats focusStats() {
        long userId = currentUser.id();
        ZoneId zone = zone(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate week = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate month = today.withDayOfMonth(1);
        long[] daily = focusAggregate(userId, today.atStartOfDay(zone).toInstant());
        long[] weekly = focusAggregate(userId, week.atStartOfDay(zone).toInstant());
        long[] monthly = focusAggregate(userId, month.atStartOfDay(zone).toInstant());
        List<FocusDaily> recent = jdbc.query("SELECT DATE(CONVERT_TZ(started_at,'+00:00',?)) day,COUNT(*) total,SUM(actual_minutes) minutes " +
                        "FROM focus_session WHERE user_id=? AND status='COMPLETED' AND started_at>=? GROUP BY day ORDER BY day",
                (row, index) -> new FocusDaily(row.getDate(1).toLocalDate().toString(), row.getLong(2), row.getLong(3)),
                zoneOffset(zone), userId, Timestamp.from(today.minusDays(6).atStartOfDay(zone).toInstant()));
        return new FocusStats(daily[0], daily[1], weekly[0], weekly[1], monthly[0], monthly[1], recent);
    }

    public List<Habit> habits(boolean archived) {
        return jdbc.query("SELECT * FROM habit WHERE user_id=? AND is_archived=? AND deleted_at IS NULL ORDER BY sort_order,id",
                (row, index) -> habit(row), currentUser.id(), archived);
    }

    @Transactional
    public Habit createHabit(HabitCommand command) {
        return createHabitForSync(command, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    @Transactional
    Habit createHabitForSync(HabitCommand command, String publicId, String opId) {
        long userId = currentUser.id();
        HabitValues values = habitValues(command, null, zone(userId));
        jdbc.update("INSERT INTO habit(public_id,user_id,name,icon,color,frequency,target_count,custom_days,remind_at,start_date,is_archived,sort_order) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", publicId, userId, values.name(), values.icon(), values.color(),
                values.frequency(), values.targetCount(), joinDays(values.customDays()), values.remindAt(), Date.valueOf(values.startDate()),
                values.archived(), values.sortOrder());
        Habit saved = habitById(userId, publicId);
        changeLog.append(userId, opId, "habit", publicId, "UPSERT", syncEntity(saved, false));
        return saved;
    }

    @Transactional
    public Habit updateHabit(String publicId, HabitCommand command, String ifMatch) {
        return updateHabitForSync(publicId, command, revision(ifMatch), UUID.randomUUID().toString());
    }

    @Transactional
    Habit updateHabitForSync(String publicId, HabitCommand command, long expected, String opId) {
        long userId = currentUser.id();
        Habit before = habitById(userId, publicId);
        if (before.revision() != expected) throw new ConflictException("习惯版本已变化", before.revision());
        HabitValues values = habitValues(command, before, zone(userId));
        int changed = jdbc.update("UPDATE habit SET name=?,icon=?,color=?,frequency=?,target_count=?,custom_days=?,remind_at=?," +
                        "start_date=?,is_archived=?,sort_order=?,revision=revision+1 WHERE public_id=? AND user_id=? AND revision=? AND deleted_at IS NULL",
                values.name(), values.icon(), values.color(), values.frequency(), values.targetCount(), joinDays(values.customDays()),
                values.remindAt(), Date.valueOf(values.startDate()), values.archived(), values.sortOrder(), publicId, userId, expected);
        if (changed == 0) throw new ConflictException("习惯版本已变化", before.revision());
        Habit saved = habitById(userId, publicId);
        changeLog.append(userId, opId, "habit", publicId, "UPSERT", syncEntity(saved, false));
        return saved;
    }

    @Transactional
    public void deleteHabit(String publicId, String ifMatch) {
        deleteHabitForSync(publicId, revision(ifMatch), UUID.randomUUID().toString());
    }

    @Transactional
    void deleteHabitForSync(String publicId, long expected, String opId) {
        long userId = currentUser.id();
        Habit before = habitById(userId, publicId);
        if (before.revision() != expected) throw new ConflictException("习惯版本已变化", before.revision());
        jdbc.update("UPDATE habit SET deleted_at=CURRENT_TIMESTAMP,revision=revision+1 WHERE public_id=? AND user_id=? AND revision=?",
                publicId, userId, expected);
        changeLog.append(userId, opId, "habit", publicId, "DELETE", syncEntity(before, true));
    }

    @Transactional
    public HabitCheckin checkin(String publicId, HabitCheckinCommand command) {
        long userId = currentUser.id();
        LocalDate date = parseDate(command == null ? null : command.date(), LocalDate.now(zone(userId)));
        HabitCheckin existing = optionalCheckinForDay(userId, publicId, date);
        return checkinForSync(publicId, command, existing == null ? UUID.randomUUID().toString() : existing.publicId(),
                existing == null ? 0 : existing.revision(), UUID.randomUUID().toString());
    }

    @Transactional
    HabitCheckin checkinForSync(String publicId, HabitCheckinCommand command, String checkinId,
                                long baseRevision, String opId) {
        long userId = currentUser.id();
        Habit habit = habitById(userId, publicId);
        LocalDate date = parseDate(command == null ? null : command.date(), LocalDate.now(zone(userId)));
        if (date.isBefore(LocalDate.parse(habit.startDate()))) throw new IllegalArgumentException("补卡日期不能早于习惯开始日期");
        if (date.isAfter(LocalDate.now(zone(userId)))) throw new IllegalArgumentException("不能打卡未来日期");
        String status = enumValue(command == null ? null : command.status(), "DONE", List.of("DONE", "SKIP"), "status");
        int count = "SKIP".equals(status) ? 0 : bounded(command == null ? null : command.count(), 1, 1, 999, "打卡次数");
        HabitCheckin existing = optionalCheckinForDay(userId, publicId, date);
        if (existing == null) {
            if (baseRevision > 0) throw new NotFoundException("习惯打卡不存在");
            jdbc.update("INSERT INTO habit_checkin(public_id,habit_id,user_id,checkin_date,count,status) " +
                            "SELECT ?,id,?,?,?,? FROM habit WHERE public_id=? AND user_id=?",
                    checkinId, userId, Date.valueOf(date), count, status, publicId, userId);
        } else {
            if (!existing.publicId().equals(checkinId) || existing.revision() != baseRevision) {
                throw new ConflictException("习惯打卡版本已变化", existing.revision());
            }
            jdbc.update("UPDATE habit_checkin SET count=?,status=?,revision=revision+1 WHERE public_id=? AND user_id=? AND revision=?",
                    count, status, checkinId, userId, baseRevision);
        }
        HabitCheckin saved = checkinForDay(userId, publicId, date);
        changeLog.append(userId, opId, "habit-checkin", saved.publicId(), "UPSERT", syncEntity(saved, false));
        jdbc.update("INSERT INTO domain_event(event_id,event_type,aggregate_id,user_id,payload_json) VALUES(?,?,?,?,JSON_OBJECT('date',?,'status',?))",
                UUID.randomUUID().toString(), "task.habit.checked", publicId, userId, saved.date(), saved.status());
        return saved;
    }

    public HabitStats habitStats(String publicId, String from, String to) {
        long userId = currentUser.id();
        Habit habit = habitById(userId, publicId);
        ZoneId zone = zone(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate start = parseDate(from, LocalDate.parse(habit.startDate()));
        LocalDate end = parseDate(to, today.plusDays(1));
        List<HabitCheckin> rows = checkins(userId, publicId, start, end);
        Map<LocalDate, HabitCheckin> byDate = new HashMap<>();
        rows.forEach(item -> byDate.put(LocalDate.parse(item.date()), item));
        if ("WEEKLY_N".equals(habit.frequency()) || "MONTHLY_N".equals(habit.frequency())) {
            return periodicHabitStats(habit, rows, today);
        }
        Streak streak = streak(habit, byDate, today);
        int completed30 = 0;
        int target30 = 0;
        for (LocalDate day = today.minusDays(29); !day.isAfter(today); day = day.plusDays(1)) {
            if (!scheduled(habit, day)) continue;
            target30++;
            HabitCheckin item = byDate.get(day);
            if (item != null && !"SKIP".equals(item.status()) && item.count() >= habit.targetCount()) completed30++;
        }
        int monthCompleted = 0;
        int monthTarget = 0;
        for (LocalDate day = today.withDayOfMonth(1); !day.isAfter(today); day = day.plusDays(1)) {
            if (!scheduled(habit, day)) continue;
            monthTarget++;
            HabitCheckin item = byDate.get(day);
            if (item != null && item.count() >= habit.targetCount()) monthCompleted++;
        }
        return new HabitStats(streak.current(), streak.longest(), target30 == 0 ? 0 : completed30 * 100 / target30,
                monthCompleted, monthTarget, rows);
    }

    public List<HabitCalendarDay> habitCalendar(LocalDate from, LocalDate toExclusive) {
        if (from == null || toExclusive == null || !toExclusive.isAfter(from)) throw new IllegalArgumentException("习惯日历范围无效");
        long userId = currentUser.id();
        List<Habit> active = habits(false);
        Map<String, Integer> done = new HashMap<>();
        jdbc.query("SELECT c.checkin_date,COUNT(*) FROM habit_checkin c JOIN habit h ON h.id=c.habit_id " +
                        "WHERE c.user_id=? AND c.checkin_date>=? AND c.checkin_date<? AND c.status='DONE' " +
                        "AND c.count>=h.target_count AND h.deleted_at IS NULL AND h.is_archived=FALSE GROUP BY c.checkin_date",
                (RowCallbackHandler) row -> done.put(row.getDate(1).toLocalDate().toString(), row.getInt(2)),
                userId, Date.valueOf(from), Date.valueOf(toExclusive));
        List<HabitCalendarDay> result = new ArrayList<>();
        for (LocalDate date = from; date.isBefore(toExclusive); date = date.plusDays(1)) {
            LocalDate current = date;
            int total = (int) active.stream().filter(item -> scheduled(item, current)).count();
            if (total > 0 || done.containsKey(date.toString())) result.add(new HabitCalendarDay(date.toString(), done.getOrDefault(date.toString(), 0), total));
        }
        return result;
    }

    public List<Countdown> countdowns() {
        return jdbc.query("SELECT * FROM countdown WHERE user_id=? AND deleted_at IS NULL ORDER BY is_pinned DESC,target_date,id",
                (row, index) -> countdown(row), currentUser.id());
    }

    @Transactional
    public Countdown createCountdown(CountdownCommand command) {
        return createCountdownForSync(command, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    @Transactional
    Countdown createCountdownForSync(CountdownCommand command, String publicId, String opId) {
        long userId = currentUser.id();
        CountdownValues values = countdownValues(command, null);
        jdbc.update("INSERT INTO countdown(public_id,user_id,title,target_date,kind,repeat_yearly,is_pinned,color,note) VALUES(?,?,?,?,?,?,?,?,?)",
                publicId, userId, values.title(), Date.valueOf(values.targetDate()), values.kind(), values.repeatYearly(),
                values.pinned(), values.color(), values.note());
        Countdown saved = countdownById(userId, publicId);
        changeLog.append(userId, opId, "countdown", publicId, "UPSERT", syncEntity(saved, false));
        return saved;
    }

    @Transactional
    public Countdown updateCountdown(String publicId, CountdownCommand command, String ifMatch) {
        return updateCountdownForSync(publicId, command, revision(ifMatch), UUID.randomUUID().toString());
    }

    @Transactional
    Countdown updateCountdownForSync(String publicId, CountdownCommand command, long expected, String opId) {
        long userId = currentUser.id();
        Countdown before = countdownById(userId, publicId);
        if (before.revision() != expected) throw new ConflictException("倒数日版本已变化", before.revision());
        CountdownValues values = countdownValues(command, before);
        int changed = jdbc.update("UPDATE countdown SET title=?,target_date=?,kind=?,repeat_yearly=?,is_pinned=?,color=?,note=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND revision=? AND deleted_at IS NULL", values.title(), Date.valueOf(values.targetDate()),
                values.kind(), values.repeatYearly(), values.pinned(), values.color(), values.note(), publicId, userId, expected);
        if (changed == 0) throw new ConflictException("倒数日版本已变化", before.revision());
        Countdown saved = countdownById(userId, publicId);
        changeLog.append(userId, opId, "countdown", publicId, "UPSERT", syncEntity(saved, false));
        return saved;
    }

    @Transactional
    public void deleteCountdown(String publicId, String ifMatch) {
        deleteCountdownForSync(publicId, revision(ifMatch), UUID.randomUUID().toString());
    }

    @Transactional
    void deleteCountdownForSync(String publicId, long expected, String opId) {
        long userId = currentUser.id();
        Countdown before = countdownById(userId, publicId);
        if (before.revision() != expected) throw new ConflictException("倒数日版本已变化", before.revision());
        jdbc.update("UPDATE countdown SET deleted_at=CURRENT_TIMESTAMP,revision=revision+1 WHERE public_id=? AND user_id=? AND revision=?",
                publicId, userId, expected);
        changeLog.append(userId, opId, "countdown", publicId, "DELETE", syncEntity(before, true));
    }

    private FocusSession focusById(long userId, String publicId) {
        List<FocusSession> rows = jdbc.query("SELECT f.*,t.public_id task_public_id,t.title task_title FROM focus_session f " +
                        "LEFT JOIN task t ON t.id=f.task_id WHERE f.user_id=? AND f.public_id=?",
                (row, index) -> focusSession(row), userId, publicId);
        if (rows.isEmpty()) throw new NotFoundException("专注记录不存在");
        return rows.get(0);
    }

    private FocusSession focusSession(ResultSet row) throws SQLException {
        Instant started = row.getTimestamp("started_at").toInstant();
        return new FocusSession(row.getString("public_id"), row.getString("task_public_id"), row.getString("task_title"),
                row.getInt("planned_minutes"), row.getInt("actual_minutes"), started.toString(),
                started.plusSeconds(row.getInt("planned_minutes") * 60L).toString(), instant(row.getTimestamp("ended_at")),
                row.getString("status"), row.getString("device_label"), row.getLong("revision"));
    }

    private void applyFocusCompletion(long userId, String publicId, Long taskId, int actual) {
        if (taskId != null && actual > 0) {
            jdbc.update("UPDATE task SET focus_minutes=focus_minutes+?,revision=revision+1 WHERE id=? AND user_id=?", actual, taskId, userId);
            String taskPublicId = jdbc.queryForObject("SELECT public_id FROM task WHERE id=? AND user_id=?", String.class, taskId, userId);
            changeLog.append(userId, UUID.randomUUID().toString(), taskPublicId, "UPSERT",
                    TaskService.syncEntity(tasks.currentForSync(taskPublicId), false));
        }
        jdbc.update("INSERT INTO domain_event(event_id,event_type,aggregate_id,user_id,payload_json) VALUES(?,?,?,?,JSON_OBJECT('actualMinutes',?))",
                UUID.randomUUID().toString(), "task.focus.completed", publicId, userId, actual);
    }

    private long[] focusAggregate(long userId, Instant from) {
        return jdbc.queryForObject("SELECT COUNT(*),COALESCE(SUM(actual_minutes),0) FROM focus_session WHERE user_id=? AND status='COMPLETED' AND started_at>=?",
                (row, index) -> new long[]{row.getLong(1), row.getLong(2)}, userId, Timestamp.from(from));
    }

    private Habit habitById(long userId, String publicId) {
        List<Habit> rows = jdbc.query("SELECT * FROM habit WHERE user_id=? AND public_id=? AND deleted_at IS NULL",
                (row, index) -> habit(row), userId, publicId);
        if (rows.isEmpty()) throw new NotFoundException("习惯不存在");
        return rows.get(0);
    }

    Habit habitForSync(String publicId) { return habitById(currentUser.id(), publicId); }

    private Habit habit(ResultSet row) throws SQLException {
        return new Habit(row.getString("public_id"), row.getString("name"), row.getString("icon"), row.getString("color"),
                row.getString("frequency"), row.getInt("target_count"), parseDays(row.getString("custom_days")),
                row.getString("remind_at"), row.getDate("start_date").toLocalDate().toString(), row.getBoolean("is_archived"),
                row.getInt("sort_order"), row.getLong("revision"));
    }

    private HabitCheckin checkinForDay(long userId, String publicId, LocalDate date) {
        return jdbc.queryForObject("SELECT c.public_id,h.public_id habit_public_id,c.checkin_date,c.count,c.status,c.revision " +
                        "FROM habit_checkin c JOIN habit h ON h.id=c.habit_id WHERE c.user_id=? AND h.public_id=? AND c.checkin_date=?",
                (row, index) -> new HabitCheckin(row.getString(1), row.getString(2), row.getDate(3).toLocalDate().toString(),
                        row.getInt(4), row.getString(5), row.getLong(6)), userId, publicId, Date.valueOf(date));
    }

    HabitCheckin checkinForSync(String publicId) {
        List<HabitCheckin> rows = jdbc.query("SELECT c.public_id,h.public_id habit_public_id,c.checkin_date,c.count,c.status,c.revision " +
                        "FROM habit_checkin c JOIN habit h ON h.id=c.habit_id WHERE c.user_id=? AND c.public_id=?",
                (row, index) -> new HabitCheckin(row.getString(1), row.getString(2), row.getDate(3).toLocalDate().toString(),
                        row.getInt(4), row.getString(5), row.getLong(6)), currentUser.id(), publicId);
        if (rows.isEmpty()) throw new NotFoundException("习惯打卡不存在");
        return rows.get(0);
    }

    private HabitCheckin optionalCheckinForDay(long userId, String publicId, LocalDate date) {
        List<HabitCheckin> rows = jdbc.query("SELECT c.public_id,h.public_id habit_public_id,c.checkin_date,c.count,c.status,c.revision " +
                        "FROM habit_checkin c JOIN habit h ON h.id=c.habit_id WHERE c.user_id=? AND h.public_id=? AND c.checkin_date=?",
                (row, index) -> new HabitCheckin(row.getString(1), row.getString(2), row.getDate(3).toLocalDate().toString(),
                        row.getInt(4), row.getString(5), row.getLong(6)), userId, publicId, Date.valueOf(date));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<HabitCheckin> checkins(long userId, String publicId, LocalDate from, LocalDate to) {
        return jdbc.query("SELECT c.public_id,h.public_id habit_public_id,c.checkin_date,c.count,c.status,c.revision " +
                        "FROM habit_checkin c JOIN habit h ON h.id=c.habit_id WHERE c.user_id=? AND h.public_id=? " +
                        "AND c.checkin_date>=? AND c.checkin_date<? ORDER BY c.checkin_date",
                (row, index) -> new HabitCheckin(row.getString(1), row.getString(2), row.getDate(3).toLocalDate().toString(),
                        row.getInt(4), row.getString(5), row.getLong(6)), userId, publicId, Date.valueOf(from), Date.valueOf(to));
    }

    private Streak streak(Habit habit, Map<LocalDate, HabitCheckin> rows, LocalDate today) {
        int current = 0;
        int longest = 0;
        int run = 0;
        LocalDate start = LocalDate.parse(habit.startDate());
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if (!scheduled(habit, day)) continue;
            HabitCheckin item = rows.get(day);
            if (item != null && "SKIP".equals(item.status())) continue;
            boolean done = item != null && item.count() >= habit.targetCount();
            run = done ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        for (LocalDate day = today; !day.isBefore(start); day = day.minusDays(1)) {
            if (!scheduled(habit, day)) continue;
            HabitCheckin item = rows.get(day);
            if (item != null && "SKIP".equals(item.status())) continue;
            if (item != null && item.count() >= habit.targetCount()) current++; else break;
        }
        return new Streak(current, longest);
    }

    private HabitStats periodicHabitStats(Habit habit, List<HabitCheckin> rows, LocalDate today) {
        boolean weekly = "WEEKLY_N".equals(habit.frequency());
        LocalDate habitStart = LocalDate.parse(habit.startDate());
        LocalDate first = weekly ? habitStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : habitStart.withDayOfMonth(1);
        LocalDate current = weekly ? today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : today.withDayOfMonth(1);
        Map<LocalDate, Integer> totals = new HashMap<>();
        for (HabitCheckin row : rows) {
            if (!"DONE".equals(row.status())) continue;
            LocalDate day = LocalDate.parse(row.date());
            LocalDate period = weekly ? day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : day.withDayOfMonth(1);
            totals.merge(period, row.count(), Integer::sum);
        }
        int run = 0;
        int longest = 0;
        int currentStreak = 0;
        for (LocalDate period = first; !period.isAfter(current); period = weekly ? period.plusWeeks(1) : period.plusMonths(1)) {
            boolean done = totals.getOrDefault(period, 0) >= habit.targetCount();
            run = done ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        for (LocalDate period = current; !period.isBefore(first); period = weekly ? period.minusWeeks(1) : period.minusMonths(1)) {
            if (totals.getOrDefault(period, 0) >= habit.targetCount()) currentStreak++; else break;
        }
        LocalDate recentStart = today.minusDays(29);
        int recentCompleted = rows.stream().filter(item -> "DONE".equals(item.status()) && !LocalDate.parse(item.date()).isBefore(recentStart))
                .mapToInt(HabitCheckin::count).sum();
        int recentTarget = weekly ? habit.targetCount() * 5 : habit.targetCount();
        LocalDate monthStart = today.withDayOfMonth(1);
        int monthCompleted = rows.stream().filter(item -> "DONE".equals(item.status()) && !LocalDate.parse(item.date()).isBefore(monthStart))
                .mapToInt(HabitCheckin::count).sum();
        int monthTarget = weekly ? habit.targetCount() * ((today.getDayOfMonth() + 6) / 7) : habit.targetCount();
        return new HabitStats(currentStreak, longest, Math.min(100, recentCompleted * 100 / Math.max(1, recentTarget)),
                monthCompleted, monthTarget, rows);
    }

    private boolean scheduled(Habit habit, LocalDate day) {
        if (day.isBefore(LocalDate.parse(habit.startDate()))) return false;
        if ("CUSTOM".equals(habit.frequency())) return habit.customDays().contains(day.getDayOfWeek().getValue());
        return true;
    }

    static TaskModels.SyncEntity syncEntity(Habit item, boolean deleted) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("name", item.name()); extra.put("icon", item.icon()); extra.put("color", item.color());
        extra.put("frequency", item.frequency()); extra.put("targetCount", item.targetCount());
        extra.put("customDays", item.customDays()); extra.put("remindAt", item.remindAt());
        extra.put("startDate", item.startDate()); extra.put("archived", item.archived()); extra.put("sortOrder", item.sortOrder());
        return efficiencyEntity(item.publicId(), item.revision() + (deleted ? 1 : 0), deleted, extra);
    }

    static TaskModels.SyncEntity syncEntity(HabitCheckin item, boolean deleted) {
        return efficiencyEntity(item.publicId(), item.revision() + (deleted ? 1 : 0), deleted,
                Map.of("habitId", item.habitId(), "date", item.date(), "count", item.count(), "status", item.status()));
    }

    static TaskModels.SyncEntity syncEntity(Countdown item, boolean deleted) {
        return efficiencyEntity(item.publicId(), item.revision() + (deleted ? 1 : 0), deleted,
                Map.of("title", item.title(), "targetDate", item.targetDate(), "kind", item.kind(),
                        "repeatYearly", item.repeatYearly(), "pinned", item.pinned(), "color", item.color(), "note", item.note()));
    }

    private static TaskModels.SyncEntity efficiencyEntity(String id, long revision, boolean deleted, Map<String, Object> extra) {
        return new TaskModels.SyncEntity(id, null, null, null, null, null, null, null, false, null, null, null,
                null, revision, deleted, null, List.of(), List.of(), null, null, null, null, null, null, null, null,
                null, null, null, null, null, extra);
    }

    private Countdown countdownById(long userId, String publicId) {
        List<Countdown> rows = jdbc.query("SELECT * FROM countdown WHERE user_id=? AND public_id=? AND deleted_at IS NULL",
                (row, index) -> countdown(row), userId, publicId);
        if (rows.isEmpty()) throw new NotFoundException("倒数日不存在");
        return rows.get(0);
    }

    Countdown countdownForSync(String publicId) { return countdownById(currentUser.id(), publicId); }

    private Countdown countdown(ResultSet row) throws SQLException {
        return new Countdown(row.getString("public_id"), row.getString("title"), row.getDate("target_date").toLocalDate().toString(),
                row.getString("kind"), row.getBoolean("repeat_yearly"), row.getBoolean("is_pinned"), row.getString("color"),
                row.getString("note"), row.getLong("revision"));
    }

    private HabitValues habitValues(HabitCommand command, Habit before, ZoneId zone) {
        if (command == null) throw new IllegalArgumentException("习惯内容不能为空");
        String name = command.name() == null && before != null ? before.name() : text(command.name(), "习惯名称", 120);
        String frequency = enumValue(command.frequency(), before == null ? "DAILY" : before.frequency(),
                List.of("DAILY", "WEEKLY_N", "MONTHLY_N", "CUSTOM"), "frequency");
        List<Integer> days = command.customDays() == null ? before == null ? List.of() : before.customDays() : command.customDays();
        if (days.stream().anyMatch(day -> day < 1 || day > 7)) throw new IllegalArgumentException("自定义星期必须在 1 到 7 之间");
        if ("CUSTOM".equals(frequency) && days.isEmpty()) throw new IllegalArgumentException("自定义频率至少选择一天");
        String start = command.startDate() == null && before != null ? before.startDate()
                : parseDate(command.startDate(), LocalDate.now(zone)).toString();
        String remind = command.remindAt() == null ? before == null ? null : before.remindAt() : time(command.remindAt());
        return new HabitValues(name, optional(command.icon() == null && before != null ? before.icon() : command.icon(), 32, "check"),
                color(command.color() == null && before != null ? before.color() : command.color()), frequency,
                bounded(command.targetCount(), before == null ? 1 : before.targetCount(), 1, 999, "目标次数"), days, remind,
                LocalDate.parse(start), value(command.archived(), before != null && before.archived()),
                command.sortOrder() == null ? before == null ? 0 : before.sortOrder() : command.sortOrder());
    }

    private CountdownValues countdownValues(CountdownCommand command, Countdown before) {
        if (command == null) throw new IllegalArgumentException("倒数日内容不能为空");
        String title = command.title() == null && before != null ? before.title() : text(command.title(), "标题", 160);
        String date = command.targetDate() == null && before != null ? before.targetDate() : parseDate(command.targetDate(), null).toString();
        return new CountdownValues(title, LocalDate.parse(date), enumValue(command.kind(), before == null ? "COUNTDOWN" : before.kind(),
                List.of("COUNTDOWN", "ANNIVERSARY"), "kind"), value(command.repeatYearly(), before != null && before.repeatYearly()),
                value(command.pinned(), before != null && before.pinned()), color(command.color() == null && before != null ? before.color() : command.color()),
                optional(command.note() == null && before != null ? before.note() : command.note(), 1000));
    }

    private void ensureFocusSettings(long userId) {
        jdbc.update("INSERT IGNORE INTO focus_setting(user_id) VALUES(?)", userId);
    }

    private Long taskId(long userId, String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        List<Long> rows = jdbc.query("SELECT id FROM task WHERE public_id=? AND user_id=? AND deleted=FALSE",
                (row, index) -> row.getLong(1), publicId, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("关联任务不存在");
        return rows.get(0);
    }

    private ZoneId zone(long userId) {
        List<String> rows = jdbc.query("SELECT timezone FROM task_setting WHERE user_id=?", (row, index) -> row.getString(1), userId);
        try { return ZoneId.of(rows.isEmpty() ? "Asia/Shanghai" : rows.get(0)); }
        catch (Exception ignored) { return ZoneId.of("Asia/Shanghai"); }
    }

    private Instant dbNow() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(3)", Timestamp.class).toInstant();
    }

    private long revision(String raw) {
        try { return Long.parseLong(text(raw, "If-Match", 40).replace("W/", "").replace("\"", "")); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("If-Match 必须是 revision"); }
    }

    private int bounded(Integer input, int fallback, int min, int max, String field) {
        int value = input == null ? fallback : input;
        if (value < min || value > max) throw new IllegalArgumentException(field + "必须在 " + min + " 到 " + max + " 之间");
        return value;
    }

    private String enumValue(String raw, String fallback, List<String> allowed, String field) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) throw new IllegalArgumentException(field + " 不支持");
        return value;
    }

    private String text(String raw, String field, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > max) throw new IllegalArgumentException(field + "必填且长度不能超过 " + max);
        return value;
    }

    private String optional(String raw, int max) { return optional(raw, max, ""); }
    private String optional(String raw, int max, String fallback) {
        String value = raw == null || raw.isBlank() ? fallback : raw.trim();
        if (value.length() > max) throw new IllegalArgumentException("字段长度不能超过 " + max);
        return value;
    }

    private String color(String raw) {
        String value = optional(raw, 32, "#2f746f");
        if (!value.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("颜色必须是六位十六进制值");
        return value;
    }

    private String time(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return LocalTime.parse(raw).withSecond(0).withNano(0).toString(); }
        catch (Exception exception) { throw new IllegalArgumentException("提醒时间格式无效"); }
    }

    private LocalDate parseDate(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            if (fallback != null) return fallback;
            throw new IllegalArgumentException("日期必填");
        }
        try { return LocalDate.parse(raw); }
        catch (Exception exception) { throw new IllegalArgumentException("日期格式无效"); }
    }

    private boolean value(Boolean input, boolean fallback) { return input == null ? fallback : input; }
    private String instant(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
    private String zoneOffset(ZoneId zone) { return LocalDateTime.now(zone).atZone(zone).getOffset().getId(); }
    private String joinDays(List<Integer> days) { return days == null || days.isEmpty() ? null : String.join(",", days.stream().map(String::valueOf).toList()); }
    private List<Integer> parseDays(String value) {
        if (value == null || value.isBlank()) return List.of();
        List<Integer> result = new ArrayList<>();
        for (String item : value.split(",")) result.add(Integer.parseInt(item));
        return result;
    }

    private record HabitValues(String name, String icon, String color, String frequency, int targetCount,
                               List<Integer> customDays, String remindAt, LocalDate startDate, boolean archived,
                               int sortOrder) { }
    private record CountdownValues(String title, LocalDate targetDate, String kind, boolean repeatYearly,
                                   boolean pinned, String color, String note) { }
    private record Streak(int current, int longest) { }
}
