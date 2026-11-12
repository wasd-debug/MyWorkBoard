package com.salarytracker.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.tools.ToolsModels.DeletedResource;
import com.salarytracker.tools.ToolsModels.QuitSmokingCraving;
import com.salarytracker.tools.ToolsModels.QuitSmokingCravingCreate;
import com.salarytracker.tools.ToolsModels.QuitSmokingCravingUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingDay;
import com.salarytracker.tools.ToolsModels.QuitSmokingDayUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingSettings;
import com.salarytracker.tools.ToolsModels.QuitSmokingSettingsUpdate;
import com.salarytracker.tools.ToolsModels.QuitSmokingSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 戒烟日记：每用户的设置、每日打卡与渴望事件。
 * <p>
 * 时区约定：「今天」一律由服务端按账号时区计算，前端只负责展示
 * {@code summary.todayDate}，避免跨设备/跨零点时错一天。
 */
@Service
public class QuitSmokingService {
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";
    private static final int MAX_RANGE_DAYS = 366;

    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    public QuitSmokingService(JdbcTemplate jdbc, CurrentUserResolver currentUser, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
    }

    // ---------------------------------------------------------------- settings

    public QuitSmokingSettings settings() {
        long userId = currentUser.id();
        List<QuitSmokingSettings> rows = jdbc.query(
                "SELECT public_id,start_at,timezone,baseline_per_day,price_per_pack,cigarettes_per_pack," +
                        "currency,revision FROM quit_smoking_setting WHERE user_id=?",
                (row, index) -> settings(row), userId);
        if (!rows.isEmpty()) return rows.get(0);
        return new QuitSmokingSettings("", Instant.now().toString(), timezone(userId), 20,
                BigDecimal.ZERO, 20, "CNY", false, 0);
    }

    @Transactional
    public QuitSmokingSettings updateSettings(QuitSmokingSettingsUpdate body, String ifMatch) {
        long userId = currentUser.id();
        QuitSmokingSettingsUpdate command = body == null
                ? new QuitSmokingSettingsUpdate(null, null, null, null, null, null) : body;
        List<QuitSmokingSettings> rows = jdbc.query(
                "SELECT public_id,start_at,timezone,baseline_per_day,price_per_pack,cigarettes_per_pack," +
                        "currency,revision FROM quit_smoking_setting WHERE user_id=?",
                (row, index) -> settings(row), userId);
        QuitSmokingSettings current = rows.isEmpty() ? null : rows.get(0);

        Long expected = revision(ifMatch);
        if (current != null && expected != null && expected != current.revision()) {
            throw new ConflictException("戒烟设置版本已变化", current.revision());
        }

        String zone = command.timezone() == null || command.timezone().isBlank()
                ? (current == null ? timezone(userId) : current.timezone()) : zoneId(command.timezone());
        Instant startAt = command.startAt() == null || command.startAt().isBlank()
                ? (current == null ? Instant.now() : Instant.parse(current.startAt()))
                : instant(command.startAt(), ZoneId.of(zone));
        int baseline = command.baselinePerDay() == null
                ? (current == null ? 20 : current.baselinePerDay())
                : between(command.baselinePerDay(), 0, 200, "每日基线支数");
        BigDecimal price = command.pricePerPack() == null
                ? (current == null ? BigDecimal.ZERO : current.pricePerPack())
                : money(command.pricePerPack(), "每包价格");
        int perPack = command.cigarettesPerPack() == null
                ? (current == null ? 20 : current.cigarettesPerPack())
                : between(command.cigarettesPerPack(), 1, 100, "每包支数");
        String currency = command.currency() == null || command.currency().isBlank()
                ? (current == null ? "CNY" : current.currency()) : currency(command.currency());

        if (current == null) {
            jdbc.update("INSERT INTO quit_smoking_setting(public_id,user_id,start_at,timezone,baseline_per_day," +
                            "price_per_pack,cigarettes_per_pack,currency) VALUES(?,?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), userId, Timestamp.from(startAt), zone, baseline, price, perPack,
                    currency);
        } else {
            jdbc.update("UPDATE quit_smoking_setting SET start_at=?,timezone=?,baseline_per_day=?,price_per_pack=?," +
                            "cigarettes_per_pack=?,currency=?,revision=revision+1 WHERE user_id=?",
                    Timestamp.from(startAt), zone, baseline, price, perPack, currency, userId);
        }
        return settings();
    }

    // -------------------------------------------------------------------- days

