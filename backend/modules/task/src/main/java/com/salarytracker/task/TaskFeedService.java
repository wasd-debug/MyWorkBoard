package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.task.TaskModels.FeedToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TaskFeedService {
    private static final DateTimeFormatter ICS_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Window> limits = new ConcurrentHashMap<>();

    public TaskFeedService(JdbcTemplate jdbc, CurrentUserResolver currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    public FeedToken status() {
        List<FeedToken> rows = jdbc.query("SELECT token_hint,created_at,last_accessed_at FROM task_feed_token " +
                        "WHERE user_id=? AND revoked_at IS NULL ORDER BY id DESC LIMIT 1",
                (result, row) -> new FeedToken(true, result.getString(1), null,
                        instant(result.getTimestamp(2)), instant(result.getTimestamp(3))), currentUser.id());
        return rows.isEmpty() ? new FeedToken(false, null, null, null, null) : rows.get(0);
    }

    @Transactional
    public FeedToken create(String baseUrl) {
        if (status().active()) throw new IllegalStateException("已有有效订阅地址，请使用轮换");
        return issue(baseUrl);
    }

    @Transactional
    public FeedToken rotate(String baseUrl) {
        revoke();
        return issue(baseUrl);
    }

    @Transactional
    public FeedToken revoke() {
        jdbc.update("UPDATE task_feed_token SET revoked_at=CURRENT_TIMESTAMP(3) WHERE user_id=? AND revoked_at IS NULL",
                currentUser.id());
        return new FeedToken(false, null, null, null, null);
    }

    @Transactional
    public String feed(String rawToken, String remoteAddress) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw new NotFoundException("订阅不存在");
        String tokenHash = hash(rawToken);
        limit(remoteAddress, tokenHash);
        List<Owner> owners = jdbc.query("SELECT f.id,f.user_id,s.feed_include_description FROM task_feed_token f " +
                        "JOIN task_setting s ON s.user_id=f.user_id WHERE f.token_hash=? AND f.revoked_at IS NULL",
                (result, row) -> new Owner(result.getLong(1), result.getLong(2), result.getBoolean(3)), tokenHash);
        if (owners.isEmpty()) throw new NotFoundException("订阅不存在");
        Owner owner = owners.get(0);
        jdbc.update("UPDATE task_feed_token SET last_accessed_at=CURRENT_TIMESTAMP(3) WHERE id=?", owner.id());
        List<FeedTask> tasks = jdbc.query("SELECT id,public_id,title,description,due_at FROM task " +
                        "WHERE user_id=? AND deleted=FALSE AND status='OPEN' AND due_at IS NOT NULL ORDER BY due_at,id",
                (result, row) -> new FeedTask(result.getLong(1), result.getString(2), result.getString(3),
                        result.getString(4), result.getTimestamp(5).toInstant()), owner.userId());
        StringBuilder out = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//MyWorkBoard//Tasks//CN\r\nCALSCALE:GREGORIAN\r\nMETHOD:PUBLISH\r\n");
        for (FeedTask task : tasks) {
            out.append("BEGIN:VEVENT\r\nUID:").append(task.publicId()).append("@myworkboard\r\n")
                    .append("DTSTAMP:").append(ICS_TIME.format(Instant.now())).append("\r\n")
                    .append("DTSTART:").append(ICS_TIME.format(task.dueAt())).append("\r\n")
                    .append("DUE:").append(ICS_TIME.format(task.dueAt())).append("\r\n")
                    .append("SUMMARY:").append(escape(task.title())).append("\r\n");
            if (owner.includeDescription() && task.description() != null && !task.description().isBlank()) {
                out.append("DESCRIPTION:").append(escape(task.description())).append("\r\n");
            }
            List<Integer> reminders = jdbc.query("SELECT offset_minutes FROM task_reminder WHERE task_id=? AND kind='RELATIVE' " +
                            "AND cancelled_at IS NULL AND offset_minutes IS NOT NULL ORDER BY offset_minutes",
                    (result, row) -> result.getInt(1), task.id());
            for (Integer minutes : reminders) out.append("BEGIN:VALARM\r\nACTION:DISPLAY\r\nDESCRIPTION:")
                    .append(escape(task.title())).append("\r\nTRIGGER:-PT").append(minutes).append("M\r\nEND:VALARM\r\n");
            out.append("END:VEVENT\r\n");
        }
        return out.append("END:VCALENDAR\r\n").toString();
    }

    private FeedToken issue(String baseUrl) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hint = raw.substring(0, 4) + "…" + raw.substring(raw.length() - 4);
        jdbc.update("INSERT INTO task_feed_token(user_id,token_hash,token_hint) VALUES(?,?,?)",
                currentUser.id(), hash(raw), hint);
        return new FeedToken(true, hint, baseUrl + "/api/v1/tasks/feed/" + raw + ".ics",
                Instant.now().toString(), null);
    }

    private void limit(String address, String hash) {
        long hour = Instant.now().getEpochSecond() / 3600;
        String key = (address == null ? "unknown" : address) + '|' + hash;
        Window window = limits.compute(key, (ignored, existing) -> existing == null || existing.hour() != hour
                ? new Window(hour, 1) : new Window(hour, existing.count() + 1));
        if (window.count() > 60) throw new IllegalStateException("订阅访问过于频繁，请稍后重试");
        if (limits.size() > 10000) limits.entrySet().removeIf(entry -> entry.getValue().hour() < hour - 1);
    }

    private String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("无法生成订阅摘要", exception); }
    }

    private String escape(String value) { return value.replace("\\", "\\\\").replace("\r", "")
            .replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;"); }
    private String instant(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
    private record Owner(long id, long userId, boolean includeDescription) { }
    private record FeedTask(long id, String publicId, String title, String description, Instant dueAt) { }
    private record Window(long hour, int count) { }
}
