package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.task.TaskModels.Attachment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TaskAttachmentService {
    private static final long MAX_SIZE = 25L * 1024 * 1024;
    private final JdbcTemplate jdbc;
    private final CurrentUserResolver currentUser;
    private final TaskService tasks;
    private final Path root;

    public TaskAttachmentService(JdbcTemplate jdbc, CurrentUserResolver currentUser, TaskService tasks,
                                 @Value("${task.attachments-dir:${java.io.tmpdir}/salary-tracker-attachments}") String root) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.tasks = tasks;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public List<Attachment> list(String taskPublicId) {
        long userId = currentUser.id();
        long taskId = taskId(userId, taskPublicId);
        return jdbc.query("SELECT a.public_id,t.public_id task_public_id,a.file_name,a.mime_type,a.size_bytes," +
                        "a.checksum_sha256,a.created_at,a.revision,a.deleted FROM task_attachment a JOIN task t ON t.id=a.task_id " +
                        "WHERE a.task_id=? AND a.user_id=? AND a.deleted=FALSE ORDER BY a.created_at,a.id",
                (row, number) -> attachment(row), taskId, userId);
    }

    @Transactional
    public Attachment upload(String taskPublicId, MultipartFile file, String idempotencyKey) {
        long userId = currentUser.id();
        long taskId = taskId(userId, taskPublicId);
        String opKey = required(idempotencyKey, "Idempotency-Key", 120);
        List<Attachment> replay = jdbc.query("SELECT a.public_id,t.public_id task_public_id,a.file_name,a.mime_type,a.size_bytes," +
                        "a.checksum_sha256,a.created_at,a.revision,a.deleted FROM task_attachment a JOIN task t ON t.id=a.task_id " +
                        "WHERE a.user_id=? AND a.client_op_key=?", (row, number) -> attachment(row), userId, opKey);
        if (!replay.isEmpty()) return replay.get(0);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("附件不能为空");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("附件不能超过 25 MB");
        String fileName = fileName(file.getOriginalFilename());
        String mime = mime(file.getContentType(), fileName);
        String publicId = UUID.randomUUID().toString();
        String storageKey = userId + "/" + publicId;
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("附件路径无效");
        try {
            Files.createDirectories(target.getParent());
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String checksum = sha256(target);
            jdbc.update("INSERT INTO task_attachment(public_id,user_id,task_id,file_name,mime_type,size_bytes,checksum_sha256,storage_key,client_op_key) " +
                            "VALUES(?,?,?,?,?,?,?,?,?)", publicId, userId, taskId, fileName, mime, file.getSize(), checksum, storageKey, opKey);
            tasks.appendActivity(userId, taskPublicId, "ATTACHMENT_ADDED", "添加了附件", null);
            return get(userId, publicId);
        } catch (DuplicateKeyException duplicate) {
            deleteQuietly(target);
            List<Attachment> retry = jdbc.query("SELECT a.public_id,t.public_id task_public_id,a.file_name,a.mime_type,a.size_bytes," +
                            "a.checksum_sha256,a.created_at,a.revision,a.deleted FROM task_attachment a JOIN task t ON t.id=a.task_id " +
                            "WHERE a.user_id=? AND a.client_op_key=?", (row, number) -> attachment(row), userId, opKey);
            if (!retry.isEmpty()) return retry.get(0);
            throw duplicate;
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new IllegalStateException("附件保存失败", exception);
        } catch (RuntimeException exception) {
            // Keep the filesystem and database atomic from the user's perspective.
            deleteQuietly(target);
            throw exception;
        }
    }

    @Transactional
    public void delete(String taskPublicId, String publicId, String ifMatch) {
        long userId = currentUser.id();
        long taskId = taskId(userId, taskPublicId);
        Attachment before = get(userId, publicId);
        if (!before.taskId().equals(taskPublicId) || before.deleted()) throw new NotFoundException("附件不存在");
        long revision = revision(ifMatch);
        int changed = jdbc.update("UPDATE task_attachment SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP(3),revision=revision+1 " +
                        "WHERE public_id=? AND task_id=? AND user_id=? AND deleted=FALSE AND revision=?",
                uuid(publicId), taskId, userId, revision);
        if (changed == 0) throw new ConflictException("附件版本已变化", before.revision());
        tasks.appendActivity(userId, taskPublicId, "ATTACHMENT_DELETED", "删除了附件", null);
    }

    public Download download(String taskPublicId, String publicId) {
        long userId = currentUser.id();
        Attachment attachment = get(userId, publicId);
        if (!attachment.taskId().equals(taskPublicId) || attachment.deleted()) throw new NotFoundException("附件不存在");
        Path target = root.resolve(userId + "/" + publicId).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) throw new NotFoundException("附件文件不存在");
        try {
            return new Download(attachment, new InputStreamResource(Files.newInputStream(target)));
        } catch (IOException exception) {
            throw new NotFoundException("附件文件不存在");
        }
    }

    private Attachment get(long userId, String publicId) {
        List<Attachment> rows = jdbc.query("SELECT a.public_id,t.public_id task_public_id,a.file_name,a.mime_type,a.size_bytes," +
                        "a.checksum_sha256,a.created_at,a.revision,a.deleted FROM task_attachment a JOIN task t ON t.id=a.task_id " +
                        "WHERE a.public_id=? AND a.user_id=?", (row, number) -> attachment(row), uuid(publicId), userId);
        if (rows.isEmpty()) throw new NotFoundException("附件不存在");
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

    private Attachment attachment(ResultSet row) throws SQLException {
        String publicId = row.getString("public_id");
        return new Attachment(publicId, row.getString("task_public_id"), row.getString("file_name"),
                row.getString("mime_type"), row.getLong("size_bytes"), row.getString("checksum_sha256"),
                "/api/v1/tasks/" + row.getString("task_public_id") + "/attachments/" + publicId + "/content",
                Long.toString(currentUser.id()), instant(row.getTimestamp("created_at")), row.getLong("revision"),
                row.getBoolean("deleted"));
    }

    private String fileName(String raw) {
        String name = raw == null ? "附件" : Path.of(raw).getFileName().toString().trim();
        if (name.isBlank() || name.length() > 255 || name.contains("\0")) throw new IllegalArgumentException("附件名称无效");
        return name;
    }

    private String mime(String raw, String fileName) {
        String value = raw == null || raw.isBlank() ? "application/octet-stream" : raw.toLowerCase(Locale.ROOT);
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (value.equals("application/x-msdownload") || value.equals("application/x-sh") || value.equals("text/html") ||
                value.equals("image/svg+xml") || value.equals("application/javascript") || value.equals("text/javascript") ||
                lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerName.endsWith(".svg") ||
                lowerName.endsWith(".js") || lowerName.endsWith(".mjs") || lowerName.endsWith(".cjs") || lowerName.endsWith(".sh")) {
            throw new IllegalArgumentException("不支持的附件类型");
        }
        return value.length() > 128 ? "application/octet-stream" : value;
    }

    private String sha256(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            if (exception instanceof IOException io) throw io;
            throw new IllegalStateException("附件摘要失败", exception);
        }
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
            throw new NotFoundException("附件不存在");
        }
    }

    private String instant(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
    private void deleteQuietly(Path path) { try { Files.deleteIfExists(path); } catch (IOException ignored) { } }
    public record Download(Attachment metadata, Resource resource) { }
}
