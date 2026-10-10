package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.NotFoundException;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TaskReminderService {
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;
    private final TaskInboxStream stream;

    public TaskReminderService(JdbcTemplate jdbc, CurrentUserResolver currentUser, TaskInboxStream stream) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.stream = stream;
    }

    public List<TaskModels.Reminder> list(String taskPublicId) {
        return jdbc.query("SELECT r.public_id,t.public_id task_public_id,r.kind,r.offset_minutes,r.remind_at,r.channel," +
                        "r.sent_at,r.daily_until_done,r.revision FROM task_reminder r JOIN task t ON t.id=r.task_id " +
                        "WHERE t.public_id=? AND r.user_id=? AND r.cancelled_at IS NULL ORDER BY r.remind_at,r.id",
                (row, number) -> reminder(row), uuid(taskPublicId), currentUser.id());
    }

    @Transactional
    public TaskModels.Reminder create(String taskPublicId, TaskModels.ReminderCommand command) {
        long userId = currentUser.id();
        TaskRef task = task(userId, taskPublicId);
        Values values = values(command, task, null);
        String publicId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO task_reminder(public_id,user_id,task_id,kind,offset_minutes,remind_at,channel,daily_until_done) " +
                        "VALUES(?,?,?,?,?,?,?,?)", publicId, userId, task.id(), values.kind(), values.offsetMinutes(),
                Timestamp.from(values.remindAt()), values.channel(), values.daily());
        return get(userId, publicId);
    }

    @Transactional
    public TaskModels.Reminder update(String taskPublicId, String publicId, TaskModels.ReminderCommand command, long revision) {
        long userId = currentUser.id();
        TaskRef task = task(userId, taskPublicId);
        TaskModels.Reminder before = get(userId, publicId);
        if (!before.taskId().equals(taskPublicId)) throw new NotFoundException("任务提醒不存在");
        Values values = values(command, task, before);
        int changed = jdbc.update("UPDATE task_reminder SET kind=?,offset_minutes=?,remind_at=?,channel=?,daily_until_done=?," +
                        "sent_at=NULL,cancelled_at=NULL,revision=revision+1 WHERE public_id=? AND user_id=? AND revision=?",
                values.kind(), values.offsetMinutes(), Timestamp.from(values.remindAt()), values.channel(), values.daily(),
                uuid(publicId), userId, revision);
        if (changed == 0) throw new IllegalArgumentException("提醒版本已变化");
        return get(userId, publicId);
    }

    @Transactional
    public void delete(String taskPublicId, String publicId, long revision) {
        int changed = jdbc.update("UPDATE task_reminder r JOIN task t ON t.id=r.task_id SET r.cancelled_at=CURRENT_TIMESTAMP(3)," +
                        "r.revision=r.revision+1 WHERE r.public_id=? AND t.public_id=? AND r.user_id=? AND r.revision=?",
                uuid(publicId), uuid(taskPublicId), currentUser.id(), revision);
        if (changed == 0) throw new NotFoundException("任务提醒不存在或版本已变化");
    }

    public TaskModels.InboxPage inbox(boolean unreadOnly, int page, int size) {
        int safePage = Math.max(0, page), safeSize = Math.min(100, Math.max(1, size));
        String where = " FROM task_inbox_message m LEFT JOIN task t ON t.id=m.task_id WHERE m.user_id=? AND " +
                "(m.expires_at IS NULL OR m.expires_at>CURRENT_TIMESTAMP(3))" + (unreadOnly ? " AND m.read_at IS NULL" : "");
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, currentUser.id());
        List<TaskModels.InboxMessage> items = jdbc.query("SELECT m.public_id,m.category,m.type,m.title,m.body,m.level," +
                        "t.public_id task_public_id,m.deep_link,m.read_at,m.created_at" + where +
                        " ORDER BY m.created_at DESC,m.id DESC LIMIT ? OFFSET ?", (row, number) -> message(row),
                currentUser.id(), safeSize, safePage * safeSize);
        return new TaskModels.InboxPage(items, safePage, safeSize, total);
    }

    public TaskModels.InboxUnread unread() {
        long unread = jdbc.queryForObject("SELECT COUNT(*) FROM task_inbox_message WHERE user_id=? AND read_at IS NULL " +
                "AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP(3))", Long.class, currentUser.id());
        long overdue = jdbc.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=? AND deleted=FALSE AND status='OPEN' " +
                "AND due_at<CURRENT_TIMESTAMP(3)", Long.class, currentUser.id());
        return new TaskModels.InboxUnread(unread, overdue);
    }

    public SseEmitter stream() { return stream.connect(currentUser.id()); }

    @Transactional
    public TaskModels.InboxUnread read(TaskModels.InboxReadCommand command) {
        long userId = currentUser.id();
        if (command != null && Boolean.TRUE.equals(command.all())) {
            jdbc.update("UPDATE task_inbox_message SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(3)) WHERE user_id=?", userId);
        } else if (command != null && command.ids() != null) {
            for (String id : command.ids()) jdbc.update("UPDATE task_inbox_message SET read_at=COALESCE(read_at,CURRENT_TIMESTAMP(3)) " +
                    "WHERE public_id=? AND user_id=?", uuid(id), userId);
        }
        return unread();
    }

    @Transactional
    public long clearRead() {
        return jdbc.update("DELETE FROM task_inbox_message WHERE user_id=? AND read_at IS NOT NULL", currentUser.id());
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 5000)
    @SchedulerLock(name = "taskReminderScan", lockAtMostFor = "PT25S", lockAtLeastFor = "PT1S")
    public void scan() {
        List<Long> ids = jdbc.query("SELECT r.id FROM task_reminder r JOIN task t ON t.id=r.task_id " +
                "WHERE r.cancelled_at IS NULL AND r.remind_at<=TIMESTAMPADD(SECOND,30,CURRENT_TIMESTAMP(3)) " +
                "AND (r.sent_at IS NULL OR r.daily_until_done=TRUE) AND t.deleted=FALSE AND t.status='OPEN'",
                (row, number) -> row.getLong(1));
        for (Long id : ids) fire(id);
        stream.heartbeat();
    }

    @Transactional
    public void fire(long reminderId) {
        List<FireRef> rows = jdbc.query("SELECT r.id,r.user_id,r.channel,r.remind_at,r.daily_until_done,t.id task_id," +
                        "t.public_id task_public_id,t.title,t.timezone FROM task_reminder r JOIN task t ON t.id=r.task_id " +
                        "WHERE r.id=? AND r.cancelled_at IS NULL AND t.deleted=FALSE AND t.status='OPEN' FOR UPDATE",
                (row, number) -> new FireRef(row.getLong("id"), row.getLong("user_id"), row.getString("channel"),
                        row.getTimestamp("remind_at").toInstant(), row.getBoolean("daily_until_done"), row.getLong("task_id"),
                        row.getString("task_public_id"), row.getString("title"), row.getString("timezone")), reminderId);
        if (rows.isEmpty()) return;
        FireRef ref = rows.get(0);
        LocalDate fireDate = ref.remindAt().atZone(ZoneId.of(ref.timezone())).toLocalDate();
        int inserted = jdbc.update("INSERT IGNORE INTO task_reminder_fire(reminder_id,user_id,fire_date,channel,result) VALUES(?,?,?,?,?)",
                ref.id(), ref.userId(), fireDate, ref.channel(), "SENT");
        if (inserted == 0) return;
        String messageId = UUID.randomUUID().toString();
        String dedupe = "task-reminder:" + ref.id() + ":" + fireDate;
        jdbc.update("INSERT IGNORE INTO task_inbox_message(public_id,user_id,category,type,title,body,level,task_id,deep_link,dedupe_key,expires_at) " +
                        "VALUES(?,?,'REMINDER','TASK_DUE',?,?,'INFO',?,?,?,TIMESTAMPADD(DAY,90,CURRENT_TIMESTAMP(3)))",
                messageId, ref.userId(), ref.title(), "任务提醒", ref.taskId(), "/tasks/all?task=" + ref.taskPublicId(), dedupe);
        jdbc.update(ref.daily() ? "UPDATE task_reminder SET sent_at=CURRENT_TIMESTAMP(3),remind_at=TIMESTAMPADD(DAY,1,remind_at),revision=revision+1 WHERE id=?"
                : "UPDATE task_reminder SET sent_at=CURRENT_TIMESTAMP(3),revision=revision+1 WHERE id=?", ref.id());
        List<TaskModels.InboxMessage> message = jdbc.query("SELECT m.public_id,m.category,m.type,m.title,m.body,m.level,t.public_id task_public_id," +
                "m.deep_link,m.read_at,m.created_at FROM task_inbox_message m LEFT JOIN task t ON t.id=m.task_id " +
                "WHERE m.user_id=? AND m.dedupe_key=?", (row, number) -> message(row), ref.userId(), dedupe);
        if (!message.isEmpty()) stream.publish(ref.userId(), message.get(0));
    }

    private TaskModels.Reminder get(long userId, String publicId) {
        List<TaskModels.Reminder> rows = jdbc.query("SELECT r.public_id,t.public_id task_public_id,r.kind,r.offset_minutes,r.remind_at," +
                        "r.channel,r.sent_at,r.daily_until_done,r.revision FROM task_reminder r JOIN task t ON t.id=r.task_id " +
                        "WHERE r.public_id=? AND r.user_id=? AND r.cancelled_at IS NULL", (row, number) -> reminder(row), uuid(publicId), userId);
        if (rows.isEmpty()) throw new NotFoundException("任务提醒不存在");
        return rows.get(0);
    }

    private Values values(TaskModels.ReminderCommand command, TaskRef task, TaskModels.Reminder before) {
        if (command == null) throw new IllegalArgumentException("提醒内容不能为空");
        String kind = value(command.kind(), before == null ? null : before.kind(), List.of("RELATIVE", "ABSOLUTE"), "kind");
        String channel = value(command.channel(), before == null ? "IN_APP" : before.channel(), List.of("IN_APP"), "channel");
        Integer offset = command.offsetMinutes() == null && before != null ? before.offsetMinutes() : command.offsetMinutes();
        Instant remindAt;
        if ("RELATIVE".equals(kind)) {
            if (task.dueAt() == null) throw new IllegalArgumentException("相对提醒要求任务设置到期时间");
            if (offset == null || offset < 0 || offset > 525600) throw new IllegalArgumentException("offsetMinutes 无效");
            remindAt = task.dueAt().minusSeconds(offset * 60L);
        } else {
            offset = null;
            String raw = command.remindAt() == null && before != null ? before.remindAt() : command.remindAt();
            try { remindAt = Instant.parse(raw); } catch (Exception exception) { throw new IllegalArgumentException("remindAt 无效"); }
        }
        return new Values(kind, offset, remindAt, channel, command.dailyUntilDone() != null
                ? command.dailyUntilDone() : before != null && before.dailyUntilDone());
    }

    private TaskRef task(long userId, String publicId) {
        List<TaskRef> rows = jdbc.query("SELECT id,due_at FROM task WHERE public_id=? AND user_id=? AND deleted=FALSE",
                (row, number) -> new TaskRef(row.getLong(1), row.getTimestamp(2) == null ? null : row.getTimestamp(2).toInstant()),
                uuid(publicId), userId);
        if (rows.isEmpty()) throw new NotFoundException("任务不存在");
        return rows.get(0);
    }

    private TaskModels.Reminder reminder(ResultSet row) throws SQLException {
        Timestamp sent = row.getTimestamp("sent_at");
        return new TaskModels.Reminder(row.getString("public_id"), row.getString("task_public_id"), row.getString("kind"),
                (Integer) row.getObject("offset_minutes"), row.getTimestamp("remind_at").toInstant().toString(),
                row.getString("channel"), sent != null, sent == null ? null : sent.toInstant().toString(),
                row.getBoolean("daily_until_done"), row.getLong("revision"));
    }

    private TaskModels.InboxMessage message(ResultSet row) throws SQLException {
        Timestamp read = row.getTimestamp("read_at");
        return new TaskModels.InboxMessage(row.getString("public_id"), row.getString("category"), row.getString("type"),
                row.getString("title"), row.getString("body"), row.getString("level"), row.getString("task_public_id"),
                row.getString("deep_link"), read == null ? null : read.toInstant().toString(), row.getTimestamp("created_at").toInstant().toString());
    }

    private String value(String raw, String fallback, List<String> allowed, String field) {
        String normalized = raw == null || raw.isBlank() ? fallback : raw.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(field + " 无效");
        return normalized;
    }

    private String uuid(String raw) { try { return UUID.fromString(raw).toString(); }
        catch (Exception exception) { throw new IllegalArgumentException("ID 必须是 UUID"); } }
    private record TaskRef(long id, Instant dueAt) { }
    private record Values(String kind, Integer offsetMinutes, Instant remindAt, String channel, boolean daily) { }
    private record FireRef(long id, long userId, String channel, Instant remindAt, boolean daily, long taskId,
                           String taskPublicId, String title, String timezone) { }
}
