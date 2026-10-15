package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.task.TaskModels.Comment;
import com.salarytracker.task.TaskModels.CommentCommand;
import com.salarytracker.task.TaskModels.CommentPage;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TaskCommentService {
    private static final int MAX_BODY_LENGTH = 20_000;
    private static final Pattern LINK = Pattern.compile("\\]\\(([^)]+)\\)");
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;
    private final TaskService tasks;

    public TaskCommentService(JdbcTemplate jdbc, CurrentUserResolver currentUser, TaskService tasks) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.tasks = tasks;
    }

    public CommentPage list(String taskPublicId, int page, int size) {
        long userId = currentUser.id();
        long taskId = taskId(userId, taskPublicId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM task_comment WHERE task_id=? AND user_id=? AND deleted=FALSE",
                Long.class, taskId, userId);
        List<Comment> items = jdbc.query("SELECT c.public_id,t.public_id task_public_id,c.author_user_id," +
                        "COALESCE(NULLIF(u.nickname,''),u.username) author_name,c.body_markdown,c.created_at,c.updated_at," +
                        "c.revision,c.deleted FROM task_comment c JOIN task t ON t.id=c.task_id " +
                        "JOIN app_user u ON u.id=c.author_user_id WHERE c.task_id=? AND c.user_id=? AND c.deleted=FALSE " +
                        "ORDER BY c.created_at ASC,c.id ASC LIMIT ? OFFSET ?", (row, number) -> comment(row),
                taskId, userId, safeSize, safePage * safeSize);
        return new CommentPage(items, safePage, safeSize, total);
    }

    @Transactional
    public Comment create(String taskPublicId, CommentCommand command, String idempotencyKey) {
        long userId = currentUser.id();
        long taskId = taskId(userId, taskPublicId);
        String opKey = required(idempotencyKey, "Idempotency-Key", 120);
        List<Comment> replay = jdbc.query("SELECT c.public_id,t.public_id task_public_id,c.author_user_id," +
                        "COALESCE(NULLIF(u.nickname,''),u.username) author_name,c.body_markdown,c.created_at,c.updated_at," +
                        "c.revision,c.deleted FROM task_comment c JOIN task t ON t.id=c.task_id JOIN app_user u ON u.id=c.author_user_id " +
                        "WHERE c.user_id=? AND c.client_op_key=?", (row, number) -> comment(row), userId, opKey);
        if (!replay.isEmpty()) return replay.get(0);
        String body = markdown(command == null ? null : command.bodyMarkdown());
        String publicId = UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO task_comment(public_id,user_id,task_id,author_user_id,body_markdown,client_op_key) VALUES(?,?,?,?,?,?)",
                    publicId, userId, taskId, userId, body, opKey);
        } catch (DuplicateKeyException duplicate) {
            List<Comment> retry = jdbc.query("SELECT c.public_id,t.public_id task_public_id,c.author_user_id," +
                            "COALESCE(NULLIF(u.nickname,''),u.username) author_name,c.body_markdown,c.created_at,c.updated_at," +
                            "c.revision,c.deleted FROM task_comment c JOIN task t ON t.id=c.task_id JOIN app_user u ON u.id=c.author_user_id " +
                            "WHERE c.user_id=? AND c.client_op_key=?", (row, number) -> comment(row), userId, opKey);
            if (!retry.isEmpty()) return retry.get(0);
            throw duplicate;
        }
        tasks.appendActivity(userId, taskPublicId, "COMMENT_CREATED", "添加了评论", null);
        return get(userId, publicId);
    }

    @Transactional
    public Comment update(String taskPublicId, String publicId, CommentCommand command, String ifMatch) {
        long userId = currentUser.id();
        long taskId = taskId(userId, taskPublicId);
        Comment before = get(userId, publicId);
        if (!before.taskId().equals(taskPublicId) || before.deleted()) throw new NotFoundException("任务评论不存在");
        long revision = revision(ifMatch);
        String body = markdown(command == null ? null : command.bodyMarkdown());
        int changed = jdbc.update("UPDATE task_comment SET body_markdown=?,revision=revision+1 WHERE public_id=? AND task_id=? " +
                        "AND user_id=? AND deleted=FALSE AND revision=?", body, uuid(publicId), taskId, userId, revision);
        if (changed == 0) throw new ConflictException("评论版本已变化", before.revision());
        tasks.appendActivity(userId, taskPublicId, "COMMENT_UPDATED", "修改了评论", null);
        return get(userId, publicId);
    }

    @Transactional
    public void delete(String taskPublicId, String publicId, String ifMatch) {
        long userId = currentUser.id();
        long taskId = taskId(userId, taskPublicId);
        Comment before = get(userId, publicId);
        if (!before.taskId().equals(taskPublicId) || before.deleted()) throw new NotFoundException("任务评论不存在");
        long revision = revision(ifMatch);
        int changed = jdbc.update("UPDATE task_comment SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP(3),revision=revision+1 " +
                        "WHERE public_id=? AND task_id=? AND user_id=? AND deleted=FALSE AND revision=?",
                uuid(publicId), taskId, userId, revision);
        if (changed == 0) throw new ConflictException("评论版本已变化", before.revision());
        tasks.appendActivity(userId, taskPublicId, "COMMENT_DELETED", "删除了评论", null);
    }

    private Comment get(long userId, String publicId) {
        List<Comment> rows = jdbc.query("SELECT c.public_id,t.public_id task_public_id,c.author_user_id," +
                        "COALESCE(NULLIF(u.nickname,''),u.username) author_name,c.body_markdown,c.created_at,c.updated_at," +
                        "c.revision,c.deleted FROM task_comment c JOIN task t ON t.id=c.task_id JOIN app_user u ON u.id=c.author_user_id " +
                        "WHERE c.public_id=? AND c.user_id=?", (row, number) -> comment(row), uuid(publicId), userId);
        if (rows.isEmpty()) throw new NotFoundException("任务评论不存在");
        return rows.get(0);
    }

    private long taskId(long userId, String publicId) {
        try {
            return jdbc.queryForObject("SELECT id FROM task WHERE public_id=? AND user_id=?", Long.class,
                    UUID.fromString(publicId).toString(), userId);
        } catch (Exception exception) {
            throw new NotFoundException("任务不存在");
        }
    }

    private Comment comment(ResultSet row) throws SQLException {
        return new Comment(row.getString("public_id"), row.getString("task_public_id"),
                Long.toString(row.getLong("author_user_id")), row.getString("author_name"),
                row.getString("body_markdown"), instant(row.getTimestamp("created_at")),
                instant(row.getTimestamp("updated_at")), row.getLong("revision"), row.getBoolean("deleted"));
    }

    private String markdown(String raw) {
        if (raw == null || raw.trim().isEmpty()) throw new IllegalArgumentException("评论内容不能为空");
        if (raw.length() > MAX_BODY_LENGTH || raw.indexOf('\0') >= 0) throw new IllegalArgumentException("评论内容无效或过长");
        Matcher matcher = LINK.matcher(raw);
        while (matcher.find()) {
            String target = matcher.group(1).trim().toLowerCase();
            if (!(target.startsWith("http://") || target.startsWith("https://") || target.startsWith("mailto:"))) {
                throw new IllegalArgumentException("评论链接仅支持 http、https、mailto");
            }
        }
        return raw.trim();
    }

    private long revision(String raw) {
        String value = required(raw, "If-Match", 40).replace("W/", "").replace("\"", "");
        try { return Long.parseLong(value); } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match 必须是 revision");
        }
    }

    private String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(field + " 无效");
        return value.trim();
    }

    private String uuid(String value) {
        try { return UUID.fromString(value).toString(); } catch (Exception exception) {
            throw new NotFoundException("任务评论不存在");
        }
    }

    private String instant(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
}
