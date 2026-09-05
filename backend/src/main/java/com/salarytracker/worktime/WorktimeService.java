package com.salarytracker.worktime;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Time;
import java.time.Duration;
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
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentUserResolver currentUser;

    public WorktimeService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, CurrentUserResolver currentUser) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.currentUser = currentUser;
    }

    public Map<String, Object> readSettings() {
        long userId = currentUser.id();
        Map<String, Object> result = defaultSettings();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT salary_pre, salary_post, basis, TIME_FORMAT(work_start, '%H:%i') work_start, TIME_FORMAT(work_end, '%H:%i') work_end, lunch_min, days_per_month, auto_days, revision FROM work_setting WHERE user_id = ?", userId);
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            result.put("salaryPre", number(row.get("salary_pre")));
            result.put("salaryPost", number(row.get("salary_post")));
            result.put("basis", row.get("basis"));
            result.put("workStart", row.get("work_start"));
            result.put("workEnd", row.get("work_end"));
            result.put("lunchMin", ((Number) row.get("lunch_min")).intValue());
            result.put("daysPerMonth", number(row.get("days_per_month")));
            Object autoDays = row.get("auto_days");
            result.put("autoDays", autoDays instanceof Boolean flag ? flag : autoDays instanceof Number number && number.intValue() == 1);
            result.put("revision", ((Number) row.get("revision")).longValue());
        }
        Map<String, Object> salaries = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("SELECT month_key, salary_pre, salary_post FROM salary_monthly WHERE user_id = ? ORDER BY month_key", userId)) {
            salaries.put(String.valueOf(row.get("month_key")), Map.of("pre", number(row.get("salary_pre")), "post", number(row.get("salary_post"))));
        }
        result.put("salaries", salaries);
        return result;
    }

    @Transactional
    public Map<String, Object> writeSettings(Map<String, Object> input, String ifMatch) {
        long userId = currentUser.id();
        long currentRevision = settingRevision(userId);
        checkRevision(ifMatch, currentRevision);
        Map<String, Object> values = input == null ? Map.of() : input;
        String basis = "pre".equals(String.valueOf(values.getOrDefault("basis", "post"))) ? "pre" : "post";
        String workStart = timeText(values.getOrDefault("workStart", "09:00"));
        String workEnd = timeText(values.getOrDefault("workEnd", "18:00"));
        int lunch = intValue(values.getOrDefault("lunchMin", 90), 0, 600);
        BigDecimal days = decimal(values.getOrDefault("daysPerMonth", "21.75"), new BigDecimal("21.75"));
        BigDecimal pre = decimal(values.getOrDefault("salaryPre", 0), BigDecimal.ZERO);
        BigDecimal post = decimal(values.getOrDefault("salaryPost", 0), BigDecimal.ZERO);
        boolean autoDays = booleanValue(values.getOrDefault("autoDays", true));
        jdbcTemplate.update("INSERT INTO work_setting (user_id, salary_pre, salary_post, basis, work_start, work_end, lunch_min, days_per_month, auto_days, revision) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE salary_pre = VALUES(salary_pre), salary_post = VALUES(salary_post), basis = VALUES(basis), work_start = VALUES(work_start), work_end = VALUES(work_end), lunch_min = VALUES(lunch_min), days_per_month = VALUES(days_per_month), auto_days = VALUES(auto_days), revision = revision + 1",
                userId, pre, post, basis, Time.valueOf(normalizeTime(workStart)), Time.valueOf(normalizeTime(workEnd)), lunch, days, autoDays);
        Object rawSalaries = values.get("salaries");
        if (rawSalaries instanceof Map<?, ?> salaries) {
            for (Map.Entry<?, ?> entry : salaries.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> monthly)) continue;
                String month = String.valueOf(entry.getKey());
                if (!month.matches("\\d{4}-\\d{2}")) continue;
                BigDecimal monthlyPre = decimal(monthly.get("pre"), BigDecimal.ZERO);
                BigDecimal monthlyPost = decimal(monthly.get("post"), BigDecimal.ZERO);
                jdbcTemplate.update("INSERT INTO salary_monthly (user_id, month_key, salary_pre, salary_post) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE salary_pre = VALUES(salary_pre), salary_post = VALUES(salary_post), revision = revision + 1", userId, month, monthlyPre, monthlyPost);
            }
        }
        return readSettings();
    }

    public List<Map<String, Object>> listRecords(String from, String to, int limit) {
        long userId = currentUser.id();
        StringBuilder sql = new StringBuilder("SELECT id, date, TIME_FORMAT(start_time, '%H:%i') start_time, IFNULL(TIME_FORMAT(end_time, '%H:%i'), '') end_time, rest_min, overtime_min, real_hourly_wage, note, calc_version, timezone, revision FROM work_record WHERE user_id = ? AND deleted = FALSE");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (from != null && !from.isBlank()) { LocalDate.parse(from, DATE); sql.append(" AND date >= ?"); args.add(from); }
        if (to != null && !to.isBlank()) { LocalDate.parse(to, DATE); sql.append(" AND date <= ?"); args.add(to); }
        sql.append(" ORDER BY date DESC LIMIT ?"); args.add(Math.min(Math.max(limit, 1), 200));
        return jdbcTemplate.queryForList(sql.toString(), args.toArray()).stream().map(this::recordView).toList();
    }

    @Transactional
    public Map<String, Object> createRecord(Map<String, Object> body) {
        return createRecord(body, null);
    }

    @Transactional
    public Map<String, Object> createRecord(Map<String, Object> body, String idempotencyKey) {
        long userId = currentUser.id();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            List<Map<String, Object>> previous = jdbcTemplate.queryForList("SELECT response_json FROM idempotency_key WHERE user_id = ? AND op_key = ?", userId, idempotencyKey.trim());
            if (!previous.isEmpty()) {
                try { return objectMapper.readValue(String.valueOf(previous.get(0).get("response_json")), new TypeReference<Map<String, Object>>() {}); }
                catch (Exception ignored) { }
            }
        }
        String date = requiredDate(body);
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id, revision, deleted FROM work_record WHERE user_id = ? AND date = ?", userId, date);
        if (!existing.isEmpty() && !isTrue(existing.get(0).get("deleted"))) {
            throw new ConflictException("该日期已有记录", ((Number) existing.get(0).get("revision")).longValue());
        }
        Map<String, Object> result = !existing.isEmpty()
                ? saveRecord(userId, ((Number) existing.get(0).get("id")).longValue(), body, true)
                : saveRecord(userId, null, body, false);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            jdbcTemplate.update("INSERT IGNORE INTO idempotency_key (user_id, op_key, response_json) VALUES (?, ?, ?)", userId, idempotencyKey.trim(), json(result));
        }
        return result;
    }

    @Transactional
    public Map<String, Object> updateRecord(long id, Map<String, Object> body, String ifMatch) {
        long userId = currentUser.id();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT date, TIME_FORMAT(start_time, '%H:%i') start_time, IFNULL(TIME_FORMAT(end_time, '%H:%i'), '') end_time, rest_min, note, revision FROM work_record WHERE id = ? AND user_id = ? AND deleted = FALSE", id, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("记录不存在");
        long revision = ((Number) rows.get(0).get("revision")).longValue();
        checkRevision(ifMatch, revision);
        Map<String, Object> merged = new LinkedHashMap<>();
        Map<String, Object> current = rows.get(0);
        merged.put("date", current.get("date"));
        merged.put("start", current.get("start_time"));
        merged.put("end", current.get("end_time"));
        merged.put("rest", current.get("rest_min"));
        merged.put("note", current.get("note"));
        if (body != null) merged.putAll(body);
        return saveRecord(userId, id, merged, true);
    }

    @Transactional
    public Map<String, Object> deleteRecord(long id, String ifMatch) {
        long userId = currentUser.id();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT revision FROM work_record WHERE id = ? AND user_id = ? AND deleted = FALSE", id, userId);
        if (rows.isEmpty()) throw new IllegalArgumentException("记录不存在");
        long revision = ((Number) rows.get(0).get("revision")).longValue();
        checkRevision(ifMatch, revision);
        jdbcTemplate.update("UPDATE work_record SET deleted = TRUE, revision = revision + 1 WHERE id = ? AND user_id = ?", id, userId);
        return Map.of("id", id, "revision", revision + 1, "deleted", true);
    }

    public Map<String, Object> readSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("settings", readSettings());
        Map<String, Object> records = new LinkedHashMap<>();
        for (Map<String, Object> record : listAllRecords()) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("start", record.get("start"));
            value.put("end", record.get("end"));
            value.put("rest", record.get("rest"));
            value.put("revision", record.get("revision"));
            records.put(String.valueOf(record.get("date")), value);
        }
        snapshot.put("records", records);
        return snapshot;
    }

    private List<Map<String, Object>> listAllRecords() {
        long userId = currentUser.id();
        return jdbcTemplate.queryForList("SELECT id, date, TIME_FORMAT(start_time, '%H:%i') start_time, IFNULL(TIME_FORMAT(end_time, '%H:%i'), '') end_time, rest_min, overtime_min, real_hourly_wage, note, calc_version, timezone, revision FROM work_record WHERE user_id = ? AND deleted = FALSE ORDER BY date DESC", userId)
                .stream().map(this::recordView).toList();
    }

    @Transactional
    public Map<String, Object> replaceSnapshot(Map<String, Object> payload) {
        Map<String, Object> snapshot = payload == null ? Map.of() : payload;
        writeSettings(snapshot.get("settings") instanceof Map<?, ?> m ? cast(m) : Map.of(), null);
        long userId = currentUser.id();
        jdbcTemplate.update("UPDATE work_record SET deleted = TRUE, revision = revision + 1 WHERE user_id = ? AND deleted = FALSE", userId);
        Object rawRecords = snapshot.get("records");
        if (rawRecords instanceof Map<?, ?> records) {
            for (Map.Entry<?, ?> entry : records.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> value)) continue;
                Map<String, Object> body = cast(value);
                body.put("date", String.valueOf(entry.getKey()));
                List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id FROM work_record WHERE user_id = ? AND date = ?", userId, entry.getKey());
                if (existing.isEmpty()) saveRecord(userId, null, body, false);
                else saveRecord(userId, ((Number) existing.get(0).get("id")).longValue(), body, true);
            }
        }
        return readSnapshot();
    }

    private Map<String, Object> saveRecord(long userId, Long id, Map<String, Object> body, boolean update) {
        String date = requiredDate(body);
        String start = timeText(body.get("start"));
        String end = timeText(body.get("end"));
        int rest = intValue(body.getOrDefault("rest", 0), 0, 600);
        String note = String.valueOf(body.getOrDefault("note", ""));
        Calculation calculation = calculate(date, start, end, rest);
        String snapshot = json(Map.of("basis", readSettings().get("basis"), "salary", calculation.salary));
        if (id == null) {
            jdbcTemplate.update("INSERT INTO work_record (user_id, date, start_time, end_time, rest_min, overtime_min, real_hourly_wage, note, salary_snapshot) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    userId, date, Time.valueOf(normalizeTime(start)), end.isBlank() ? null : Time.valueOf(normalizeTime(end)), rest, calculation.overtimeMin, calculation.realHourlyWage, note, snapshot);
            id = jdbcTemplate.queryForObject("SELECT id FROM work_record WHERE user_id = ? AND date = ?", Long.class, userId, date);
        } else {
            jdbcTemplate.update("UPDATE work_record SET date = ?, start_time = ?, end_time = ?, rest_min = ?, overtime_min = ?, real_hourly_wage = ?, note = ?, salary_snapshot = ?, deleted = FALSE, revision = revision + 1 WHERE id = ? AND user_id = ?",
                    date, Time.valueOf(normalizeTime(start)), end.isBlank() ? null : Time.valueOf(normalizeTime(end)), rest, calculation.overtimeMin, calculation.realHourlyWage, note, snapshot, id, userId);
        }
        return recordView(jdbcTemplate.queryForMap("SELECT id, date, TIME_FORMAT(start_time, '%H:%i') start_time, IFNULL(TIME_FORMAT(end_time, '%H:%i'), '') end_time, rest_min, overtime_min, real_hourly_wage, note, calc_version, timezone, revision FROM work_record WHERE id = ? AND user_id = ?", id, userId));
    }

    private Calculation calculate(String date, String startText, String endText, int rest) {
        Map<String, Object> settings = readSettings();
        LocalTime start = LocalTime.parse(normalizeTime(startText));
        int actual = 0;
        if (!endText.isBlank()) {
            LocalTime end = LocalTime.parse(normalizeTime(endText));
            long minutes = Duration.between(start, end).toMinutes();
            if (minutes < 0) minutes += 24 * 60;
            actual = Math.max(0, (int) minutes - rest);
        }
        LocalTime workStart = LocalTime.parse(normalizeTime(String.valueOf(settings.get("workStart"))));
        LocalTime workEnd = LocalTime.parse(normalizeTime(String.valueOf(settings.get("workEnd"))));
        int standard = (int) Duration.between(workStart, workEnd).toMinutes() - intValue(settings.get("lunchMin"), 90, 0, 600);
        int overtime = actual - Math.max(0, standard);
        String month = date.substring(0, 7);
        BigDecimal salary = monthlySalary(settings, month);
        BigDecimal days = decimal(settings.get("daysPerMonth"), new BigDecimal("21.75"));
        BigDecimal wage = actual > 0 && salary.signum() > 0 && days.signum() > 0
                ? salary.divide(days, 8, RoundingMode.HALF_UP).divide(BigDecimal.valueOf(actual).divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new Calculation(overtime, wage, salary);
    }

    private BigDecimal monthlySalary(Map<String, Object> settings, String month) {
        Object salaries = settings.get("salaries");
        if (salaries instanceof Map<?, ?> map && map.get(month) instanceof Map<?, ?> monthly) {
            String basis = String.valueOf(settings.getOrDefault("basis", "post"));
            return decimal(monthly.get("pre".equals(basis) ? "pre" : "post"), BigDecimal.ZERO);
        }
        return decimal(settings.get("pre".equals(String.valueOf(settings.getOrDefault("basis", "post"))) ? "salaryPre" : "salaryPost"), BigDecimal.ZERO);
    }

    private Map<String, Object> recordView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", ((Number) row.get("id")).longValue());
        view.put("date", String.valueOf(row.get("date")));
        view.put("start", row.get("start_time"));
        view.put("end", row.get("end_time"));
        view.put("rest", ((Number) row.get("rest_min")).intValue());
        view.put("overtimeMin", ((Number) row.get("overtime_min")).intValue());
        view.put("realHourlyWage", row.get("real_hourly_wage"));
        view.put("note", row.get("note"));
        view.put("calcVersion", row.get("calc_version"));
        view.put("timezone", row.get("timezone"));
        view.put("revision", ((Number) row.get("revision")).longValue());
        return view;
    }

    private long settingRevision(long userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT revision FROM work_setting WHERE user_id = ?", userId);
        return rows.isEmpty() ? 0 : ((Number) rows.get(0).get("revision")).longValue();
    }

    private void checkRevision(String ifMatch, long revision) {
        if (ifMatch == null || ifMatch.isBlank()) return;
        String value = ifMatch.trim().replace("W/", "").replace("\"", "");
        try {
            long expected = Long.parseLong(value);
            if (expected != revision) throw new ConflictException("资源版本已变化", revision);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match 必须是 revision");
        }
    }

    private Map<String, Object> defaultSettings() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workStart", "09:00"); result.put("workEnd", "18:00"); result.put("lunchMin", 90);
        result.put("daysPerMonth", 21.75); result.put("autoDays", true); result.put("salaryPre", 0); result.put("salaryPost", 0);
        result.put("basis", "post"); result.put("salaries", new LinkedHashMap<>()); result.put("revision", 0L);
        return result;
    }

    private String requiredDate(Map<String, Object> body) {
        if (body == null || body.get("date") == null) throw new IllegalArgumentException("date 必填");
        String date = String.valueOf(body.get("date"));
        LocalDate.parse(date, DATE);
        return date;
    }

    private String timeText(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw).trim();
        if (value.isBlank()) return "";
        LocalTime.parse(normalizeTime(value));
        return value;
    }

    private String normalizeTime(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("start 必须是 HH:mm");
        return value.length() == 5 ? value + ":00" : value;
    }

    private int intValue(Object value, int min, int max) {
        return intValue(value, min, min, max);
    }

    private int intValue(Object value, int fallback, int min, int max) {
        try { return Math.min(max, Math.max(min, Integer.parseInt(String.valueOf(value)))); }
        catch (Exception ignored) { return fallback; }
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private boolean isTrue(Object value) {
        return value instanceof Boolean b ? b : value instanceof Number number && number.intValue() != 0;
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        try { return new BigDecimal(String.valueOf(value)).max(BigDecimal.ZERO); }
        catch (Exception ignored) { return fallback; }
    }

    private Object number(Object value) {
        return value instanceof BigDecimal decimal ? decimal.stripTrailingZeros() : value;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cast(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    private record Calculation(int overtimeMin, BigDecimal realHourlyWage, BigDecimal salary) {
    }
}
