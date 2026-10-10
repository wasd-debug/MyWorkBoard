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
import com.salarytracker.task.TaskModels.TaskListCommand;
import com.salarytracker.task.TaskModels.TaskTag;
import com.salarytracker.task.TaskModels.TaskTagCommand;
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
            "t.source, t.completed_at, t.revision, t.deleted, t.deleted_at, " +
            "(SELECT p.public_id FROM task p WHERE p.id = t.parent_id) parent_public_id";
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserResolver currentUser;
    private final TaskChangeLog changeLog;

    public TaskService(JdbcTemplate jdbcTemplate, CurrentUserResolver currentUser, TaskChangeLog changeLog) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
        this.changeLog = changeLog;
    }

    @Transactional
    public List<TaskList> listLists() {
        long userId = currentUser.id();
        ensureDefaults(userId);
        return jdbcTemplate.query("SELECT public_id, name, color, icon, system_key, sort_order, archived, revision FROM task_list " +
                        "WHERE user_id = ? AND deleted = FALSE ORDER BY sort_order, id",
                (result, rowNum) -> new TaskList(result.getString("public_id"), result.getString("name"),
                        result.getString("color"), result.getString("icon"), result.getString("system_key"),
                        result.getInt("sort_order"), result.getBoolean("archived"), result.getLong("revision")),
                userId);
    }

    public List<TaskList> listDeletedLists() {
        return jdbcTemplate.query("SELECT public_id, name, color, icon, system_key, sort_order, archived, revision FROM task_list " +
                        "WHERE user_id = ? AND deleted = TRUE ORDER BY deleted_at DESC, id DESC",
                (result, rowNum) -> taskList(result), currentUser.id());
    }

    @Transactional
    public TaskPage listTasks(String listId, String status, int page, int size) {
        return listTasks(listId, status, null, null, page, size);
    }

    @Transactional
    public TaskPage listTasks(String listId, String status, String view, String tagId, int page, int size) {
        long userId = currentUser.id();
        ensureDefaults(userId);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        String normalizedStatus = optionalStatus(status);
        boolean trash = "TRASH".equalsIgnoreCase(view);
        StringBuilder where = new StringBuilder(" FROM task t JOIN task_list l ON l.id = t.list_id " +
                "WHERE t.user_id = ? AND t.deleted = " + (trash ? "TRUE" : "FALSE") + " AND l.deleted = FALSE");
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
        String normalizedView = view == null ? "" : view.trim().toUpperCase(Locale.ROOT);
        switch (normalizedView) {
            case "TODAY" -> where.append(" AND l.archived=FALSE AND t.status='OPEN' AND t.due_at < DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY)");
            case "NEXT7" -> where.append(" AND l.archived=FALSE AND t.status='OPEN' AND t.due_at >= CURRENT_DATE AND t.due_at < DATE_ADD(CURRENT_DATE, INTERVAL 8 DAY)");
            case "INBOX" -> where.append(" AND l.system_key='INBOX'");
            case "ALL", "TRASH", "" -> { }
            case "COMPLETED" -> where.append(" AND t.status='COMPLETED'");
            default -> throw new IllegalArgumentException("view 无效");
        }
        if (tagId != null && !tagId.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM task_tag_link ttl JOIN task_tag tag ON tag.id=ttl.tag_id " +
                    "WHERE ttl.task_id=t.id AND tag.public_id=? AND tag.user_id=? AND tag.deleted=FALSE)");
            args.add(validUuid(tagId, "tagId"));
            args.add(userId);
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

    @Transactional
    public TaskList createList(TaskListCommand command, String idempotencyKey) {
        return createList(command, UUID.randomUUID().toString(), requiredText(idempotencyKey, "Idempotency-Key", 120));
    }

    @Transactional
    TaskList createListForSync(TaskListCommand command, String publicId, String opId) {
        return createList(command, validUuid(publicId, "entityId"), requiredText(opId, "opId", 120));
    }

    private TaskList createList(TaskListCommand command, String publicId, String opId) {
        long userId = currentUser.id();
        ensureDefaults(userId);
        List<String> replay = jdbcTemplate.query("SELECT entity_id FROM task_sync_oplog WHERE user_id=? AND op_id=? AND entity_type='task-list'",
                (result, rowNum) -> result.getString(1), userId, opId);
        if (!replay.isEmpty()) return listForUser(userId, replay.get(0), false);
        String name = requiredText(command == null ? null : command.name(), "name", 120);
        int sortOrder = command.sortOrder() == null ? nextOrder("task_list", userId) : command.sortOrder();
        jdbcTemplate.update("INSERT INTO task_list(public_id,user_id,name,color,icon,sort_order,archived) VALUES(?,?,?,?,?,?,?)",
                publicId, userId, name, optionalText(command.color(), 24), optionalText(command.icon(), 40), sortOrder,
                Boolean.TRUE.equals(command.archived()));
        TaskList created = listForUser(userId, publicId, false);
        changeLog.append(userId, opId, "task-list", publicId, "UPSERT", syncEntity(created, false));
        return created;
    }

    @Transactional
    public TaskList updateList(String publicId, TaskListCommand command, String ifMatch) {
        return updateList(publicId, command, ifMatch, UUID.randomUUID().toString());
    }

    @Transactional
    TaskList updateListForSync(String publicId, TaskListCommand command, long revision, String opId) {
        return updateList(publicId, command, String.valueOf(revision), opId);
    }

    private TaskList updateList(String publicId, TaskListCommand command, String ifMatch, String opId) {
        long userId = currentUser.id();
        TaskList before = listForUser(userId, publicId, false);
        if ("INBOX".equals(before.systemKey()) && Boolean.TRUE.equals(command.archived())) {
            throw new ConflictException("收件箱不可归档", before.revision());
        }
        if ("INBOX".equals(before.systemKey()) && command.name() != null && !before.name().equals(command.name().trim())) {
            throw new ConflictException("收件箱不可重命名", before.revision());
        }
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("清单版本已变化", before.revision());
        int updated = jdbcTemplate.update("UPDATE task_list SET name=?,color=?,icon=?,sort_order=?,archived=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND deleted=FALSE AND revision=?",
                command.name() == null ? before.name() : requiredText(command.name(), "name", 120),
                command.color() == null ? before.color() : optionalText(command.color(), 24),
                command.icon() == null ? before.icon() : optionalText(command.icon(), 40),
                command.sortOrder() == null ? before.sortOrder() : command.sortOrder(),
                command.archived() == null ? before.archived() : command.archived(), validUuid(publicId, "publicId"),
                userId, expected);
        if (updated == 0) throw new ConflictException("清单版本已变化", before.revision());
        TaskList changed = listForUser(userId, publicId, false);
        changeLog.append(userId, opId, "task-list", publicId, "UPSERT", syncEntity(changed, false));
        return changed;
    }

    @Transactional
    public DeletedResource deleteList(String publicId, String ifMatch) {
        return deleteList(publicId, ifMatch, UUID.randomUUID().toString());
    }

    @Transactional
    DeletedResource deleteListForSync(String publicId, long revision, String opId) {
        return deleteList(publicId, String.valueOf(revision), opId);
    }

    private DeletedResource deleteList(String publicId, String ifMatch, String opId) {
        long userId = currentUser.id();
        TaskList list = listForUser(userId, publicId, false);
        if ("INBOX".equals(list.systemKey())) throw new ConflictException("收件箱不可删除", list.revision());
        long expected = requiredRevision(ifMatch);
        if (list.revision() != expected) throw new ConflictException("清单版本已变化", list.revision());
        Long listId = jdbcTemplate.queryForObject("SELECT id FROM task_list WHERE public_id=? AND user_id=?", Long.class,
                publicId, userId);
        int changed = jdbcTemplate.update("UPDATE task_list SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 " +
                "WHERE id=? AND revision=?", listId, expected);
        if (changed == 0) throw new ConflictException("清单版本已变化", listForUser(userId, publicId, false).revision());
        DeletedResource deleted = new DeletedResource(publicId, expected + 1, true);
        changeLog.append(userId, opId, "task-list", publicId, "DELETE", syncEntity(list, true));
        return deleted;
    }

    @Transactional
    public TaskList restoreList(String publicId, String ifMatch) {
        long userId = currentUser.id();
        TaskList before = listForUser(userId, publicId, true);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("清单版本已变化", before.revision());
        int updated = jdbcTemplate.update("UPDATE task_list SET deleted=FALSE,deleted_at=NULL,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND deleted=TRUE AND revision=?",
                validUuid(publicId, "publicId"), userId, expected);
        if (updated == 0) throw new ConflictException("清单版本已变化", before.revision());
        TaskList restored = listForUser(userId, publicId, false);
        changeLog.append(userId, UUID.randomUUID().toString(), "task-list", publicId, "UPSERT",
                syncEntity(restored, false));
        return restored;
    }

    public List<TaskTag> listTags() {
        long userId = currentUser.id();
        return jdbcTemplate.query("SELECT t.public_id,p.public_id parent_public_id,t.name,t.color,t.sort_order,t.revision " +
                        "FROM task_tag t LEFT JOIN task_tag p ON p.id=t.parent_id WHERE t.user_id=? AND t.deleted=FALSE " +
                        "ORDER BY t.sort_order,t.id", (result, rowNum) -> tag(result), userId);
    }

    @Transactional
    public TaskTag createTag(TaskTagCommand command) {
        return createTag(command, UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    @Transactional
    TaskTag createTagForSync(TaskTagCommand command, String publicId, String opId) {
        return createTag(command, validUuid(publicId, "entityId"), requiredText(opId, "opId", 120));
    }

    private TaskTag createTag(TaskTagCommand command, String publicId, String opId) {
        long userId = currentUser.id();
        String name = requiredText(command == null ? null : command.name(), "name", 30);
        ensureUniqueTagName(userId, name, null);
        Long parentId = resolveTagParent(userId, command.parentId(), null);
        jdbcTemplate.update("INSERT INTO task_tag(public_id,user_id,parent_id,name,normalized_name,color,sort_order) VALUES(?,?,?,?,?,?,?)",
                publicId, userId, parentId, name, name.toLowerCase(Locale.ROOT), optionalText(command.color(), 24),
                command.sortOrder() == null ? nextOrder("task_tag", userId) : command.sortOrder());
        TaskTag created = tagForUser(userId, publicId);
        changeLog.append(userId, opId, "task-tag", publicId, "UPSERT", syncEntity(created, false));
        return created;
    }

    @Transactional
    public TaskTag updateTag(String publicId, TaskTagCommand command, String ifMatch) {
        return updateTag(publicId, command, ifMatch, UUID.randomUUID().toString());
    }

    @Transactional
    TaskTag updateTagForSync(String publicId, TaskTagCommand command, long revision, String opId) {
        return updateTag(publicId, command, String.valueOf(revision), opId);
    }

    private TaskTag updateTag(String publicId, TaskTagCommand command, String ifMatch, String opId) {
        long userId = currentUser.id();
        TaskTag before = tagForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("标签版本已变化", before.revision());
        String name = command.name() == null ? before.name() : requiredText(command.name(), "name", 30);
        ensureUniqueTagName(userId, name, publicId);
        Long parentId = command.parentId() == null ? resolveTagParent(userId, before.parentId(), publicId)
                : resolveTagParent(userId, command.parentId(), publicId);
        int updated = jdbcTemplate.update("UPDATE task_tag SET parent_id=?,name=?,normalized_name=?,color=?,sort_order=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND deleted=FALSE AND revision=?", parentId, name,
                name.toLowerCase(Locale.ROOT), command.color() == null ? before.color() : optionalText(command.color(), 24),
                command.sortOrder() == null ? before.sortOrder() : command.sortOrder(), publicId, userId, expected);
        if (updated == 0) throw new ConflictException("标签版本已变化", tagForUser(userId, publicId).revision());
        TaskTag changed = tagForUser(userId, publicId);
        changeLog.append(userId, opId, "task-tag", publicId, "UPSERT", syncEntity(changed, false));
        return changed;
    }

    @Transactional
    public DeletedResource deleteTag(String publicId, String ifMatch) {
        return deleteTag(publicId, ifMatch, UUID.randomUUID().toString());
    }

    @Transactional
    DeletedResource deleteTagForSync(String publicId, long revision, String opId) {
        return deleteTag(publicId, String.valueOf(revision), opId);
    }

    private DeletedResource deleteTag(String publicId, String ifMatch, String opId) {
        long userId = currentUser.id();
        TaskTag tag = tagForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (tag.revision() != expected) throw new ConflictException("标签版本已变化", tag.revision());
        Long tagId = jdbcTemplate.queryForObject("SELECT id FROM task_tag WHERE public_id=? AND user_id=?", Long.class, publicId, userId);
        List<String> affectedTasks = jdbcTemplate.query("SELECT t.public_id FROM task t JOIN task_tag_link link ON link.task_id=t.id " +
                "JOIN task_list l ON l.id=t.list_id WHERE link.tag_id=? AND t.user_id=? AND l.deleted=FALSE",
                (result, rowNum) -> result.getString(1), tagId, userId);
        jdbcTemplate.update("DELETE FROM task_tag_link WHERE tag_id=?", tagId);
        for (String taskId : affectedTasks) {
            jdbcTemplate.update("UPDATE task SET revision=revision+1 WHERE public_id=? AND user_id=?", taskId, userId);
            boolean isDeleted = jdbcTemplate.queryForObject("SELECT deleted FROM task WHERE public_id=? AND user_id=?", Boolean.class, taskId, userId);
            changeLog.append(userId, UUID.randomUUID().toString(), taskId, isDeleted ? "DELETE" : "UPSERT",
                    syncEntity(taskForUser(userId, taskId, isDeleted), isDeleted));
        }
        List<String> children = jdbcTemplate.query("SELECT public_id FROM task_tag WHERE parent_id=? AND deleted=FALSE",
                (result, rowNum) -> result.getString(1), tagId);
        jdbcTemplate.update("UPDATE task_tag SET parent_id=NULL,revision=revision+1 WHERE parent_id=?", tagId);
        for (String child : children) changeLog.append(userId, UUID.randomUUID().toString(), "task-tag", child,
                "UPSERT", syncEntity(tagForUser(userId, child), false));
        int updated = jdbcTemplate.update("UPDATE task_tag SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 WHERE id=? AND revision=?", tagId, expected);
        if (updated == 0) throw new ConflictException("标签版本已变化", tagForUser(userId, publicId).revision());
        DeletedResource deleted = new DeletedResource(publicId, expected + 1, true);
        changeLog.append(userId, opId, "task-tag", publicId, "DELETE", syncEntity(tag, true));
        return deleted;
    }

    @Transactional
    public TaskItem restore(String publicId, String ifMatch) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId, true);
        long expected = requiredRevision(ifMatch);
        if (!before.deleted()) throw new ConflictException("任务未在垃圾桶中", before.revision());
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        List<String> deletedTree = jdbcTemplate.query("SELECT public_id FROM task WHERE user_id=? AND deleted=TRUE " +
                "AND deleted_root_public_id=?", (result, rowNum) -> result.getString(1), userId, publicId);
        int updated = jdbcTemplate.update("UPDATE task SET deleted=FALSE,deleted_at=NULL,deleted_root_public_id=NULL,revision=revision+1 WHERE public_id=? AND user_id=? AND revision=?",
                publicId, userId, expected);
        if (updated == 0) throw new ConflictException("任务版本已变化", before.revision());
        for (String child : deletedTree) {
            if (child.equals(publicId)) continue;
            jdbcTemplate.update("UPDATE task SET deleted=FALSE,deleted_at=NULL,deleted_root_public_id=NULL,revision=revision+1 WHERE public_id=? AND user_id=? AND deleted=TRUE",
                    child, userId);
            changeLog.append(userId, UUID.randomUUID().toString(), child, "UPSERT", syncEntity(taskForUser(userId, child), false));
        }
        TaskItem restored = taskForUser(userId, publicId);
        changeLog.append(userId, UUID.randomUUID().toString(), publicId, "UPSERT", syncEntity(restored, false));
        return restored;
    }

    @Transactional
    public DeletedResource purge(String publicId, String confirmation) {
        long userId = currentUser.id();
        TaskItem task = taskForUser(userId, publicId, true);
        if (!(("DELETE:" + publicId).equals(confirmation))) {
            throw new IllegalArgumentException("永久清除需要输入 DELETE:任务ID");
        }
        Long taskId = jdbcTemplate.queryForObject("SELECT id FROM task WHERE public_id=? AND user_id=? AND deleted=TRUE",
                Long.class, publicId, userId);
        jdbcTemplate.update("DELETE FROM task_tag_link WHERE task_id=?", taskId);
        jdbcTemplate.update("DELETE FROM task_checklist_item WHERE task_id=?", taskId);
        List<String> children = jdbcTemplate.query("SELECT public_id FROM task WHERE parent_id=? AND deleted=FALSE",
                (result, rowNum) -> result.getString(1), taskId);
        jdbcTemplate.update("UPDATE task SET parent_id=NULL,revision=revision+1 WHERE parent_id=?", taskId);
        for (String child : children) changeLog.append(userId, UUID.randomUUID().toString(), child,
                "UPSERT", syncEntity(taskForUser(userId, child), false));
        jdbcTemplate.update("DELETE FROM task WHERE id=?", taskId);
        changeLog.append(userId, UUID.randomUUID().toString(), publicId, "PURGE", syncEntity(task, true));
        return new DeletedResource(publicId, task.revision(), true);
    }

    public TaskItem get(String publicId) {
        return taskForUser(currentUser.id(), publicId);
    }

    @Transactional
    public TaskItem create(TaskCommand command, String idempotencyKey) {
        return create(command, "OPEN", idempotencyKey, UUID.randomUUID().toString());
    }

    @Transactional
    TaskItem createForSync(TaskCommand command, String status, String opId, String publicId) {
        return create(command, status, opId, validUuid(publicId, "entityId"));
    }

    private TaskItem create(TaskCommand command, String status, String opKey, String publicId) {
        long userId = currentUser.id();
        opKey = requiredText(opKey, "opId", 120);
        List<String> replay = jdbcTemplate.query("SELECT public_id FROM task WHERE user_id = ? AND client_op_key = ?",
                (result, rowNum) -> result.getString(1), userId, opKey);
        if (!replay.isEmpty()) return taskForUser(userId, replay.get(0));
        ensureDefaults(userId);
        long listDatabaseId = resolveListId(userId, command == null ? null : command.listId());
        Long parentDatabaseId = resolveParentId(userId, command == null ? null : command.parentId(), null);
        if (parentDatabaseId != null) {
            long parentListId = jdbcTemplate.queryForObject("SELECT list_id FROM task WHERE id=?", Long.class, parentDatabaseId);
            if (command.listId() == null || command.listId().isBlank()) listDatabaseId = parentListId;
            else if (parentListId != listDatabaseId) throw new IllegalArgumentException("子任务与父任务必须位于同一清单");
        }
        ValidatedTask values = validate(command, null);
        String normalizedStatus = enumValue(status, "OPEN", List.of("OPEN", "COMPLETED"), "status");
        try {
            jdbcTemplate.update("INSERT INTO task (public_id, user_id, list_id, parent_id, title, description, status, priority, " +
                            "start_at, due_at, all_day, timezone, duration_minutes, source, completed_at, created_by, client_op_key) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WEB', ?, ?, ?)",
                    publicId, userId, listDatabaseId, parentDatabaseId, values.title(), values.description(), normalizedStatus, values.priority(),
                    timestamp(values.startAt()), timestamp(values.dueAt()), values.allDay(), values.timezone(),
                    values.durationMinutes(), "COMPLETED".equals(normalizedStatus) ? Timestamp.from(Instant.now()) : null,
                    userId, opKey);
        } catch (DuplicateKeyException exception) {
            List<String> concurrent = jdbcTemplate.query(
                    "SELECT public_id FROM task WHERE user_id = ? AND client_op_key = ?",
                    (result, rowNum) -> result.getString(1), userId, opKey);
            if (!concurrent.isEmpty()) return taskForUser(userId, concurrent.get(0));
            throw exception;
        }
        replaceTagsAndChecklist(userId, publicId, command.tagIds(), command.checklist());
        TaskItem created = taskForUser(userId, publicId);
        changeLog.append(userId, opKey, publicId, "UPSERT", syncEntity(created, false));
        return created;
    }

    @Transactional
    public TaskItem update(String publicId, TaskCommand command, String ifMatch) {
        return update(publicId, command, null, ifMatch, UUID.randomUUID().toString());
    }

    @Transactional
    TaskItem updateForSync(String publicId, TaskCommand command, String status, long revision, String opId) {
        return update(publicId, command, status, String.valueOf(revision), opId);
    }

    private TaskItem update(String publicId, TaskCommand command, String status, String ifMatch, String opId) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        long listDatabaseId = resolveListId(userId, command == null || command.listId() == null ? before.listId() : command.listId());
        String parentId = command == null || command.parentId() == null ? before.parentId() : command.parentId();
        Long parentDatabaseId = resolveParentId(userId, parentId, publicId);
        if (parentDatabaseId != null && jdbcTemplate.queryForObject("SELECT list_id FROM task WHERE id=?", Long.class,
                parentDatabaseId) != listDatabaseId) {
            if (!before.listId().equals(command.listId())) parentDatabaseId = null;
            else throw new IllegalArgumentException("子任务与父任务必须位于同一清单");
        }
        ValidatedTask values = validate(command, before);
        String normalizedStatus = status == null ? before.status()
                : enumValue(status, before.status(), List.of("OPEN", "COMPLETED"), "status");
        int updated = jdbcTemplate.update("UPDATE task SET list_id = ?, parent_id = ?, title = ?, description = ?, status = ?, priority = ?, " +
                        "start_at = ?, due_at = ?, all_day = ?, timezone = ?, duration_minutes = ?, completed_at = ?, revision = revision + 1 " +
                        "WHERE public_id = ? AND user_id = ? AND deleted = FALSE AND revision = ?",
                listDatabaseId, parentDatabaseId, values.title(), values.description(), normalizedStatus, values.priority(), timestamp(values.startAt()),
                timestamp(values.dueAt()), values.allDay(), values.timezone(), values.durationMinutes(),
                "COMPLETED".equals(normalizedStatus) ? timestamp(before.completedAt() == null
                        ? Instant.now() : Instant.parse(before.completedAt())) : null,
                validUuid(publicId, "publicId"), userId, expected);
        ensureUpdated(updated, userId, publicId);
        replaceTagsAndChecklist(userId, publicId, command.tagIds(), command.checklist());
        if (!before.listId().equals(command.listId()) && command.listId() != null) {
            List<String> children = jdbcTemplate.query("WITH RECURSIVE tree AS (" +
                    "SELECT id,public_id FROM task WHERE public_id=? AND user_id=? UNION ALL " +
                    "SELECT t.id,t.public_id FROM task t JOIN tree p ON t.parent_id=p.id WHERE t.user_id=?) " +
                    "SELECT public_id FROM tree WHERE public_id<>?", (result, rowNum) -> result.getString(1),
                    publicId, userId, userId, publicId);
            for (String child : children) {
                jdbcTemplate.update("UPDATE task SET list_id=?,revision=revision+1 WHERE public_id=? AND user_id=?",
                        listDatabaseId, child, userId);
                List<Boolean> deleted = jdbcTemplate.query("SELECT deleted FROM task WHERE public_id=? AND user_id=?",
                        (result, rowNum) -> result.getBoolean(1), child, userId);
                boolean isDeleted = deleted.get(0);
                changeLog.append(userId, UUID.randomUUID().toString(), child, isDeleted ? "DELETE" : "UPSERT",
                        syncEntity(taskForUser(userId, child, isDeleted), isDeleted));
            }
        }
        TaskItem changed = taskForUser(userId, publicId);
        changeLog.append(userId, opId, publicId, "UPSERT", syncEntity(changed, false));
        return changed;
    }

    @Transactional
    public TaskItem complete(String publicId, String ifMatch) {
        return changeStatus(publicId, ifMatch, "COMPLETED", UUID.randomUUID().toString());
    }

    @Transactional
    public TaskItem reopen(String publicId, String ifMatch) {
        return changeStatus(publicId, ifMatch, "OPEN", UUID.randomUUID().toString());
    }

    @Transactional
    public DeletedResource delete(String publicId, String ifMatch) {
        return delete(publicId, ifMatch, UUID.randomUUID().toString());
    }

    @Transactional
    DeletedResource deleteForSync(String publicId, long revision, String opId) {
        return delete(publicId, String.valueOf(revision), opId);
    }

    private DeletedResource delete(String publicId, String ifMatch, String opId) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        List<String> descendants = jdbcTemplate.query("WITH RECURSIVE tree AS (" +
                "SELECT id,public_id FROM task WHERE public_id=? AND user_id=? UNION ALL " +
                "SELECT t.id,t.public_id FROM task t JOIN tree p ON t.parent_id=p.id WHERE t.user_id=?) " +
                "SELECT t.public_id FROM tree JOIN task t ON t.id=tree.id WHERE t.deleted=FALSE AND t.public_id<>?",
                (result, rowNum) -> result.getString(1), publicId, userId, userId, publicId);
        int updated = jdbcTemplate.update("UPDATE task SET deleted = TRUE, deleted_at = CURRENT_TIMESTAMP, deleted_root_public_id=?, " +
                        "revision = revision + 1 WHERE public_id = ? AND user_id = ? AND deleted = FALSE AND revision = ?",
                publicId, validUuid(publicId, "publicId"), userId, expected);
        ensureUpdated(updated, userId, publicId);
        for (String child : descendants) {
            jdbcTemplate.update("UPDATE task SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_root_public_id=?,revision=revision+1 " +
                    "WHERE public_id=? AND user_id=? AND deleted=FALSE", publicId, child, userId);
            changeLog.append(userId, UUID.randomUUID().toString(), child, "DELETE", syncEntity(taskForUser(userId, child, true), true));
        }
        DeletedResource deleted = new DeletedResource(publicId, expected + 1, true);
        changeLog.append(userId, opId, publicId, "DELETE", syncEntity(taskForUser(userId, publicId, true), true));
        return deleted;
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

    private TaskItem changeStatus(String publicId, String ifMatch, String status, String opId) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        int updated = jdbcTemplate.update("UPDATE task SET status = ?, completed_at = " +
                        ("COMPLETED".equals(status) ? "CURRENT_TIMESTAMP" : "NULL") +
                        ", revision = revision + 1 WHERE public_id = ? AND user_id = ? AND deleted = FALSE AND revision = ?",
                status, validUuid(publicId, "publicId"), userId, expected);
        ensureUpdated(updated, userId, publicId);
        TaskItem changed = taskForUser(userId, publicId);
        changeLog.append(userId, opId, publicId, "UPSERT", syncEntity(changed, false));
        return changed;
    }

    TaskItem currentForSync(String publicId) {
        return taskForUser(currentUser.id(), publicId);
    }

    TaskModels.SyncEntity deletedEntityForSync(String publicId) {
        return syncEntity(taskForUser(currentUser.id(), publicId, true), true);
    }

    boolean existsForAnotherUser(String publicId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task WHERE public_id = ? AND user_id <> ?",
                Long.class, validUuid(publicId, "entityId"), currentUser.id()) > 0;
    }

    static TaskModels.SyncEntity syncEntity(TaskItem item, boolean deleted) {
        return new TaskModels.SyncEntity(item.publicId(), item.listId(), item.title(), item.description(), item.status(),
                item.priority(), item.startAt(), item.dueAt(), item.allDay(), item.timezone(), item.durationMinutes(),
                item.source(), item.completedAt(), item.revision(), deleted, item.parentId(), item.tagIds(),
                item.checklist(), item.deletedAt(), null, null, null, null, null, null);
    }

    static TaskModels.SyncEntity syncEntity(TaskList item, boolean deleted) {
        return new TaskModels.SyncEntity(item.publicId(), null, null, null, null, null, null, null, false,
                null, null, null, null, item.revision() + (deleted ? 1 : 0), deleted, null, List.of(), List.of(), null,
                item.name(), item.color(), item.icon(), item.systemKey(), item.sortOrder(), item.archived());
    }

    static TaskModels.SyncEntity syncEntity(TaskTag item, boolean deleted) {
        return new TaskModels.SyncEntity(item.publicId(), null, null, null, null, null, null, null, false,
                null, null, null, null, item.revision() + (deleted ? 1 : 0), deleted, item.parentId(), List.of(), List.of(), null,
                item.name(), item.color(), null, null, item.sortOrder(), null);
    }

    TaskList currentListForSync(String publicId) { return listForUser(currentUser.id(), publicId, false); }
    TaskTag currentTagForSync(String publicId) { return tagForUser(currentUser.id(), publicId); }
    boolean listExistsForAnotherUser(String publicId) { return existsForAnother("task_list", publicId); }
    boolean tagExistsForAnotherUser(String publicId) { return existsForAnother("task_tag", publicId); }

    private boolean existsForAnother(String table, String publicId) {
        if (!List.of("task_list", "task_tag").contains(table)) return false;
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE public_id=? AND user_id<>?",
                Long.class, validUuid(publicId, "entityId"), currentUser.id()) > 0;
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

    private Long resolveParentId(long userId, String publicId, String currentPublicId) {
        if (publicId == null || publicId.isBlank()) return null;
        String parentPublicId = validUuid(publicId, "parentId");
        if (parentPublicId.equals(currentPublicId)) throw new IllegalArgumentException("任务不能作为自己的子任务");
        List<ParentRef> rows = jdbcTemplate.query("SELECT t.id,t.parent_id,p.parent_id grandparent_id FROM task t " +
                        "LEFT JOIN task p ON p.id=t.parent_id WHERE t.public_id=? AND t.user_id=? AND t.deleted=FALSE",
                (result, rowNum) -> new ParentRef(result.getLong("id"), (Long) result.getObject("parent_id"),
                        (Long) result.getObject("grandparent_id")), parentPublicId, userId);
        if (rows.isEmpty()) throw new NotFoundException("父任务不存在");
        if (rows.get(0).grandparentId() != null) throw new IllegalArgumentException("子任务最多支持两层");
        if (currentPublicId != null) {
            long descendants = jdbcTemplate.queryForObject("WITH RECURSIVE descendants AS (" +
                    "SELECT id,public_id,0 depth FROM task WHERE public_id=? AND user_id=? UNION ALL " +
                    "SELECT t.id,t.public_id,d.depth+1 FROM task t JOIN descendants d ON t.parent_id=d.id) " +
                    "SELECT COUNT(*) FROM descendants WHERE public_id=?", Long.class, currentPublicId, userId, parentPublicId);
            if (descendants > 0) throw new IllegalArgumentException("任务父子关系不能形成循环");
            int subtreeDepth = jdbcTemplate.queryForObject("WITH RECURSIVE descendants AS (" +
                    "SELECT id,0 depth FROM task WHERE public_id=? AND user_id=? UNION ALL " +
                    "SELECT t.id,d.depth+1 FROM task t JOIN descendants d ON t.parent_id=d.id) " +
                    "SELECT COALESCE(MAX(depth),0) FROM descendants", Integer.class, currentPublicId, userId);
            if (subtreeDepth + (rows.get(0).parentId() == null ? 1 : 2) > 2) {
                throw new IllegalArgumentException("移动后子任务超过两层");
            }
        }
        return rows.get(0).id();
    }

    private void replaceTagsAndChecklist(long userId, String taskPublicId, List<String> tagIds,
                                         List<TaskModels.ChecklistCommand> checklist) {
        Long taskId = jdbcTemplate.queryForObject("SELECT id FROM task WHERE public_id=? AND user_id=?", Long.class,
                taskPublicId, userId);
        if (tagIds != null) {
            if (tagIds.size() > 20) throw new IllegalArgumentException("单个任务最多添加 20 个标签");
            jdbcTemplate.update("DELETE FROM task_tag_link WHERE task_id=?", taskId);
            for (String tagPublicId : tagIds.stream().distinct().toList()) {
                List<Long> rows = jdbcTemplate.query("SELECT id FROM task_tag WHERE public_id=? AND user_id=? AND deleted=FALSE",
                        (result, rowNum) -> result.getLong(1), validUuid(tagPublicId, "tagId"), userId);
                if (rows.isEmpty()) throw new NotFoundException("任务标签不存在");
                jdbcTemplate.update("INSERT INTO task_tag_link(task_id,tag_id) VALUES(?,?)", taskId, rows.get(0));
            }
        }
        if (checklist != null) {
            jdbcTemplate.update("UPDATE task_checklist_item SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 " +
                    "WHERE task_id=? AND deleted=FALSE", taskId);
            int position = 0;
            for (TaskModels.ChecklistCommand item : checklist) {
                String title = requiredText(item.title(), "checklist.title", 500);
                String itemId = item.publicId() == null || item.publicId().isBlank()
                        ? UUID.randomUUID().toString() : validUuid(item.publicId(), "checklist.publicId");
                long foreignItem = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_checklist_item " +
                        "WHERE public_id=? AND (user_id<>? OR task_id<>?)", Long.class, itemId, userId, taskId);
                if (foreignItem > 0) throw new IllegalArgumentException("检查项不属于当前任务");
                int order = item.sortOrder() == null ? position++ : item.sortOrder();
                jdbcTemplate.update("INSERT INTO task_checklist_item(public_id,user_id,task_id,title,completed,sort_order) " +
                                "VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE title=VALUES(title),completed=VALUES(completed)," +
                                "sort_order=VALUES(sort_order),deleted=FALSE,deleted_at=NULL,revision=revision+1",
                        itemId, userId, taskId, title, Boolean.TRUE.equals(item.completed()), order);
            }
        }
    }

    private TaskItem taskForUser(long userId, String publicId) {
        return taskForUser(userId, publicId, false);
    }

    private TaskItem taskForUser(long userId, String publicId, boolean deleted) {
        List<TaskItem> rows = jdbcTemplate.query("SELECT " + TASK_COLUMNS +
                        " FROM task t JOIN task_list l ON l.id = t.list_id " +
                        "WHERE t.public_id = ? AND t.user_id = ? AND t.deleted = ? AND l.deleted = FALSE",
                (result, rowNum) -> task(result), validUuid(publicId, "publicId"), userId, deleted);
        if (rows.isEmpty()) throw new NotFoundException("任务不存在");
        return rows.get(0);
    }

    private TaskList listForUser(long userId, String publicId, boolean deleted) {
        List<TaskList> rows = jdbcTemplate.query("SELECT public_id,name,color,icon,system_key,sort_order,archived,revision " +
                        "FROM task_list WHERE public_id=? AND user_id=? AND deleted=?",
                (result, rowNum) -> taskList(result),
                validUuid(publicId, "publicId"), userId, deleted);
        if (rows.isEmpty()) throw new NotFoundException("任务清单不存在");
        return rows.get(0);
    }

    private TaskList taskList(ResultSet result) throws SQLException {
        return new TaskList(result.getString("public_id"), result.getString("name"), result.getString("color"),
                result.getString("icon"), result.getString("system_key"), result.getInt("sort_order"),
                result.getBoolean("archived"), result.getLong("revision"));
    }

    private TaskTag tagForUser(long userId, String publicId) {
        List<TaskTag> rows = jdbcTemplate.query("SELECT t.public_id,p.public_id parent_public_id,t.name,t.color,t.sort_order,t.revision " +
                        "FROM task_tag t LEFT JOIN task_tag p ON p.id=t.parent_id WHERE t.public_id=? AND t.user_id=? AND t.deleted=FALSE",
                (result, rowNum) -> tag(result), validUuid(publicId, "publicId"), userId);
        if (rows.isEmpty()) throw new NotFoundException("任务标签不存在");
        return rows.get(0);
    }

    private void ensureUniqueTagName(long userId, String name, String publicId) {
        long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_tag WHERE user_id=? AND normalized_name=? " +
                "AND (? IS NULL OR public_id<>?)", Long.class, userId, name.toLowerCase(Locale.ROOT), publicId, publicId);
        if (count > 0) throw new ConflictException("标签名称已存在", 0);
    }

    private TaskTag tag(ResultSet result) throws SQLException {
        return new TaskTag(result.getString("public_id"), result.getString("parent_public_id"), result.getString("name"),
                result.getString("color"), result.getInt("sort_order"), result.getLong("revision"));
    }

    private Long resolveTagParent(long userId, String publicId, String currentPublicId) {
        if (publicId == null || publicId.isBlank()) return null;
        String parent = validUuid(publicId, "parentId");
        if (parent.equals(currentPublicId)) throw new IllegalArgumentException("标签不能作为自己的父标签");
        List<long[]> rows = jdbcTemplate.query("SELECT id,parent_id FROM task_tag WHERE public_id=? AND user_id=? AND deleted=FALSE",
                (result, rowNum) -> new long[]{result.getLong("id"), result.getLong("parent_id")}, parent, userId);
        if (rows.isEmpty()) throw new NotFoundException("父标签不存在");
        if (rows.get(0)[1] != 0) throw new IllegalArgumentException("标签最多支持两层");
        if (currentPublicId != null && jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_tag child " +
                "JOIN task_tag current ON child.parent_id=current.id WHERE current.public_id=? AND child.deleted=FALSE",
                Long.class, currentPublicId) > 0) throw new IllegalArgumentException("有子标签的标签不能移入其他标签");
        return rows.get(0)[0];
    }

    private int nextOrder(String table, long userId) {
        if (!List.of("task_list", "task_tag").contains(table)) throw new IllegalArgumentException("排序资源无效");
        return jdbcTemplate.queryForObject("SELECT COALESCE(MAX(sort_order),-1)+1 FROM " + table + " WHERE user_id=?",
                Integer.class, userId);
    }

    private TaskItem task(ResultSet result) throws SQLException {
        String publicId = result.getString("public_id");
        List<String> tags = jdbcTemplate.query("SELECT t.public_id FROM task_tag t JOIN task_tag_link l ON l.tag_id=t.id " +
                        "JOIN task x ON x.id=l.task_id WHERE x.public_id=? ORDER BY t.sort_order, t.id",
                (row, rowNum) -> row.getString(1), publicId);
        List<TaskModels.ChecklistItem> checklist = jdbcTemplate.query("SELECT public_id,title,completed,sort_order,revision " +
                        "FROM task_checklist_item WHERE task_id=(SELECT id FROM task WHERE public_id=? AND user_id=?) " +
                        "AND deleted=FALSE ORDER BY sort_order,id", (row, rowNum) -> new TaskModels.ChecklistItem(
                        row.getString("public_id"), row.getString("title"), row.getBoolean("completed"),
                        row.getInt("sort_order"), row.getLong("revision")), publicId, currentUser.id());
        int completedSubtasks = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task child JOIN task parent ON parent.id=child.parent_id " +
                "WHERE parent.public_id=? AND child.user_id=? AND child.deleted=FALSE AND child.status='COMPLETED'", Integer.class, publicId, currentUser.id());
        int totalSubtasks = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task child JOIN task parent ON parent.id=child.parent_id " +
                "WHERE parent.public_id=? AND child.user_id=? AND child.deleted=FALSE", Integer.class, publicId, currentUser.id());
        return new TaskItem(publicId, result.getString("list_public_id"),
                result.getString("title"), result.getString("description"), result.getString("status"),
                result.getString("priority"), instant(result.getTimestamp("start_at")),
                instant(result.getTimestamp("due_at")), result.getBoolean("all_day"), result.getString("timezone"),
                (Integer) result.getObject("duration_minutes"), result.getString("source"),
                instant(result.getTimestamp("completed_at")), result.getLong("revision"),
                result.getString("parent_public_id"), tags, checklist, completedSubtasks, totalSubtasks,
                result.getBoolean("deleted"), instant(result.getTimestamp("deleted_at")));
    }

    private ValidatedTask validate(TaskCommand command, TaskItem before) {
        if (command == null) throw new IllegalArgumentException("任务内容不能为空");
        String title = command.title() == null && before != null ? before.title()
                : requiredText(command.title(), "title", 500);
        String description = command.description() == null && before != null ? before.description()
                : optionalText(command.description(), 20000);
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

    private record ParentRef(long id, Long parentId, Long grandparentId) {
    }
}