    public List<QuitSmokingDay> days(String from, String to) {
        long userId = currentUser.id();
        ZoneId zone = ZoneId.of(settings().timezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate end = to == null || to.isBlank() ? today : date(to, "结束日期");
        LocalDate start = from == null || from.isBlank() ? end.minusDays(89) : date(from, "开始日期");
        assertRange(start, end);
        return jdbc.query("SELECT day_date,smoked_count,note,revision FROM quit_smoking_day " +
                        "WHERE user_id=? AND day_date BETWEEN ? AND ? ORDER BY day_date",
                (row, index) -> new QuitSmokingDay(row.getDate("day_date").toLocalDate().toString(),
                        row.getInt("smoked_count"), row.getString("note"), row.getLong("revision")),
                userId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    @Transactional
    public QuitSmokingDay putDay(String rawDate, QuitSmokingDayUpdate body, String ifMatch) {
        long userId = currentUser.id();
        ZoneId zone = ZoneId.of(settings().timezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate day = date(rawDate, "日期");
        if (day.isAfter(today.plusDays(1))) throw new IllegalArgumentException("不能记录未来的日期");
        if (day.isBefore(today.minusDays(QuitSmokingSummaryCalculator.MAX_SCAN_DAYS))) {
            throw new IllegalArgumentException("日期超出可记录范围");
        }

        QuitSmokingDayUpdate command = body == null ? new QuitSmokingDayUpdate(null, null) : body;
        List<QuitSmokingDay> rows = jdbc.query("SELECT day_date,smoked_count,note,revision FROM quit_smoking_day " +
                        "WHERE user_id=? AND day_date=?",
                (row, index) -> new QuitSmokingDay(row.getDate("day_date").toLocalDate().toString(),
                        row.getInt("smoked_count"), row.getString("note"), row.getLong("revision")),
                userId, java.sql.Date.valueOf(day));
        QuitSmokingDay current = rows.isEmpty() ? null : rows.get(0);
        Long expected = revision(ifMatch);
        if (current != null && expected != null && expected != current.revision()) {
            throw new ConflictException("当日记录版本已变化", current.revision());
        }
        int smoked = command.smokedCount() == null
                ? (current == null ? 0 : current.smokedCount())
                : between(command.smokedCount(), 0, 200, "吸烟支数");
        String note = command.note() == null ? (current == null ? "" : current.note()) : text(command.note(), 500, "备注");

        if (current == null) {
            jdbc.update("INSERT INTO quit_smoking_day(public_id,user_id,day_date,smoked_count,note) " +
                            "VALUES(?,?,?,?,?)",
                    UUID.randomUUID().toString(), userId, java.sql.Date.valueOf(day), smoked, note);
        } else {
            jdbc.update("UPDATE quit_smoking_day SET smoked_count=?,note=?,revision=revision+1 " +
                            "WHERE user_id=? AND day_date=?",
                    smoked, note, userId, java.sql.Date.valueOf(day));
        }
        return jdbc.queryForObject("SELECT day_date,smoked_count,note,revision FROM quit_smoking_day " +
                        "WHERE user_id=? AND day_date=?",
                (row, index) -> new QuitSmokingDay(row.getDate("day_date").toLocalDate().toString(),
                        row.getInt("smoked_count"), row.getString("note"), row.getLong("revision")),
                userId, java.sql.Date.valueOf(day));
    }

    // ---------------------------------------------------------------- cravings

    public List<QuitSmokingCraving> cravings(String from, String to) {
        long userId = currentUser.id();
        ZoneId zone = ZoneId.of(settings().timezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate end = to == null || to.isBlank() ? today : date(to, "结束日期");
        LocalDate start = from == null || from.isBlank() ? end.minusDays(29) : date(from, "开始日期");
        assertRange(start, end);
        return jdbc.query("SELECT public_id,occurred_at,day_date,intensity,resisted,trigger_key,note,revision " +
                        "FROM quit_smoking_craving WHERE user_id=? AND deleted_at IS NULL " +
                        "AND day_date BETWEEN ? AND ? ORDER BY occurred_at DESC",
                (row, index) -> craving(row), userId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    @Transactional
    public QuitSmokingCraving createCraving(QuitSmokingCravingCreate body, String idempotencyKey) {
        long userId = currentUser.id();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String opKey = "tools:craving:" + idempotencyKey.trim();
            List<String> previous = jdbc.query(
                    "SELECT response_json FROM idempotency_key WHERE user_id=? AND op_key=?",
                    (row, index) -> row.getString("response_json"), userId, opKey);
            if (!previous.isEmpty()) {
                try {
                    return objectMapper.readValue(previous.get(0), QuitSmokingCraving.class);
                } catch (Exception ignored) {
                    // 失效的幂等记录不应该阻塞一次合法重试。
                }
            }
        }
        QuitSmokingCravingCreate command = body == null
                ? new QuitSmokingCravingCreate(null, null, null, null, null) : body;
        QuitSmokingSettings settings = settings();
        ZoneId zone = ZoneId.of(settings.timezone());
        Instant occurredAt = command.occurredAt() == null || command.occurredAt().isBlank()
                ? Instant.now() : instant(command.occurredAt(), zone);
        Instant startAt = Instant.parse(settings.startAt());
        if (settings.configured() && occurredAt.isBefore(startAt.minusSeconds(86_400))) {
            throw new IllegalArgumentException("渴望记录不能早于戒烟起始时间");
        }
        if (occurredAt.isAfter(Instant.now().plusSeconds(300))) {
            throw new IllegalArgumentException("渴望记录不能晚于当前时间");
        }
        int intensity = command.intensity() == null ? 3 : between(command.intensity(), 1, 5, "渴望强度");
        boolean resisted = command.resisted() == null || command.resisted();
        String trigger = text(command.trigger(), 64, "触发场景");
        String note = text(command.note(), 500, "备注");
        LocalDate day = occurredAt.atZone(zone).toLocalDate();

        String publicId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO quit_smoking_craving(public_id,user_id,occurred_at,day_date,intensity,resisted," +
                        "trigger_key,note) VALUES(?,?,?,?,?,?,?,?)",
                publicId, userId, Timestamp.from(occurredAt), java.sql.Date.valueOf(day), intensity, resisted,
                trigger, note);
        QuitSmokingCraving result = cravingById(userId, publicId);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            jdbc.update("INSERT IGNORE INTO idempotency_key(user_id,op_key,response_json) VALUES(?,?,?)",
                    userId, "tools:craving:" + idempotencyKey.trim(), json(result));
        }
        return result;
    }

    @Transactional
    public QuitSmokingCraving updateCraving(String id, QuitSmokingCravingUpdate body, String ifMatch) {
        long userId = currentUser.id();
        QuitSmokingCraving current = cravingById(userId, id);
        Long expected = revision(ifMatch);
        if (expected != null && expected != current.revision()) {
            throw new ConflictException("渴望记录版本已变化", current.revision());
        }
        QuitSmokingCravingUpdate command = body == null
                ? new QuitSmokingCravingUpdate(null, null, null, null) : body;
        int intensity = command.intensity() == null
                ? current.intensity() : between(command.intensity(), 1, 5, "渴望强度");
        boolean resisted = command.resisted() == null ? current.resisted() : command.resisted();
        String trigger = command.trigger() == null ? current.trigger() : text(command.trigger(), 64, "触发场景");
        String note = command.note() == null ? current.note() : text(command.note(), 500, "备注");
        jdbc.update("UPDATE quit_smoking_craving SET intensity=?,resisted=?,trigger_key=?,note=?," +
                        "revision=revision+1 WHERE user_id=? AND public_id=? AND deleted_at IS NULL",
                intensity, resisted, trigger, note, userId, id);
        return cravingById(userId, id);
    }

    @Transactional
    public DeletedResource deleteCraving(String id, String ifMatch) {
        long userId = currentUser.id();
        QuitSmokingCraving current = cravingById(userId, id);
        Long expected = revision(ifMatch);
        if (expected != null && expected != current.revision()) {
            throw new ConflictException("渴望记录版本已变化", current.revision());
        }
        jdbc.update("UPDATE quit_smoking_craving SET deleted_at=CURRENT_TIMESTAMP,revision=revision+1 " +
                "WHERE user_id=? AND public_id=? AND deleted_at IS NULL", userId, id);
        return new DeletedResource(id);
    }

    // ----------------------------------------------------------------- summary

    public QuitSmokingSummary summary() {
        long userId = currentUser.id();
        QuitSmokingSettings settings = settings();
        ZoneId zone = ZoneId.of(settings.timezone());
        LocalDate today = LocalDate.now(zone);
        LocalDate start = Instant.parse(settings.startAt()).atZone(zone).toLocalDate();

        Map<LocalDate, Integer> smokedByDay = new LinkedHashMap<>();
        jdbc.query("SELECT day_date,smoked_count FROM quit_smoking_day WHERE user_id=? AND day_date BETWEEN ? AND ?",
                (ResultSet row) -> {
                    smokedByDay.put(row.getDate("day_date").toLocalDate(), row.getInt("smoked_count"));
                }, userId, java.sql.Date.valueOf(start.isBefore(today) ? start : today),
                java.sql.Date.valueOf(today));

        QuitSmokingSummaryCalculator.Totals totals =
                QuitSmokingSummaryCalculator.totals(start, today, smokedByDay);
        BigDecimal saved = QuitSmokingSummaryCalculator.savedAmount(totals, settings.baselinePerDay(),
                settings.pricePerPack(), settings.cigarettesPerPack());

        List<QuitSmokingCraving> todayItems = jdbc.query(
                "SELECT public_id,occurred_at,day_date,intensity,resisted,trigger_key,note,revision " +
                        "FROM quit_smoking_craving WHERE user_id=? AND day_date=? AND deleted_at IS NULL " +
                        "ORDER BY occurred_at DESC",
                (row, index) -> craving(row), userId, java.sql.Date.valueOf(today));
        int resistedCount = (int) todayItems.stream().filter(QuitSmokingCraving::resisted).count();
        QuitSmokingDay todayRecord = jdbc.query(
                        "SELECT day_date,smoked_count,note,revision FROM quit_smoking_day WHERE user_id=? AND day_date=?",
                        (row, index) -> new QuitSmokingDay(row.getDate("day_date").toLocalDate().toString(),
                                row.getInt("smoked_count"), row.getString("note"), row.getLong("revision")),
                        userId, java.sql.Date.valueOf(today))
                .stream().findFirst().orElse(null);

        return new QuitSmokingSummary(settings.startAt(), totals.elapsedDays(), totals.cleanDays(),
                totals.currentStreak(), totals.longestStreak(), totals.totalSmoked(), saved, settings.currency(),
                today.toString(), todayRecord == null ? 0 : todayRecord.smokedCount(), todayItems.size(),
                resistedCount, todayRecord != null, settings.revision(),
                todayRecord == null ? 0 : todayRecord.revision(), todayItems);
    }

    // ------------------------------------------------------------------ helpers

    private QuitSmokingSettings settings(ResultSet row) throws SQLException {
        return new QuitSmokingSettings(row.getString("public_id"),
                row.getTimestamp("start_at").toInstant().toString(), row.getString("timezone"),
                row.getInt("baseline_per_day"), row.getBigDecimal("price_per_pack"),
                row.getInt("cigarettes_per_pack"), row.getString("currency"), true, row.getLong("revision"));
    }

    private QuitSmokingCraving craving(ResultSet row) throws SQLException {
        return new QuitSmokingCraving(row.getString("public_id"),
                row.getTimestamp("occurred_at").toInstant().toString(),
                row.getDate("day_date").toLocalDate().toString(), row.getInt("intensity"),
                row.getBoolean("resisted"), row.getString("trigger_key"), row.getString("note"),
                row.getLong("revision"));
    }

    private QuitSmokingCraving cravingById(long userId, String id) {
        List<QuitSmokingCraving> rows = jdbc.query(
                "SELECT public_id,occurred_at,day_date,intensity,resisted,trigger_key,note,revision " +
                        "FROM quit_smoking_craving WHERE user_id=? AND public_id=? AND deleted_at IS NULL",
                (row, index) -> craving(row), userId, id);
        if (rows.isEmpty()) throw new NotFoundException("渴望记录不存在");
        return rows.get(0);
    }

    private String timezone(long userId) {
        try {
            String zone = jdbc.queryForObject("SELECT timezone FROM app_user WHERE id=?", String.class, userId);
            return zone == null || zone.isBlank() ? DEFAULT_TIMEZONE : zoneId(zone);
        } catch (RuntimeException exception) {
            return DEFAULT_TIMEZONE;
        }
    }

    private String zoneId(String raw) {
        String text = text(raw, 64, "时区");
        try {
            return ZoneId.of(text).getId();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("时区无效");
        }
    }

    private Instant instant(String raw, ZoneId zone) {
        String value = text(raw, 64, "时间");
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        try {
            return LocalDateTime.parse(value).atZone(zone).toInstant();
        } catch (DateTimeParseException ignored) {
            // 继续尝试其它格式
        }
        try {
            return LocalDate.parse(value).atStartOfDay(zone).toInstant();
        } catch (DateTimeParseException ignored) {
            throw new IllegalArgumentException("时间格式无效");
        }
    }

    private LocalDate date(String raw, String label) {
        try {
            return LocalDate.parse(text(raw, 20, label));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(label + "格式无效");
        }
    }

    private void assertRange(LocalDate start, LocalDate end) {
        if (start.isAfter(end)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("查询区间不能超过 " + MAX_RANGE_DAYS + " 天");
        }
    }

    private int between(int value, int min, int max, String label) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + "需要在 " + min + " 到 " + max + " 之间");
        }
        return value;
    }

    private BigDecimal money(BigDecimal value, String label) {
        if (value == null) return BigDecimal.ZERO;
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(10_000)) > 0) {
            throw new IllegalArgumentException(label + "需要在 0 到 10000 之间");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String currency(String raw) {
        String value = text(raw, 8, "币种").toUpperCase();
        if (!value.matches("[A-Z]{3}")) throw new IllegalArgumentException("币种需要是三位字母代码");
        return value;
    }

    private String text(String raw, int max, String label) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > max) throw new IllegalArgumentException(label + "不能超过 " + max + " 个字符");
        return value;
    }

    private Long revision(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) return null;
        try {
            return Long.parseLong(ifMatch.replace("W/", "").replace("\"", "").trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match 必须是 revision");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法序列化响应", exception);
        }
    }
}
