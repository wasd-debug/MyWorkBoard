package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.task.TaskModels.DeletedResource;
import com.salarytracker.task.TaskModels.Settings;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import com.salarytracker.task.TaskModels.TaskList;
import com.salarytracker.task.TaskModels.TaskPage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TaskService {
    private static final String TASK_COLUMNS = "t.public_id, l.public_id list_public_id, t.title, t.description, " +
            "t.status, t.priority, t.start_at, t.due_at, t.all_day, t.timezone, t.duration_minutes, " +
            "t.source, t.completed_at, t.revision";
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserResolver currentUser;

    public TaskService(JdbcTemplate jdbcTemplate, CurrentUserResolver currentUser) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
    }

    @Transactional
    public List<TaskList> listLists() {
        long userId = currentUser.id();
        ensureDefaults(userId);
        return jdbcTemplate.query("SELECT public_id, name, system_key, sort_order, revision FROM task_list " +
                        "WHERE user_id = ? AND deleted = FALSE ORDER BY sort_order, id",
                (result, rowNum) -> new TaskList(result.getString("public_id"), result.getString("name"),
                        result.getString("system_key"), result.getInt("sort_order"), result.getLong("revision")),
                userId);
    }

    @Transactional
    public TaskPage listTasks(String listId, String status, int page, int size) {
        long userId = currentUser.id();
        ensureDefaults(userId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedStatus = optionalStatus(status);
        StringBuilder where = new StringBuilder(" FROM task t JOIN task_list l ON l.id = t.list_id " +
                "WHERE t.user_id = ? AND t.deleted = FALSE AND l.deleted = FALSE");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (listId != null && !listId.isBlank()) {
            where.append(" AND l.public_id = ?");
            args.add(validUuid(listId, "listId"));
        }
        if (normalizedStatus != null) {
            where.append(" AND t.status = ?");
            args.add(normalizedStatus);
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        List<TaskItem> items = jdbcTemplate.query("SELECT " + TASK_COLUMNS + where +
                        " ORDER BY t.status = 'COMPLETED', t.created_at DESC, t.id DESC LIMIT ? OFFSET ?",
                (result, rowNum) -> task(result), pageArgs.toArray());
        return new TaskPage(items, safePage, safeSize, total);
    }

    public TaskItem get(String publicId) {
        return taskForUser(currentUser.id(), publicId);
    }

    @Transactional
    public TaskItem create(TaskCommand command, String idempotencyKey) {
        long userId = currentUser.id();
        String opKey = requiredText(idempotencyKey, "Idempotency-Key", 120);
        List<String> replay = jdbcTemplate.query("SELECT public_id FROM task WHERE user_id = ? AND client_op_key = ?",
                (result, rowNum) -> result.getString(1), userId, opKey);
        if (!replay.isEmpty()) return taskForUser(userId, replay.get(0));
        ensureDefaults(userId);
        long listDatabaseId = resolveListId(userId, command == null ? null : command.listId());
        ValidatedTask values = validate(command, null);
        String publicId = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("INSERT INTO task (public_id, user_id, list_id, title, description, status, priority, " +
                            "start_at, due_at, all_day, timezone, duration_minutes, source, created_by, client_op_key) " +
                            "VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, ?, ?, ?, 'WEB', ?, ?)",
                    publicId, userId, listDatabaseId, values.title(), values.description(), values.priority(),
                    timestamp(values.startAt()), timestamp(values.dueAt()), values.allDay(), values.timezone(),
                    values.durationMinutes(), userId, opKey);
        } catch (DuplicateKeyException exception) {
            List<String> concurrent = jdbcTemplate.query(
                    "SELECT public_id FROM task WHERE user_id = ? AND client_op_key = ?",
                    (result, rowNum) -> result.getString(1), userId, opKey);
            if (!concurrent.isEmpty()) return taskForUser(userId, concurrent.get(0));
            throw exception;
        }
        return taskForUser(userId, publicId);
    }

    @Transactional
    public TaskItem update(String publicId, TaskCommand command, String ifMatch) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        long listDatabaseId = resolveListId(userId, command == null ? before.listId() : command.listId());
        ValidatedTask values = validate(command, before);
        int updated = jdbcTemplate.update("UPDATE task SET list_id = ?, title = ?, description = ?, priority = ?, " +
                        "start_at = ?, due_at = ?, all_day = ?, timezone = ?, duration_minutes = ?, revision = revision + 1 " +
                        "WHERE public_id = ? AND user_id = ? AND deleted = FALSE AND revision = ?",
                listDatabaseId, values.title(), values.description(), values.priority(), timestamp(values.startAt()),
                timestamp(values.dueAt()), values.allDay(), values.timezone(), values.durationMinutes(),
                validUuid(publicId, "publicId"), userId, expected);
        ensureUpdated(updated, userId, publicId);
        return taskForUser(userId, publicId);
    }

    @Transactional
    public TaskItem complete(String publicId, String ifMatch) {
        return changeStatus(publicId, ifMatch, "COMPLETED");
    }

    @Transactional
    public TaskItem reopen(String publicId, String ifMatch) {
        return changeStatus(publicId, ifMatch, "OPEN");
    }

    @Transactional
    public DeletedResource delete(String publicId, String ifMatch) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        int updated = jdbcTemplate.update("UPDATE task SET deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, " +
                        "revision = revision + 1 WHERE public_id = ? AND user_id = ? AND deleted = FALSE AND revision = ?",
                validUuid(publicId, "publicId"), userId, expected);
        ensureUpdated(updated, userId, publicId);
        return new DeletedResource(publicId, expected + 1, true);
    }

    @Transactional
    public Settings settings() {
        long userId = currentUser.id();
        ensureDefaults(userId);
        return jdbcTemplate.queryForObject("SELECT l.public_id, s.default_view, s.week_start, s.timezone, s.revision " +
                        "FROM task_setting s JOIN task_list l ON l.id = s.default_list_id WHERE s.user_id = ?",
                (result, rowNum) -> new Settings(result.getString("public_id"), result.getString("default_view"),
                        result.getInt("week_start"), result.getString("timezone"), result.getLong("revision")), userId);
    }

    private TaskItem changeStatus(String publicId, String ifMatch, String status) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        int updated = jdbcTemplate.update("UPDATE task SET status = ?, completed_at = " +
                        ("COMPLETED".equals(status) ? "CURRENT_TIMESTAMP" : "NULL") +
                        ", revision = revision + 1 WHERE public_id = ? AND user_id = ? AND deleted = FALSE AND revision = ?",
                status, validUuid(publicId, "publicId"), userId, expected);
        ensureUpdated(updated, userId, publicId);
        return taskForUser(userId, publicId);
    }

    private void ensureDefaults(long userId) {
        jdbcTemplate.update("INSERT IGNORE INTO task_list (public_id, user_id, name, system_key, sort_order) " +
                "VALUES (?, ?, '收件箱', 'INBOX', 0)", UUID.randomUUID().toString(), userId);
        Long inboxId = jdbcTemplate.queryForObject(
                "SELECT id FROM task_list WHERE user_id = ? AND system_key = 'INBOX'", Long.class, userId);
        String timezone = jdbcTemplate.queryForObject("SELECT timezone FROM app_user WHERE id = ?", String.class, userId);
        jdbcTemplate.update("INSERT IGNORE INTO task_setting (user_id, default_list_id, default_view, week_start, timezone) " +
                "VALUES (?, ?, 'INBOX', 1, ?)", userId, inboxId, timezone == null ? "Asia/Shanghai" : timezone);
    }

    private long resolveListId(long userId, String publicId) {
        String requested = publicId;
        if (requested == null || requested.isBlank()) {
            return jdbcTemplate.queryForObject("SELECT default_list_id FROM task_setting WHERE user_id = ?", Long.class, userId);
        }
        List<Long> rows = jdbcTemplate.query("SELECT id FROM task_list WHERE public_id = ? AND user_id = ? AND deleted = FALSE",
                (result, rowNum) -> result.getLong(1), validUuid(requested, "listId"), userId);
        if (rows.isEmpty()) throw new NotFoundException("任务清单不存在");
        return rows.get(0);
    }

    private TaskItem taskForUser(long userId, String publicId) {
        List<TaskItem> rows = jdbcTemplate.query("SELECT " + TASK_COLUMNS +
                        " FROM task t JOIN task_list l ON l.id = t.list_id " +
                        "WHERE t.public_id = ? AND t.user_id = ? AND t.deleted = FALSE AND l.deleted = FALSE",
                (result, rowNum) -> task(result), validUuid(publicId, "publicId"), userId);
        if (rows.isEmpty()) throw new NotFoundException("任务不存在");
        return rows.get(0);
    }

    private TaskItem task(ResultSet result) throws SQLException {
        return new TaskItem(result.getString("public_id"), result.getString("list_public_id"),
                result.getString("title"), result.getString("description"), result.getString("status"),
                result.getString("priority"), instant(result.getTimestamp("start_at")),
                instant(result.getTimestamp("due_at")), result.getBoolean("all_day"), result.getString("timezone"),
                (Integer) result.getObject("duration_minutes"), result.getString("source"),
                instant(result.getTimestamp("completed_at")), result.getLong("revision"));
    }

    private ValidatedTask validate(TaskCommand command, TaskItem before) {
        if (command == null) throw new IllegalArgumentException("任务内容不能为空");
        String title = command.title() == null && before != null ? before.title()
                : requiredText(command.title(), "title", 255);
        String description = command.description() == null && before != null ? before.description()
                : optionalText(command.description(), 4000);
        if (description == null) description = "";
        String priority = command.priority() == null && before != null ? before.priority()
                : enumValue(command.priority(), "NONE", List.of("NONE", "LOW", "MEDIUM", "HIGH"), "priority");
        Instant startAt = parseInstant(command.startAt() == null && before != null ? before.startAt() : command.startAt(), "startAt");
        Instant dueAt = parseInstant(command.dueAt() == null && before != null ? before.dueAt() : command.dueAt(), "dueAt");
        if (startAt != null && dueAt != null && dueAt.isBefore(startAt)) {
            throw new IllegalArgumentException("dueAt 不能早于 startAt");
        }
        boolean allDay = command.allDay() == null ? before != null && before.allDay() : command.allDay();
        String timezone = command.timezone() == null && before != null ? before.timezone()
                : optionalText(command.timezone(), 64);
        if (timezone == null || timezone.isBlank()) timezone = "Asia/Shanghai";
        try {
            ZoneId.of(timezone);
        } catch (Exception exception) {
            throw new IllegalArgumentException("timezone 无效");
        }
        Integer duration = command.durationMinutes() == null && before != null
                ? before.durationMinutes() : command.durationMinutes();
        if (duration != null && (duration < 0 || duration > 525600)) {
            throw new IllegalArgumentException("durationMinutes 超出范围");
        }
        return new ValidatedTask(title, description, priority, startAt, dueAt, allDay, timezone, duration);
    }

    private void ensureUpdated(int updated, long userId, String publicId) {
        if (updated > 0) return;
        TaskItem current = taskForUser(userId, publicId);
        throw new ConflictException("任务版本已变化", current.revision());
    }

    private long requiredRevision(String raw) {
        String value = requiredText(raw, "If-Match", 40).replace("W/", "").replace("\"", "");
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match 必须是 revision");
        }
    }

    private String validUuid(String value, String field) {
        try {
            return UUID.fromString(value).toString();
        } catch (Exception exception) {
            throw new IllegalArgumentException(field + " 必须是 UUID");
        }
    }

    private String optionalStatus(String value) {
        if (value == null || value.isBlank()) return null;
        return enumValue(value, null, List.of("OPEN", "COMPLETED"), "status");
    }

    private String enumValue(String value, String fallback, List<String> allowed, String field) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " 无效");
        return normalized;
    }

    private String requiredText(String value, String field, int max) {
        String result = optionalText(value, max);
        if (result == null || result.isBlank()) throw new IllegalArgumentException(field + " 必填");
        return result;
    }

    private String optionalText(String value, int max) {
        if (value == null) return null;
        String result = value.trim();
        if (result.length() > max) throw new IllegalArgumentException("内容长度不能超过 " + max);
        return result;
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException(field + " 必须是 UTC ISO-8601 时间");
        }
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String instant(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    private record ValidatedTask(String title, String description, String priority, Instant startAt, Instant dueAt,
                                 boolean allDay, String timezone, Integer durationMinutes) {
    }
}
