package com.salarytracker.worktime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.worktime.WorktimeModels.Basis;
import com.salarytracker.worktime.WorktimeModels.DeletedResource;
import com.salarytracker.worktime.WorktimeModels.MonthlySalary;
import com.salarytracker.worktime.WorktimeModels.RecordCommand;
import com.salarytracker.worktime.WorktimeModels.RecordPreview;
import com.salarytracker.worktime.WorktimeModels.Settings;
import com.salarytracker.worktime.WorktimeModels.SettingsUpdate;
import com.salarytracker.worktime.WorktimeModels.WorkRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorktimeService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String CALC_VERSION = "phase0-v2-day-type";
    private static final String RECORD_COLUMNS = "id, date, TIME_FORMAT(start_time, '%H:%i') start_time, " +
            "IFNULL(TIME_FORMAT(end_time, '%H:%i'), '') end_time, rest_min, overtime_min, " +
            "real_hourly_wage, note, calc_version, timezone, revision";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserResolver currentUser;
    private final WorkdayCalendar workdayCalendar;

    public WorktimeService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, CurrentUserResolver currentUser) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
        this.workdayCalendar = new WorkdayCalendar(jdbcTemplate, objectMapper);
    }

    public Settings readSettings() {
        long userId = currentUser.id();
        List<SettingRow> rows = jdbcTemplate.query(
                "SELECT salary_pre, salary_post, basis, TIME_FORMAT(work_start, '%H:%i') work_start, " +
                        "TIME_FORMAT(work_end, '%H:%i') work_end, lunch_min, days_per_month, auto_days, revision " +
                        "FROM work_setting WHERE user_id = ?",
                (result, rowNum) -> settingRow(result), userId);
        SettingRow row = rows.isEmpty() ? SettingRow.defaults() : rows.get(0);
        Map<String, MonthlySalary> salaries = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT month_key, salary_pre, salary_post FROM salary_monthly WHERE user_id = ? ORDER BY month_key",
                (result, rowNum) -> new MonthlySalaryRow(result.getString("month_key"),
                        new MonthlySalary(result.getBigDecimal("salary_pre"), result.getBigDecimal("salary_post"))),
                userId).forEach(monthly -> salaries.put(monthly.month(), monthly.salary()));
        return new Settings(row.salaryPre(), row.salaryPost(), row.basis(), row.workStart(), row.workEnd(),
                row.lunchMin(), row.daysPerMonth(), row.autoDays(), salaries, row.revision());
    }

    @Transactional
    public Settings writeSettings(SettingsUpdate input, String ifMatch) {
        long userId = currentUser.id();
        checkRevision(ifMatch, settingRevision(userId));
        Settings current = readSettings();
        SettingsUpdate values = input == null
                ? new SettingsUpdate(null, null, null, null, null, null, null, null, null)
                : input;
        Basis basis = values.basis() == null ? current.basis() : values.basis();
        String workStart = validTime(defaultText(values.workStart(), current.workStart()), false);
        String workEnd = validTime(defaultText(values.workEnd(), current.workEnd()), false);
        int lunch = bounded(values.lunchMin(), current.lunchMin(), 0, 600);
        BigDecimal days = nonNegative(values.daysPerMonth(), current.daysPerMonth());
        BigDecimal pre = nonNegative(values.salaryPre(), current.salaryPre());
        BigDecimal post = nonNegative(values.salaryPost(), current.salaryPost());
        boolean autoDays = values.autoDays() == null ? current.autoDays() : values.autoDays();
        jdbcTemplate.update("INSERT INTO work_setting (user_id, salary_pre, salary_post, basis, work_start, work_end, lunch_min, days_per_month, auto_days, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE salary_pre = VALUES(salary_pre), salary_post = VALUES(salary_post), basis = VALUES(basis), work_start = VALUES(work_start), work_end = VALUES(work_end), lunch_min = VALUES(lunch_min), days_per_month = VALUES(days_per_month), auto_days = VALUES(auto_days), revision = revision + 1",
                userId, pre, post, basis.value(), Time.valueOf(toSqlTime(workStart)), Time.valueOf(toSqlTime(workEnd)), lunch, days, autoDays);
        if (values.salaries() != null) {
            jdbcTemplate.update("DELETE FROM salary_monthly WHERE user_id = ?", userId);
            values.salaries().forEach((month, salary) -> {
                if (!month.matches("\\d{4}-\\d{2}") || salary == null) return;
                jdbcTemplate.update("INSERT INTO salary_monthly (user_id, month_key, salary_pre, salary_post) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE salary_pre = VALUES(salary_pre), salary_post = VALUES(salary_post), revision = revision + 1",
                        userId, month, nonNegative(salary.pre(), BigDecimal.ZERO), nonNegative(salary.post(), BigDecimal.ZERO));
            });
        }
        return readSettings();
    }

    public List<WorkRecord> listRecords(String from, String to, int limit) {
        return listRecords(from, to, limit, 0);
    }

    public List<WorkRecord> listRecords(String from, String to, int limit, int offset) {
        long userId = currentUser.id();
        StringBuilder sql = new StringBuilder("SELECT ").append(RECORD_COLUMNS)
                .append(" FROM work_record WHERE user_id = ? AND deleted = FALSE");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (from != null && !from.isBlank()) {
            LocalDate.parse(from, DATE);
            sql.append(" AND date >= ?");
            args.add(from);
        }
        if (to != null && !to.isBlank()) {
            LocalDate.parse(to, DATE);
            sql.append(" AND date <= ?");
            args.add(to);
        }
        sql.append(" ORDER BY date DESC LIMIT ? OFFSET ?");
        args.add(Math.min(Math.max(limit, 1), 200));
        args.add(Math.max(offset, 0));
        return jdbcTemplate.query(sql.toString(), (result, rowNum) -> workRecord(result), args.toArray());
    }

    @Transactional
    public WorkRecord createRecord(RecordCommand body) {
        return createRecord(body, null);
    }

    public RecordPreview previewCreateRecord(RecordCommand body) {
        String date = requiredDate(body == null ? null : body.date());
        String start = validTime(body.start(), false);
        String end = validTime(body.end(), true);
        int rest = bounded(body.rest(), 0, 0, 600);
        String note = body.note() == null ? "" : body.note();
        Calculation calculation = calculate(date, start, end, rest);
        return new RecordPreview(date, start, end, rest, calculation.overtimeMin(),
                calculation.realHourlyWage(), note);
    }

    @Transactional
    public WorkRecord createRecord(RecordCommand body, String idempotencyKey) {
        long userId = currentUser.id();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            List<String> previous = jdbcTemplate.query(
                    "SELECT response_json FROM idempotency_key WHERE user_id = ? AND op_key = ?",
                    (result, rowNum) -> result.getString("response_json"), userId, idempotencyKey.trim());
            if (!previous.isEmpty()) {
                try {
                    return objectMapper.readValue(previous.get(0), WorkRecord.class);
                } catch (Exception ignored) {
                    // A stale idempotency row should not block a valid retry.
                }
            }
        }
        String date = requiredDate(body == null ? null : body.date());
        List<ExistingRecord> existing = jdbcTemplate.query(
                "SELECT id, revision, deleted FROM work_record WHERE user_id = ? AND date = ?",
                (result, rowNum) -> new ExistingRecord(result.getLong("id"), result.getLong("revision"), result.getBoolean("deleted")),
                userId, date);
        if (!existing.isEmpty() && !existing.get(0).deleted()) {
            throw new ConflictException("该日期已有记录", existing.get(0).revision());
        }
        WorkRecord result = existing.isEmpty()
                ? saveRecord(userId, null, body)
                : saveRecord(userId, existing.get(0).id(), body);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            jdbcTemplate.update("INSERT IGNORE INTO idempotency_key (user_id, op_key, response_json) VALUES (?, ?, ?)",
                    userId, idempotencyKey.trim(), json(result));
        }
        return result;
    }

    @Transactional
    public WorkRecord updateRecord(long id, RecordCommand body, String ifMatch) {
        long userId = currentUser.id();
        List<CurrentRecord> rows = jdbcTemplate.query(
                "SELECT date, TIME_FORMAT(start_time, '%H:%i') start_time, IFNULL(TIME_FORMAT(end_time, '%H:%i'), '') end_time, rest_min, note, revision FROM work_record WHERE id = ? AND user_id = ? AND deleted = FALSE",
                (result, rowNum) -> new CurrentRecord(result.getString("date"), result.getString("start_time"),
                        result.getString("end_time"), result.getInt("rest_min"), result.getString("note"), result.getLong("revision")),
                id, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("记录不存在");
        CurrentRecord current = rows.get(0);
        checkRevision(ifMatch, current.revision());
        RecordCommand update = body == null ? new RecordCommand(null, null, null, null, null) : body;
        RecordCommand merged = new RecordCommand(
                defaultText(update.date(), current.date()), defaultText(update.start(), current.start()),
                update.end() == null ? current.end() : update.end(),
                update.rest() == null ? current.rest() : update.rest(),
                update.note() == null ? current.note() : update.note());
        return saveRecord(userId, id, merged);
    }

    @Transactional
    public DeletedResource deleteRecord(long id, String ifMatch) {
        long userId = currentUser.id();
        List<Long> revisions = jdbcTemplate.query(
                "SELECT revision FROM work_record WHERE id = ? AND user_id = ? AND deleted = FALSE",
                (result, rowNum) -> result.getLong("revision"), id, userId);
        if (revisions.isEmpty()) throw new IllegalArgumentException("记录不存在");
        long revision = revisions.get(0);
        checkRevision(ifMatch, revision);
        jdbcTemplate.update("UPDATE work_record SET deleted = TRUE, revision = revision + 1 WHERE id = ? AND user_id = ?", id, userId);
        return new DeletedResource(id, revision + 1, true);
    }

    private WorkRecord saveRecord(long userId, Long id, RecordCommand body) {
        String date = requiredDate(body == null ? null : body.date());
        String start = validTime(body.start(), false);
        String end = validTime(body.end(), true);
        int rest = bounded(body.rest(), 0, 0, 600);
        String note = body.note() == null ? "" : body.note();
        Calculation calculation = calculate(date, start, end, rest);
        SalarySnapshot snapshot = new SalarySnapshot(readSettings().basis(), calculation.salary());
        if (id == null) {
            jdbcTemplate.update("INSERT INTO work_record (user_id, date, start_time, end_time, rest_min, overtime_min, real_hourly_wage, note, salary_snapshot, calc_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    userId, date, Time.valueOf(toSqlTime(start)), end.isBlank() ? null : Time.valueOf(toSqlTime(end)),
                    rest, calculation.overtimeMin(), calculation.realHourlyWage(), note, json(snapshot), CALC_VERSION);
            id = jdbcTemplate.queryForObject("SELECT id FROM work_record WHERE user_id = ? AND date = ?", Long.class, userId, date);
        } else {
            jdbcTemplate.update("UPDATE work_record SET date = ?, start_time = ?, end_time = ?, rest_min = ?, overtime_min = ?, real_hourly_wage = ?, note = ?, salary_snapshot = ?, calc_version = ?, deleted = FALSE, revision = revision + 1 WHERE id = ? AND user_id = ?",
                    date, Time.valueOf(toSqlTime(start)), end.isBlank() ? null : Time.valueOf(toSqlTime(end)), rest,
                    calculation.overtimeMin(), calculation.realHourlyWage(), note, json(snapshot), CALC_VERSION, id, userId);
        }
        return jdbcTemplate.queryForObject("SELECT " + RECORD_COLUMNS + " FROM work_record WHERE id = ? AND user_id = ?",
                (result, rowNum) -> workRecord(result), id, userId);
    }

    private Calculation calculate(String date, String startText, String endText, int rest) {
        Settings settings = readSettings();
        MonthlySalary monthly = settings.salaries().get(date.substring(0, 7));
        BigDecimal salary = monthly == null
                ? (settings.basis() == Basis.PRE ? settings.salaryPre() : settings.salaryPost())
                : (settings.basis() == Basis.PRE ? monthly.pre() : monthly.post());
        WorktimeCalculator.Result result = WorktimeCalculator.calculate(
                startText, endText, settings.lunchMin(), rest, settings.workStart(), settings.workEnd(),
                salary, settings.daysPerMonth(), workdayCalendar.isOffDay(LocalDate.parse(date, DATE)));
        return new Calculation(result.overtimeMin(), result.realHourlyWage(), salary);
    }

    private SettingRow settingRow(ResultSet result) throws SQLException {
        return new SettingRow(result.getBigDecimal("salary_pre"), result.getBigDecimal("salary_post"),
                Basis.from(result.getString("basis")), result.getString("work_start"), result.getString("work_end"),
                result.getInt("lunch_min"), result.getBigDecimal("days_per_month"), result.getBoolean("auto_days"),
                result.getLong("revision"));
    }

    private WorkRecord workRecord(ResultSet result) throws SQLException {
        return new WorkRecord(result.getLong("id"), result.getString("date"), result.getString("start_time"),
                result.getString("end_time"), result.getInt("rest_min"), result.getInt("overtime_min"),
                result.getBigDecimal("real_hourly_wage"), result.getString("note"), result.getString("calc_version"),
                result.getString("timezone"), result.getLong("revision"));
    }

    private long settingRevision(long userId) {
        List<Long> rows = jdbcTemplate.query("SELECT revision FROM work_setting WHERE user_id = ?",
                (result, rowNum) -> result.getLong("revision"), userId);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private void checkRevision(String ifMatch, long revision) {
        if (ifMatch == null || ifMatch.isBlank()) return;
        String value = ifMatch.trim().replace("W/", "").replace("\"", "");
        try {
            if (Long.parseLong(value) != revision) throw new ConflictException("资源版本已变化", revision);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match 必须是 revision");
        }
    }

    private String requiredDate(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("date 必填");
        LocalDate.parse(value, DATE);
        return value;
    }

    private String validTime(String raw, boolean blankAllowed) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank() && blankAllowed) return "";
        if (value.isBlank()) throw new IllegalArgumentException("start 必须是 HH:mm");
        String normalized = value.length() >= 5 ? value.substring(0, 5) : value;
        LocalTime.parse(normalized);
        return normalized;
    }

    private String toSqlTime(String value) {
        return value.length() == 5 ? value + ":00" : value;
    }

    private int bounded(Integer value, int fallback, int min, int max) {
        return value == null ? fallback : Math.min(max, Math.max(min, value));
    }

    private BigDecimal nonNegative(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value.max(BigDecimal.ZERO);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化工时资源", exception);
        }
    }

    private record SettingRow(BigDecimal salaryPre, BigDecimal salaryPost, Basis basis, String workStart,
                              String workEnd, int lunchMin, BigDecimal daysPerMonth, boolean autoDays,
                              long revision) {
        private static SettingRow defaults() {
            return new SettingRow(BigDecimal.ZERO, BigDecimal.ZERO, Basis.POST, "09:00", "18:00", 90,
                    new BigDecimal("21.75"), true, 0);
        }
    }

    private record ExistingRecord(long id, long revision, boolean deleted) {
    }

    private record MonthlySalaryRow(String month, MonthlySalary salary) {
    }

    private record CurrentRecord(String date, String start, String end, int rest, String note, long revision) {
    }

    private record Calculation(int overtimeMin, BigDecimal realHourlyWage, BigDecimal salary) {
    }

    private record SalarySnapshot(Basis basis, BigDecimal salary) {
    }
}
