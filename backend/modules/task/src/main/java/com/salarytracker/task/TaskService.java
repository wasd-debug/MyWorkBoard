package com.salarytracker.task;

import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.task.TaskModels.DeletedResource;
import com.salarytracker.task.TaskModels.Settings;
import com.salarytracker.task.TaskModels.SidebarPreferences;
import com.salarytracker.task.TaskModels.SidebarPreferencesCommand;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import com.salarytracker.task.TaskModels.TaskList;
import com.salarytracker.task.TaskModels.TaskPage;
import com.salarytracker.task.TaskModels.TaskListCommand;
import com.salarytracker.task.TaskModels.TaskTag;
import com.salarytracker.task.TaskModels.TaskTagCommand;
import com.salarytracker.task.TaskModels.CalendarItem;
import com.salarytracker.task.TaskModels.RescheduleCommand;
import com.salarytracker.task.TaskModels.ListGroup;
import com.salarytracker.task.TaskModels.ListGroupCommand;
import com.salarytracker.task.TaskModels.Activity;
import com.salarytracker.task.TaskModels.ActivityPage;
import com.salarytracker.task.TaskModels.ListActivity;
import com.salarytracker.task.TaskModels.ListActivityPage;
import com.salarytracker.task.TaskModels.BatchCommand;
import com.salarytracker.task.TaskModels.BatchResult;
import com.salarytracker.task.TaskModels.BatchTarget;
import com.salarytracker.task.TaskModels.SortCommand;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TaskService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String TASK_COLUMNS = "t.public_id,t.user_id, l.public_id list_public_id, t.title, t.description, " +
            "t.status, t.priority, t.start_at, t.due_at, t.all_day, t.timezone, t.duration_minutes, " +
            "t.source, t.completed_at, t.abandoned_at, t.revision, t.deleted, t.deleted_at, " +
            "t.sort_order,t.focus_minutes,t.created_at, " +
            "(SELECT COUNT(*) FROM task_attachment a WHERE a.task_id=t.id AND a.deleted=FALSE) attachment_count, " +
            "(SELECT p.public_id FROM task p WHERE p.id = t.parent_id) parent_public_id, " +
            "t.rrule,t.recurrence_anchor,t.series_id,t.series_sequence,t.planned_due_at";
    private final JdbcTemplate jdbcTemplate;
    private final CurrentUserResolver currentUser;
    private final TaskChangeLog changeLog;
    private final TaskRecurrenceCalendar recurrenceCalendar;

    public TaskService(JdbcTemplate jdbcTemplate, CurrentUserResolver currentUser, TaskChangeLog changeLog) {
        this(jdbcTemplate, currentUser, changeLog, null);
    }

    @Autowired
    public TaskService(JdbcTemplate jdbcTemplate, CurrentUserResolver currentUser, TaskChangeLog changeLog,
                       TaskRecurrenceCalendar recurrenceCalendar) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUser = currentUser;
        this.changeLog = changeLog;
        this.recurrenceCalendar = recurrenceCalendar;
    }

    @Transactional
    public List<TaskList> listLists() {
        long userId = currentUser.id();
        ensureDefaults(userId);
        return jdbcTemplate.query("SELECT l.public_id,(SELECT g.public_id FROM task_list_group g WHERE g.id=l.group_id) group_public_id," +
                        "l.name,l.color,l.icon,l.system_key,l.sort_order,l.archived,l.is_pinned,l.view_type,l.object_type,l.show_in_smart_lists,l.revision FROM task_list l " +
                        "WHERE l.user_id = ? AND l.deleted = FALSE ORDER BY l.sort_order, l.id",
                (result, rowNum) -> new TaskList(result.getString("public_id"), result.getString("group_public_id"), result.getString("name"),
                        result.getString("color"), result.getString("icon"), result.getString("system_key"),
                        result.getLong("sort_order"), result.getBoolean("archived"), result.getBoolean("is_pinned"),
                        result.getString("view_type"), result.getString("object_type"), result.getBoolean("show_in_smart_lists"), result.getLong("revision")),
                userId);
    }

    public List<TaskList> listDeletedLists() {
        return jdbcTemplate.query("SELECT l.public_id,(SELECT g.public_id FROM task_list_group g WHERE g.id=l.group_id) group_public_id," +
                        "l.name,l.color,l.icon,l.system_key,l.sort_order,l.archived,l.is_pinned,l.view_type,l.object_type,l.show_in_smart_lists,l.revision FROM task_list l " +
                        "WHERE l.user_id = ? AND l.deleted = TRUE ORDER BY l.deleted_at DESC, l.id DESC",
                (result, rowNum) -> taskList(result), currentUser.id());
    }

    public List<ListGroup> listGroups() {
        return jdbcTemplate.query("SELECT public_id,name,color,icon,sort_order,is_pinned,revision FROM task_list_group WHERE user_id=? ORDER BY is_pinned DESC,sort_order,id",
                (result, rowNum) -> listGroup(result), currentUser.id());
    }

    @Transactional
    public ListGroup createListGroup(ListGroupCommand command) {
        long userId = currentUser.id();
        String name = requiredText(command == null ? null : command.name(), "name", 100);
        ensureUniqueListGroupName(userId, name, null);
        String publicId = UUID.randomUUID().toString();
        int order = command.sortOrder() == null ? nextOrder("task_list_group", userId) : command.sortOrder();
        jdbcTemplate.update("INSERT INTO task_list_group(public_id,user_id,name,color,icon,sort_order,is_pinned) VALUES(?,?,?,?,?,?,?)",
                publicId, userId, name, optionalText(command.color(), 16), optionalText(command.icon(), 24), order,
                Boolean.TRUE.equals(command.pinned()));
        return listGroupForUser(userId, publicId);
    }

    @Transactional
    public ListGroup updateListGroup(String publicId, ListGroupCommand command, String ifMatch) {
        long userId = currentUser.id();
        ListGroup before = listGroupForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("清单分组版本已变化", before.revision());
        String name = command == null || command.name() == null ? before.name() : requiredText(command.name(), "name", 100);
        ensureUniqueListGroupName(userId, name, publicId);
        int changed = jdbcTemplate.update("UPDATE task_list_group SET name=?,color=?,icon=?,sort_order=?,is_pinned=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND revision=?", name,
                command == null || command.color() == null ? before.color() : optionalText(command.color(), 16),
                command == null || command.icon() == null ? before.icon() : optionalText(command.icon(), 24),
                command == null || command.sortOrder() == null ? before.sortOrder() : command.sortOrder(),
                command == null || command.pinned() == null ? before.pinned() : command.pinned(),
                validUuid(publicId, "publicId"), userId, expected);
        if (changed == 0) throw new ConflictException("清单分组版本已变化", before.revision());
        return listGroupForUser(userId, publicId);
    }

    @Transactional
    public void deleteListGroup(String publicId, String ifMatch) {
        long userId = currentUser.id();
        ListGroup before = listGroupForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("清单分组版本已变化", before.revision());
        Long id = jdbcTemplate.queryForObject("SELECT id FROM task_list_group WHERE public_id=? AND user_id=?", Long.class,
                validUuid(publicId, "publicId"), userId);
        jdbcTemplate.update("UPDATE task_list SET group_id=NULL,revision=revision+1 WHERE user_id=? AND group_id=?", userId, id);
        int deleted = jdbcTemplate.update("DELETE FROM task_list_group WHERE id=? AND user_id=? AND revision=?", id, userId, expected);
        if (deleted == 0) throw new ConflictException("清单分组版本已变化", before.revision());
    }

    @Transactional
    public TaskList assignListGroup(String listPublicId, String groupPublicId, String ifMatch) {
        return assignListGroup(listPublicId, groupPublicId, null, ifMatch);
    }

    @Transactional
    public TaskList assignListGroup(String listPublicId, String groupPublicId, Long sortOrder, String ifMatch) {
        long userId = currentUser.id();
        TaskList before = listForUser(userId, listPublicId, false);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("清单版本已变化", before.revision());
        if (before.systemKey() != null && groupPublicId != null && !groupPublicId.isBlank()) {
            throw new ConflictException("系统收集箱不能加入分组", before.revision());
        }
        Long groupId = null;
        if (groupPublicId != null && !groupPublicId.isBlank()) {
            ListGroup group = listGroupForUser(userId, groupPublicId);
            groupId = jdbcTemplate.queryForObject("SELECT id FROM task_list_group WHERE public_id=? AND user_id=?", Long.class,
                    group.publicId(), userId);
        }
        int changed = jdbcTemplate.update("UPDATE task_list SET group_id=?,sort_order=?,revision=revision+1 WHERE public_id=? AND user_id=? AND revision=?",
                groupId, sortOrder == null ? before.sortOrder() : sortOrder,
                validUuid(listPublicId, "publicId"), userId, expected);
        if (changed == 0) throw new ConflictException("清单版本已变化", before.revision());
        TaskList assigned = listForUser(userId, listPublicId, false);
        changeLog.append(userId, UUID.randomUUID().toString(), "task-list", listPublicId, "UPSERT", syncEntity(assigned, false));
        return assigned;
    }

    @Transactional
    public TaskPage listTasks(String listId, String status, int page, int size) {
        return listTasks(listId, status, null, null, page, size);
    }

    @Transactional
    public TaskPage listTasks(String listId, String status, String view, String tagId, int page, int size) {
        return listTasks(listId, status, view, tagId, null, null, null, page, size);
    }

    @Transactional
    public TaskPage listTasks(String listId, String status, String view, String tagId, String priority,
                              Instant dueFrom, Instant dueTo, int page, int size) {
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
            case "TOMORROW" -> {
                ZoneId zone = userZone(userId);
                java.time.LocalDate tomorrowDate = java.time.LocalDate.now(zone).plusDays(1);
                Instant tomorrow = tomorrowDate.atStartOfDay(zone).toInstant();
                where.append(" AND l.archived=FALSE AND t.status='OPEN' AND t.due_at >= ? AND t.due_at < ?");
                args.add(Timestamp.from(tomorrow));
                args.add(Timestamp.from(tomorrowDate.plusDays(1).atStartOfDay(zone).toInstant()));
            }
            case "NEXT7" -> where.append(" AND l.archived=FALSE AND t.status='OPEN' AND t.due_at >= CURRENT_DATE AND t.due_at < DATE_ADD(CURRENT_DATE, INTERVAL 8 DAY)");
            case "INBOX" -> where.append(" AND l.system_key='INBOX'");
            case "ALL", "TRASH", "" -> { }
            case "COMPLETED" -> where.append(" AND t.status='COMPLETED'");
            case "ABANDONED" -> where.append(" AND t.status='ABANDONED'");
            default -> throw new IllegalArgumentException("view 无效");
        }
        if (tagId != null && !tagId.isBlank()) {
            where.append(" AND EXISTS (SELECT 1 FROM task_tag_link ttl JOIN task_tag tag ON tag.id=ttl.tag_id " +
                    "WHERE ttl.task_id=t.id AND tag.public_id=? AND tag.user_id=? AND tag.deleted=FALSE)");
            args.add(validUuid(tagId, "tagId"));
            args.add(userId);
        }
        if (priority != null && !priority.isBlank()) {
            where.append(" AND t.priority = ?");
            args.add(enumValue(priority, null, List.of("NONE", "LOW", "MEDIUM", "HIGH"), "priority"));
        }
        if (dueFrom != null || dueTo != null) {
            if (dueFrom == null || dueTo == null || !dueTo.isAfter(dueFrom)) {
                throw new IllegalArgumentException("到期日期范围无效");
            }
            if (dueTo.isAfter(dueFrom.plusSeconds(370L * 24 * 60 * 60))) {
                throw new IllegalArgumentException("到期日期范围不能超过 370 天");
            }
            where.append(" AND t.due_at >= ? AND t.due_at < ?");
            args.add(Timestamp.from(dueFrom));
            args.add(Timestamp.from(dueTo));
        }
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*)" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(safeSize);
        pageArgs.add(safePage * safeSize);
        List<TaskItem> items = jdbcTemplate.query("SELECT " + TASK_COLUMNS + where +
                        " ORDER BY t.status = 'COMPLETED', t.sort_order, t.created_at, t.id LIMIT ? OFFSET ?",
                (result, rowNum) -> task(result), pageArgs.toArray());
        return new TaskPage(items, safePage, safeSize, total);
    }

    public List<CalendarItem> calendarItems(Instant from, Instant to) {
        if (from == null || to == null || !to.isAfter(from)) throw new IllegalArgumentException("日历时间范围无效");
        if (to.isAfter(from.plusSeconds(370L * 24 * 60 * 60))) throw new IllegalArgumentException("日历查询范围不能超过 370 天");
        return jdbcTemplate.query("SELECT public_id,title,status,priority,start_at,due_at,all_day,timezone,duration_minutes,revision " +
                        "FROM task WHERE user_id=? AND deleted=FALSE AND (" +
                        "(start_at IS NOT NULL AND start_at<? AND COALESCE(due_at,DATE_ADD(start_at,INTERVAL COALESCE(duration_minutes,0) MINUTE))>=?) " +
                        "OR (start_at IS NULL AND due_at>=? AND due_at<?)) ORDER BY COALESCE(start_at,due_at),id",
                (result, rowNum) -> new CalendarItem(result.getString("public_id"), result.getString("title"),
                        result.getString("status"), result.getString("priority"), instant(result.getTimestamp("start_at")),
                        instant(result.getTimestamp("due_at")), result.getBoolean("all_day"), result.getString("timezone"),
                        (Integer) result.getObject("duration_minutes"), result.getLong("revision")),
                currentUser.id(), Timestamp.from(to), Timestamp.from(from), Timestamp.from(from), Timestamp.from(to));
    }

    @Transactional
    public TaskItem reschedule(String publicId, RescheduleCommand command, String ifMatch) {
        if (command == null) throw new IllegalArgumentException("改期内容不能为空");
        TaskItem current = get(publicId);
        TaskCommand update = new TaskCommand(current.listId(), current.title(), current.description(), current.priority(),
                command.startAt(), command.dueAt(), command.allDay() == null ? current.allDay() : command.allDay(),
                current.timezone(), command.durationMinutes(), current.parentId(), current.tagIds(),
                current.checklist().stream().map(item -> new TaskModels.ChecklistCommand(item.publicId(), item.title(),
                        item.completed(), item.sortOrder())).toList(), current.rrule(), current.recurrenceAnchor(),
                current.sortOrder());
        return update(publicId, update, ifMatch);
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
        long sortOrder = command.sortOrder() == null ? nextListOrder(userId) : command.sortOrder();
        String viewType = enumValue(command.viewType(), "LIST", List.of("LIST", "KANBAN", "TIMELINE"), "viewType");
        String objectType = enumValue(command.objectType(), "TASK", List.of("TASK", "NOTE"), "objectType");
        jdbcTemplate.update("INSERT INTO task_list(public_id,user_id,name,color,icon,sort_order,archived,is_pinned,view_type,object_type,show_in_smart_lists) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                publicId, userId, name, optionalText(command.color(), 24), optionalText(command.icon(), 40), sortOrder,
                Boolean.TRUE.equals(command.archived()), Boolean.TRUE.equals(command.pinned()), viewType, objectType,
                !Boolean.FALSE.equals(command.showInSmartLists()));
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
            throw new ConflictException("收集箱不可归档", before.revision());
        }
        if ("INBOX".equals(before.systemKey()) && command.name() != null && !before.name().equals(command.name().trim())) {
            throw new ConflictException("收集箱不可重命名", before.revision());
        }
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("清单版本已变化", before.revision());
        int updated = jdbcTemplate.update("UPDATE task_list SET name=?,color=?,icon=?,sort_order=?,archived=?,is_pinned=?,view_type=?,object_type=?,show_in_smart_lists=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND deleted=FALSE AND revision=?",
                command.name() == null ? before.name() : requiredText(command.name(), "name", 120),
                command.color() == null ? before.color() : optionalText(command.color(), 24),
                command.icon() == null ? before.icon() : optionalText(command.icon(), 40),
                command.sortOrder() == null ? before.sortOrder() : command.sortOrder(),
                command.archived() == null ? before.archived() : command.archived(),
                command.pinned() == null ? before.pinned() : command.pinned(),
                enumValue(command.viewType(), before.viewType(), List.of("LIST", "KANBAN", "TIMELINE"), "viewType"),
                enumValue(command.objectType(), before.objectType(), List.of("TASK", "NOTE"), "objectType"),
                command.showInSmartLists() == null ? before.showInSmartLists() : command.showInSmartLists(),
                validUuid(publicId, "publicId"),
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
        if ("INBOX".equals(list.systemKey())) throw new ConflictException("收集箱不可删除", list.revision());
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
        return jdbcTemplate.query("SELECT t.public_id,p.public_id parent_public_id,t.name,t.color,t.icon,t.sort_order,t.revision " +
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
        jdbcTemplate.update("INSERT INTO task_tag(public_id,user_id,parent_id,name,normalized_name,color,sort_order,icon) VALUES(?,?,?,?,?,?,?,?)",
                publicId, userId, parentId, name, name.toLowerCase(Locale.ROOT), optionalText(command.color(), 24),
                command.sortOrder() == null ? nextOrder("task_tag", userId) : command.sortOrder(), optionalText(command.icon(), 40));
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
        int updated = jdbcTemplate.update("UPDATE task_tag SET parent_id=?,name=?,normalized_name=?,color=?,sort_order=?,icon=?,revision=revision+1 " +
                        "WHERE public_id=? AND user_id=? AND deleted=FALSE AND revision=?", parentId, name,
                name.toLowerCase(Locale.ROOT), command.color() == null ? before.color() : optionalText(command.color(), 24),
                command.sortOrder() == null ? before.sortOrder() : command.sortOrder(),
                command.icon() == null ? before.icon() : optionalText(command.icon(), 40), publicId, userId, expected);
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
    public TaskModels.TaskTagMergeResult mergeTag(String sourcePublicId, String targetPublicId,
                                                   String sourceIfMatch, long targetRevision) {
        long userId = currentUser.id();
        if (sourcePublicId != null && sourcePublicId.equals(targetPublicId))
            throw new IllegalArgumentException("不能将标签合并到自身");
        List<Long> lockedTagIds = jdbcTemplate.query("SELECT id FROM task_tag WHERE public_id IN (?,?) AND user_id=? " +
                        "AND deleted=FALSE ORDER BY id FOR UPDATE", (result, rowNum) -> result.getLong(1),
                validUuid(sourcePublicId, "sourceTagId"), validUuid(targetPublicId, "targetTagId"), userId);
        if (lockedTagIds.size() != 2) throw new NotFoundException("源标签或目标标签不存在");
        TaskTag source = tagForUser(userId, sourcePublicId);
        TaskTag target = tagForUser(userId, targetPublicId);
        long expectedSourceRevision = requiredRevision(sourceIfMatch);
        if (source.revision() != expectedSourceRevision) throw new ConflictException("源标签版本已变化", source.revision());
        if (target.revision() != targetRevision) throw new ConflictException("目标标签版本已变化", target.revision());

        long sourceId = jdbcTemplate.queryForObject("SELECT id FROM task_tag WHERE public_id=? AND user_id=?", Long.class,
                source.publicId(), userId);
        long targetId = jdbcTemplate.queryForObject("SELECT id FROM task_tag WHERE public_id=? AND user_id=?", Long.class,
                target.publicId(), userId);
        List<String> childIds = jdbcTemplate.query("SELECT public_id FROM task_tag WHERE parent_id=? AND deleted=FALSE ORDER BY id",
                (result, rowNum) -> result.getString(1), sourceId);
        if (!childIds.isEmpty() && target.parentId() != null)
            throw new IllegalArgumentException("含有子标签的标签只能合并到一级标签");

        List<String> affectedTasks = jdbcTemplate.query("SELECT DISTINCT t.public_id FROM task t " +
                        "JOIN task_tag_link link ON link.task_id=t.id WHERE link.tag_id=? AND t.user_id=? ORDER BY t.public_id",
                (result, rowNum) -> result.getString(1), sourceId, userId);
        for (String taskPublicId : affectedTasks) {
            jdbcTemplate.update("INSERT IGNORE INTO task_tag_link(task_id,tag_id) " +
                            "SELECT task.id,? FROM task WHERE task.public_id=? AND task.user_id=?", targetId, taskPublicId, userId);
            jdbcTemplate.update("DELETE link FROM task_tag_link link JOIN task ON task.id=link.task_id " +
                            "WHERE task.public_id=? AND task.user_id=? AND link.tag_id=?", taskPublicId, userId, sourceId);
            jdbcTemplate.update("UPDATE task SET revision=revision+1 WHERE public_id=? AND user_id=?", taskPublicId, userId);
            boolean deleted = jdbcTemplate.queryForObject("SELECT deleted FROM task WHERE public_id=? AND user_id=?",
                    Boolean.class, taskPublicId, userId);
            changeLog.append(userId, UUID.randomUUID().toString(), taskPublicId, deleted ? "DELETE" : "UPSERT",
                    syncEntity(taskForUserIncludingDeleted(userId, taskPublicId), deleted));
        }
        for (String childId : childIds) {
            int changed = jdbcTemplate.update("UPDATE task_tag SET parent_id=?,revision=revision+1 " +
                    "WHERE public_id=? AND user_id=? AND deleted=FALSE", targetId, childId, userId);
            if (changed != 1) throw new ConflictException("子标签已被其他操作修改", 0);
            changeLog.append(userId, UUID.randomUUID().toString(), "task-tag", childId,
                    "UPSERT", syncEntity(tagForUser(userId, childId), false));
        }
        int deleted = jdbcTemplate.update("UPDATE task_tag SET deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,revision=revision+1 " +
                "WHERE public_id=? AND user_id=? AND deleted=FALSE AND revision=?",
                source.publicId(), userId, expectedSourceRevision);
        if (deleted != 1) throw new ConflictException("源标签版本已变化", tagForUser(userId, source.publicId()).revision());
        changeLog.append(userId, UUID.randomUUID().toString(), "task-tag", source.publicId(),
                "DELETE", syncEntity(source, true));
        return new TaskModels.TaskTagMergeResult(source.publicId(), target.publicId(), affectedTasks.size(),
                childIds.size(), expectedSourceRevision + 1, target.revision());
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
        appendActivity(userId, publicId, "RESTORED", "恢复了任务", null);
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

    public ActivityPage activity(String publicId, int page, int size) {
        long userId = currentUser.id();
        taskForUserIncludingDeleted(userId, publicId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_activity a JOIN task t ON t.id=a.task_id " +
                "WHERE t.public_id=? AND a.user_id=? AND a.created_at>=TIMESTAMPADD(MONTH,-12,CURRENT_TIMESTAMP(3))",
                Long.class, validUuid(publicId, "publicId"), userId);
        List<Activity> items = jdbcTemplate.query("SELECT a.public_id,a.event_type,a.summary,a.detail_json,a.created_at " +
                        "FROM task_activity a JOIN task t ON t.id=a.task_id WHERE t.public_id=? AND a.user_id=? " +
                        "AND a.created_at>=TIMESTAMPADD(MONTH,-12,CURRENT_TIMESTAMP(3)) " +
                        "ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
                (result, rowNum) -> new Activity(result.getString("public_id"), result.getString("event_type"),
                        result.getString("summary"), result.getString("detail_json"),
                        result.getTimestamp("created_at").toInstant().toString()),
                validUuid(publicId, "publicId"), userId, safeSize, safePage * safeSize);
        return new ActivityPage(items, safePage, safeSize, total);
    }

    public ListActivityPage listActivity(String listPublicId, int page, int size) {
        long userId = currentUser.id();
        String safeListPublicId = validUuid(listPublicId, "publicId");
        listForUser(userId, safeListPublicId, false);
        Long listId = jdbcTemplate.queryForObject("SELECT id FROM task_list WHERE public_id=? AND user_id=? AND deleted=FALSE",
                Long.class, safeListPublicId, userId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String listMatch = "(t.list_id=? OR JSON_UNQUOTE(JSON_EXTRACT(a.detail_json,'$.changes.listId.before'))=? " +
                "OR JSON_UNQUOTE(JSON_EXTRACT(a.detail_json,'$.changes.listId.after'))=?)";
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_activity a JOIN task t ON t.id=a.task_id " +
                        "WHERE a.user_id=? AND a.created_at>=TIMESTAMPADD(MONTH,-12,CURRENT_TIMESTAMP(3)) AND " + listMatch,
                Long.class, userId, listId, safeListPublicId, safeListPublicId);
        List<ListActivity> items = jdbcTemplate.query("SELECT a.public_id,a.event_type,a.summary,a.detail_json,a.created_at, " +
                        "t.public_id task_public_id,t.title task_title,t.priority, " +
                        "(SELECT JSON_ARRAYAGG(tag.public_id) FROM task_tag_link link JOIN task_tag tag ON tag.id=link.tag_id " +
                        "WHERE link.task_id=t.id AND tag.deleted=FALSE) tag_ids, " +
                        "COALESCE(NULLIF(u.nickname,''),u.username) actor_name " +
                        "FROM task_activity a JOIN task t ON t.id=a.task_id JOIN app_user u ON u.id=a.user_id " +
                        "WHERE a.user_id=? AND a.created_at>=TIMESTAMPADD(MONTH,-12,CURRENT_TIMESTAMP(3)) AND " + listMatch +
                        " ORDER BY a.created_at DESC,a.id DESC LIMIT ? OFFSET ?",
                (result, rowNum) -> {
                    String summary = result.getString("summary");
                    String detailJson = result.getString("detail_json");
                    String movement = listActivityMovement(detailJson, safeListPublicId);
                    if ("OUT".equals(movement)) summary = "移出了任务";
                    else if ("IN".equals(movement)) summary = "移入了任务";
                    List<String> tagIds = List.of();
                    String rawTagIds = result.getString("tag_ids");
                    if (rawTagIds != null) {
                        try { tagIds = JSON.readValue(rawTagIds, new TypeReference<>() { }); }
                        catch (Exception ignored) { tagIds = List.of(); }
                    }
                    return new ListActivity(result.getString("public_id"), result.getString("event_type"), summary,
                            result.getString("task_public_id"), result.getString("task_title"), result.getString("priority"),
                            tagIds, result.getString("actor_name"), result.getTimestamp("created_at").toInstant().toString());
                }, userId, listId, safeListPublicId, safeListPublicId, safeSize, safePage * safeSize);
        return new ListActivityPage(items, safePage, safeSize, total);
    }

    private String listActivityMovement(String detailJson, String listPublicId) {
        if (detailJson == null || detailJson.isBlank()) return "";
        try {
            var listChange = JSON.readTree(detailJson).path("changes").path("listId");
            String before = listChange.path("before").asText("");
            String after = listChange.path("after").asText("");
            if (listPublicId.equals(before) && !listPublicId.equals(after)) return "OUT";
            if (listPublicId.equals(after) && !listPublicId.equals(before)) return "IN";
        } catch (Exception ignored) {
            return "";
        }
        return "";
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
        String normalizedStatus = enumValue(status, "OPEN", List.of("OPEN", "COMPLETED", "ABANDONED"), "status");
        String rrule = TaskRecurrence.validate(command == null ? null : command.rrule());
        String anchor = rrule == null ? null : enumValue(command.recurrenceAnchor(), "DUE_DATE",
                List.of("DUE_DATE", "COMPLETION_DATE"), "recurrenceAnchor");
        if (rrule != null && values.dueAt() == null) throw new IllegalArgumentException("重复任务必须设置到期时间");
        String seriesId = rrule == null ? null : UUID.randomUUID().toString();
        try {
            int sortOrder = command.sortOrder() == null ? jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(sort_order),-1)+1 FROM task WHERE user_id=? AND list_id=? AND deleted=FALSE", Integer.class, userId, listDatabaseId)
                    : Math.max(0, command.sortOrder());
            jdbcTemplate.update("INSERT INTO task (public_id, user_id, list_id, parent_id, title, description, status, priority, " +
                            "start_at, due_at, all_day, timezone, duration_minutes,sort_order,series_id,series_sequence,planned_due_at,rrule,recurrence_anchor,source, completed_at, abandoned_at, created_by, client_op_key) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'WEB', ?, ?, ?, ?)",
                    publicId, userId, listDatabaseId, parentDatabaseId, values.title(), values.description(), normalizedStatus, values.priority(),
                    timestamp(values.startAt()), timestamp(values.dueAt()), values.allDay(), values.timezone(),
                    values.durationMinutes(), sortOrder, seriesId, seriesId == null ? null : 1, timestamp(values.dueAt()), rrule, anchor,
                    "COMPLETED".equals(normalizedStatus) ? Timestamp.from(Instant.now()) : null,
                    "ABANDONED".equals(normalizedStatus) ? Timestamp.from(Instant.now()) : null,
                    userId, opKey);
        } catch (DuplicateKeyException exception) {
            List<String> concurrent = jdbcTemplate.query(
                    "SELECT public_id FROM task WHERE user_id = ? AND client_op_key = ?",
                    (result, rowNum) -> result.getString(1), userId, opKey);
            if (!concurrent.isEmpty()) return taskForUser(userId, concurrent.get(0));
            throw exception;
        }
        replaceTagsAndChecklist(userId, publicId, command.tagIds(), command.checklist());
        if (seriesId != null) jdbcTemplate.update("INSERT IGNORE INTO task_recurrence_instance(series_id,planned_due_at,task_id) " +
                "SELECT series_id,planned_due_at,id FROM task WHERE public_id=?", publicId);
        TaskItem created = taskForUser(userId, publicId);
        changeLog.append(userId, opKey, publicId, "UPSERT", syncEntity(created, false));
        appendActivity(userId, publicId, "CREATED", "创建了任务", null);
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
                : enumValue(status, before.status(), List.of("OPEN", "COMPLETED", "ABANDONED"), "status");
        if ("COMPLETED".equals(before.status()) && "OPEN".equals(normalizedStatus)) {
            ensureRecurringTaskCanReopen(userId, before);
        }
        String rrule = command.rrule() == null ? before.rrule() : TaskRecurrence.validate(command.rrule());
        String anchor = rrule == null ? null : enumValue(command.recurrenceAnchor(),
                before.recurrenceAnchor() == null ? "DUE_DATE" : before.recurrenceAnchor(),
                List.of("DUE_DATE", "COMPLETION_DATE"), "recurrenceAnchor");
        if (rrule != null && values.dueAt() == null) throw new IllegalArgumentException("重复任务必须设置到期时间");
        String seriesId = rrule == null ? null : before.seriesId() == null ? UUID.randomUUID().toString() : before.seriesId();
        Integer sequence = rrule == null ? null : before.seriesSequence() == null ? 1 : before.seriesSequence();
        int updated = jdbcTemplate.update("UPDATE task SET list_id = ?, parent_id = ?, title = ?, description = ?, status = ?, priority = ?, " +
                        "start_at = ?, due_at = ?, all_day = ?, timezone = ?, duration_minutes = ?,sort_order=?,rrule=?,recurrence_anchor=?,series_id=?,series_sequence=?,planned_due_at=?, completed_at = ?, abandoned_at = ?, revision = revision + 1 " +
                        "WHERE public_id = ? AND user_id = ? AND deleted = FALSE AND revision = ?",
                listDatabaseId, parentDatabaseId, values.title(), values.description(), normalizedStatus, values.priority(), timestamp(values.startAt()),
                timestamp(values.dueAt()), values.allDay(), values.timezone(), values.durationMinutes(),
                command.sortOrder() == null ? before.sortOrder() : Math.max(0, command.sortOrder()), rrule, anchor, seriesId, sequence,
                rrule == null ? null : timestamp(values.dueAt()),
                "COMPLETED".equals(normalizedStatus) ? timestamp(before.completedAt() == null
                        ? Instant.now() : Instant.parse(before.completedAt())) : null,
                "ABANDONED".equals(normalizedStatus) ? timestamp(before.abandonedAt() == null
                        ? Instant.now() : Instant.parse(before.abandonedAt())) : null,
                validUuid(publicId, "publicId"), userId, expected);
        ensureUpdated(updated, userId, publicId);
        replaceTagsAndChecklist(userId, publicId, command.tagIds(), command.checklist());
        if (seriesId != null) jdbcTemplate.update("INSERT IGNORE INTO task_recurrence_instance(series_id,planned_due_at,task_id) " +
                "SELECT series_id,planned_due_at,id FROM task WHERE public_id=?", publicId);
        if (!"COMPLETED".equals(before.status()) && "COMPLETED".equals(normalizedStatus)) {
            generateNextInstance(userId, before, Instant.now());
            jdbcTemplate.update("UPDATE task_reminder r JOIN task t ON t.id=r.task_id SET r.cancelled_at=CURRENT_TIMESTAMP(3),r.revision=r.revision+1 " +
                    "WHERE t.public_id=? AND t.user_id=? AND r.cancelled_at IS NULL AND r.sent_at IS NULL", publicId, userId);
        }
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
        if (!before.status().equals(changed.status())) {
            String event = switch (changed.status()) {
                case "COMPLETED" -> "COMPLETED";
                case "ABANDONED" -> "ABANDONED";
                default -> "REOPENED";
            };
            String summary = switch (changed.status()) {
                case "COMPLETED" -> "完成了任务";
                case "ABANDONED" -> "放弃了任务";
                default -> "重新打开了任务";
            };
            appendActivity(userId, publicId, event, summary, null);
        } else {
            appendActivity(userId, publicId, "UPDATED", updateSummary(before, changed), updateDetail(before, changed));
        }
        return changed;
    }

    @Transactional
    public TaskItem complete(String publicId, String ifMatch) {
        return changeStatus(publicId, ifMatch, "COMPLETED", UUID.randomUUID().toString());
    }

    @Transactional
    public TaskItem reopen(String publicId, String ifMatch) {
        TaskItem current = taskForUser(currentUser.id(), publicId);
        if ("COMPLETED".equals(current.status())) ensureRecurringTaskCanReopen(currentUser.id(), current);
        return changeStatus(publicId, ifMatch, "OPEN", UUID.randomUUID().toString());
    }

    @Transactional
    public TaskItem abandon(String publicId, String ifMatch) {
        return changeStatus(publicId, ifMatch, "ABANDONED", UUID.randomUUID().toString());
    }

    @Transactional
    public TaskItem restoreAbandoned(String publicId, String ifMatch) {
        TaskItem current = taskForUser(currentUser.id(), publicId);
        if (!"ABANDONED".equals(current.status())) {
            throw new ConflictException("任务当前并非已放弃状态", current.revision());
        }
        return changeStatus(publicId, ifMatch, "OPEN", UUID.randomUUID().toString());
    }

    @Transactional
    public BatchResult batch(BatchCommand command) {
        if (command == null || command.targets() == null || command.targets().isEmpty()) throw new IllegalArgumentException("批量操作至少选择一项");
        if (command.targets().size() > 200) throw new IllegalArgumentException("批量操作最多支持 200 项");
        String action = enumValue(command.action(), null, List.of("COMPLETE", "DELETE", "MOVE", "PRIORITY", "RESCHEDULE", "TAGS", "REMINDER"), "action");
        long userId = currentUser.id();
        for (BatchTarget target : command.targets()) {
            TaskItem current = taskForUser(userId, target.publicId());
            if (current.revision() != target.revision()) throw new ConflictException("任务版本已变化", current.revision());
        }
        List<TaskItem> changed = new ArrayList<>();
        for (BatchTarget target : command.targets()) {
            TaskItem before = taskForUser(userId, target.publicId());
            if (before.revision() != target.revision()) throw new ConflictException("任务版本已变化", before.revision());
            if ("COMPLETE".equals(action)) changeStatus(target.publicId(), String.valueOf(target.revision()), "COMPLETED", UUID.randomUUID().toString());
            else if ("DELETE".equals(action)) delete(target.publicId(), String.valueOf(target.revision()), UUID.randomUUID().toString());
            else if ("REMINDER".equals(action)) {
                if (command.reminder() == null) throw new IllegalArgumentException("提醒内容不能为空");
                String kind = enumValue(command.reminder().kind(), null, List.of("RELATIVE", "ABSOLUTE"), "reminder.kind");
                Integer offset = command.reminder().offsetMinutes();
                Instant remindAt;
                if ("RELATIVE".equals(kind)) {
                    if (offset == null || offset < 0 || offset > 10080 || before.dueAt() == null) throw new IllegalArgumentException("相对提醒需要有效截止时间和 0-10080 分钟偏移");
                    remindAt = Instant.parse(before.dueAt()).minusSeconds(offset * 60L);
                } else {
                    if (command.reminder().remindAt() == null || command.reminder().remindAt().isBlank()) throw new IllegalArgumentException("绝对提醒时间不能为空");
                    remindAt = Instant.parse(command.reminder().remindAt());
                }
                jdbcTemplate.update("INSERT INTO task_reminder(public_id,user_id,task_id,kind,offset_minutes,remind_at,channel,daily_until_done) " +
                                "SELECT UUID(),user_id,id,?,?,?,?,? FROM task WHERE public_id=? AND user_id=? AND deleted=FALSE",
                        kind, offset, Timestamp.from(remindAt), "IN_APP", Boolean.TRUE.equals(command.reminder().dailyUntilDone()), target.publicId(), userId);
            } else {
                String listId = "MOVE".equals(action) ? command.listId() : before.listId();
                if ("MOVE".equals(action) && (listId == null || listId.isBlank())) throw new IllegalArgumentException("移动操作必须指定清单");
                Long listDbId = resolveListId(userId, listId);
                String priority = "PRIORITY".equals(action) ? enumValue(command.priority(), null, List.of("NONE", "LOW", "MEDIUM", "HIGH"), "priority") : before.priority();
                String dueAt = "RESCHEDULE".equals(action) ? command.dueAt() : before.dueAt();
                int updated = jdbcTemplate.update("UPDATE task SET list_id=?,priority=?,due_at=?,revision=revision+1 WHERE public_id=? AND user_id=? AND deleted=FALSE AND revision=?",
                        listDbId, priority, dueAt == null || dueAt.isBlank() ? null : Timestamp.from(Instant.parse(dueAt)), target.publicId(), userId, target.revision());
                ensureUpdated(updated, userId, target.publicId());
                if ("TAGS".equals(action)) {
                    List<String> tags = new ArrayList<>(before.tagIds());
                    if (command.tagIds() != null) command.tagIds().stream().filter(tag -> !tags.contains(tag)).forEach(tags::add);
                    replaceTagsAndChecklist(userId, target.publicId(), tags, before.checklist().stream().map(i -> new TaskModels.ChecklistCommand(i.publicId(), i.title(), i.completed(), i.sortOrder())).toList());
                }
                TaskItem item = taskForUser(userId, target.publicId());
                changeLog.append(userId, UUID.randomUUID().toString(), target.publicId(), "UPSERT", syncEntity(item, false));
            }
            changed.add(taskForUser(userId, target.publicId(), "DELETE".equals(action)));
        }
        return new BatchResult(changed.size(), changed);
    }

    @Transactional
    public TaskItem sort(String publicId, SortCommand command, String ifMatch) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        List<String> ordered = jdbcTemplate.query("SELECT public_id FROM task WHERE user_id=? AND list_id=(SELECT list_id FROM task WHERE public_id=? AND user_id=?) " +
                        "AND deleted=FALSE AND status=? AND public_id<>? ORDER BY sort_order,created_at,id",
                (row, number) -> row.getString(1), userId, publicId, userId, before.status(), publicId);
        int order = Math.min(command == null || command.sortOrder() == null ? before.sortOrder() : Math.max(0, command.sortOrder()), ordered.size());
        ordered.add(order, publicId);
        for (int index = 0; index < ordered.size(); index++) {
            String id = ordered.get(index);
            int changed = jdbcTemplate.update("UPDATE task SET sort_order=?,revision=revision+1 WHERE public_id=? AND user_id=? AND deleted=FALSE", index, id, userId);
            if (changed != 1) throw new ConflictException("任务排序失败", expected);
            TaskItem changedItem = taskForUser(userId, id);
            changeLog.append(userId, UUID.randomUUID().toString(), id, "UPSERT", syncEntity(changedItem, false));
        }
        TaskItem item = taskForUser(userId, publicId);
        return item;
    }

    private void ensureRecurringTaskCanReopen(long userId, TaskItem current) {
        if (current.rrule() != null && current.seriesId() != null && current.seriesSequence() != null) {
            Integer successors = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task WHERE user_id=? AND series_id=? " +
                            "AND series_sequence>? AND status='OPEN' AND deleted=FALSE",
                    Integer.class, userId, current.seriesId(), current.seriesSequence());
            if (successors != null && successors > 0) {
                throw new ConflictException("周期任务的下一实例已生成，无法重开当前实例", current.revision());
            }
        }
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
        jdbcTemplate.update("UPDATE task_reminder r JOIN task t ON t.id=r.task_id SET r.cancelled_at=CURRENT_TIMESTAMP(3),r.revision=r.revision+1 " +
                "WHERE t.user_id=? AND t.deleted=TRUE AND r.cancelled_at IS NULL AND r.sent_at IS NULL", userId);
        DeletedResource deleted = new DeletedResource(publicId, expected + 1, true);
        changeLog.append(userId, opId, publicId, "DELETE", syncEntity(taskForUser(userId, publicId, true), true));
        appendActivity(userId, publicId, "DELETED", "删除了任务", null);
        return deleted;
    }

    @Transactional
    public Settings settings() {
        long userId = currentUser.id();
        ensureDefaults(userId);
        return jdbcTemplate.queryForObject("SELECT l.public_id, s.*,TIME_FORMAT(s.daily_summary_time,'%H:%i') summary_time," +
                        "TIME_FORMAT(s.quiet_start,'%H:%i') quiet_start_text,TIME_FORMAT(s.quiet_end,'%H:%i') quiet_end_text " +
                        "FROM task_setting s JOIN task_list l ON l.id = s.default_list_id WHERE s.user_id = ?",
                (result, rowNum) -> new Settings(result.getString("public_id"), result.getString("default_view"),
                        (Integer) result.getObject("default_reminder_minutes"), result.getString("default_due_date"),
                        result.getString("default_due_time"), result.getInt("week_start"),
                        result.getBoolean("show_lunar"), result.getBoolean("overlay_worktime"),
                        result.getBoolean("overlay_ledger"), result.getBoolean("overlay_holiday"),
                        result.getBoolean("overlay_habit"), result.getBoolean("notification_reminders"),
                        result.getBoolean("notification_overdue"), result.getString("summary_time"),
                        result.getString("quiet_start_text"), result.getString("quiet_end_text"),
                        result.getBoolean("feed_include_description"), result.getString("timezone"),
                        result.getLong("revision")), userId);
    }

    @Transactional
    public Settings updateSettings(TaskModels.SettingsCommand command, String ifMatch) {
        if (command == null) throw new IllegalArgumentException("任务设置不能为空");
        Settings before = settings();
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务设置版本已变化", before.revision());
        long listId = resolveListId(currentUser.id(), command.defaultListId() == null ? before.defaultListId() : command.defaultListId());
        String view = enumValue(command.defaultView(), before.defaultView(), List.of("INBOX", "TODAY", "CALENDAR", "BOARD"), "defaultView");
        Integer reminder = command.defaultReminderMinutes() == null ? before.defaultReminderMinutes() : command.defaultReminderMinutes();
        if (reminder != null && (reminder < 0 || reminder > 10080)) throw new IllegalArgumentException("默认提醒必须在 0-10080 分钟");
        String defaultDueDate = command.defaultDueDate() == null ? before.defaultDueDate()
                : enumValue(command.defaultDueDate(), before.defaultDueDate(), List.of("TODAY", "NONE"), "defaultDueDate");
        String defaultDueTime = command.defaultDueTime() == null ? before.defaultDueTime()
                : requiredText(command.defaultDueTime(), "defaultDueTime", 16);
        if (!"NEXT_HOUR".equals(defaultDueTime) && !defaultDueTime.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
            throw new IllegalArgumentException("默认任务时间必须为 NEXT_HOUR 或 HH:mm");
        }
        int weekStart = command.weekStart() == null ? before.weekStart() : command.weekStart();
        if (weekStart != 0 && weekStart != 1) throw new IllegalArgumentException("一周起始日只能是周日或周一");
        String timezone = command.timezone() == null ? before.timezone() : requiredText(command.timezone(), "timezone", 64);
        try { ZoneId.of(timezone); } catch (Exception exception) { throw new IllegalArgumentException("timezone 无效"); }
        String summary = time(command.dailySummaryTime(), before.dailySummaryTime(), "每日摘要时间");
        String quietStart = time(command.quietStart(), before.quietStart(), "免打扰开始时间");
        String quietEnd = time(command.quietEnd(), before.quietEnd(), "免打扰结束时间");
        int changed = jdbcTemplate.update("UPDATE task_setting SET default_list_id=?,default_view=?,default_reminder_minutes=?,default_due_date=?,default_due_time=?,week_start=?," +
                        "show_lunar=?,overlay_worktime=?,overlay_ledger=?,overlay_holiday=?,overlay_habit=?,notification_reminders=?," +
                        "notification_overdue=?,daily_summary_time=?,quiet_start=?,quiet_end=?,feed_include_description=?,timezone=?,revision=revision+1 " +
                "WHERE user_id=? AND revision=?", listId, view, reminder, defaultDueDate, defaultDueTime, weekStart,
                value(command.showLunar(), before.showLunar()), value(command.overlayWorktime(), before.overlayWorktime()),
                value(command.overlayLedger(), before.overlayLedger()), value(command.overlayHoliday(), before.overlayHoliday()),
                value(command.overlayHabit(), before.overlayHabit()), value(command.notificationReminders(), before.notificationReminders()),
                value(command.notificationOverdue(), before.notificationOverdue()), summary, quietStart, quietEnd,
                value(command.feedIncludeDescription(), before.feedIncludeDescription()), timezone, currentUser.id(), expected);
        if (changed != 1) throw new ConflictException("任务设置版本已变化", before.revision());
        return settings();
    }

    @Transactional
    public SidebarPreferences sidebarPreferences() {
        long userId = currentUser.id();
        jdbcTemplate.update("INSERT IGNORE INTO task_sidebar_preference(user_id,hidden_system_views_json,pinned_system_views_json," +
                        "conditional_system_views_json,hidden_list_ids_json,pinned_list_ids_json,hidden_tag_ids_json,pinned_tag_ids_json,hidden_filter_ids_json," +
                        "pinned_filter_ids_json,collapsed_sections_json) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                userId, "[]", "[]", "[]", "[]", "[]", "[]", "[]", "[]", "[]", "{}");
        return jdbcTemplate.queryForObject("SELECT * FROM task_sidebar_preference WHERE user_id=?", (row, n) ->
                new SidebarPreferences(jsonList(row.getString("hidden_system_views_json")),
                        jsonList(row.getString("pinned_system_views_json")), jsonList(row.getString("conditional_system_views_json")),
                        jsonList(row.getString("hidden_list_ids_json")),
                        jsonList(row.getString("pinned_list_ids_json")), jsonList(row.getString("hidden_tag_ids_json")),
                        jsonList(row.getString("pinned_tag_ids_json")), jsonList(row.getString("hidden_filter_ids_json")),
                        jsonList(row.getString("pinned_filter_ids_json")), jsonMap(row.getString("collapsed_sections_json")),
                        row.getLong("revision")), userId);
    }

    @Transactional
    public SidebarPreferences updateSidebarPreferences(SidebarPreferencesCommand command, String ifMatch) {
        if (command == null) throw new IllegalArgumentException("侧栏偏好不能为空");
        SidebarPreferences before = sidebarPreferences();
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("侧栏偏好已被其他操作更新", before.revision());
        List<String> hiddenViews = preferenceIds(command.hiddenSystemViews(), 20);
        List<String> pinnedViews = preferenceIds(command.pinnedSystemViews(), 20);
        List<String> conditionalViews = preferenceIds(command.conditionalSystemViews(), 20);
        validateSystemViews(hiddenViews); validateSystemViews(pinnedViews);
        validateConditionalSystemViews(conditionalViews);
        List<String> hiddenLists = ownedPreferenceIds(command.hiddenListIds(), "task_list");
        List<String> pinnedLists = ownedPreferenceIds(command.pinnedListIds(), "task_list");
        List<String> hiddenTags = ownedPreferenceIds(command.hiddenTagIds(), "task_tag");
        List<String> pinnedTags = ownedPreferenceIds(command.pinnedTagIds(), "task_tag");
        List<String> hiddenFilters = ownedPreferenceIds(command.hiddenFilterIds(), "task_filter");
        List<String> pinnedFilters = ownedPreferenceIds(command.pinnedFilterIds(), "task_filter");
        Map<String, Boolean> collapsed = command.collapsedSections() == null ? Map.of() : command.collapsedSections();
        if (collapsed.size() > 20 || collapsed.keySet().stream().anyMatch(key -> !key.matches("[A-Za-z0-9_-]{1,40}")))
            throw new IllegalArgumentException("侧栏分区折叠状态无效");
        int changed = jdbcTemplate.update("UPDATE task_sidebar_preference SET hidden_system_views_json=?,pinned_system_views_json=?," +
                        "conditional_system_views_json=?,hidden_list_ids_json=?,pinned_list_ids_json=?,hidden_tag_ids_json=?,pinned_tag_ids_json=?," +
                        "hidden_filter_ids_json=?,pinned_filter_ids_json=?,collapsed_sections_json=?,revision=revision+1 " +
                        "WHERE user_id=? AND revision=?",
                json(hiddenViews), json(pinnedViews), json(conditionalViews), json(hiddenLists), json(pinnedLists), json(hiddenTags), json(pinnedTags),
                json(hiddenFilters), json(pinnedFilters), json(collapsed), currentUser.id(), expected);
        if (changed != 1) throw new ConflictException("侧栏偏好已被其他操作更新", before.revision());
        return sidebarPreferences();
    }

    private List<String> preferenceIds(List<String> values, int limit) {
        List<String> normalized = values == null ? List.of() : values.stream().filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isBlank()).distinct().toList();
        if (normalized.size() > limit) throw new IllegalArgumentException("侧栏偏好项目过多");
        return normalized;
    }

    private List<String> ownedPreferenceIds(List<String> values, String table) {
        if (!List.of("task_list", "task_tag", "task_filter").contains(table)) throw new IllegalArgumentException("侧栏资源无效");
        List<String> ids = preferenceIds(values, 500);
        for (String id : ids) {
            long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE public_id=? AND user_id=?", Long.class, id, currentUser.id());
            if (count == 0) throw new IllegalArgumentException("侧栏偏好包含不存在或不属于当前账号的对象");
        }
        return ids;
    }

    private void validateSystemViews(List<String> values) {
        if (!List.of("INBOX", "TODAY", "TOMORROW", "NEXT7", "ALL", "COMPLETED", "ABANDONED", "TRASH", "CALENDAR",
                        "all", "today", "tomorrow", "next7", "assigned", "inbox", "summary", "this-week", "next-week",
                        "no-date", "no-tag", "completed", "abandoned", "trash", "archived")
                .containsAll(values)) throw new IllegalArgumentException("系统视图无效");
    }

    private void validateConditionalSystemViews(List<String> values) {
        if (!List.of("all", "today", "tomorrow", "next7", "assigned", "inbox", "this-week", "next-week",
                        "no-date", "no-tag", "completed", "abandoned", "trash", "archived")
                .containsAll(values)) throw new IllegalArgumentException("按内容显示的系统视图无效");
    }

    private String json(Object value) {
        try { return JSON.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("侧栏偏好序列化失败", exception); }
    }

    private List<String> jsonList(String value) {
        try { return JSON.readValue(value, new TypeReference<>() { }); } catch (Exception exception) { throw new IllegalStateException("侧栏偏好数据损坏", exception); }
    }

    private Map<String, Boolean> jsonMap(String value) {
        try { return JSON.readValue(value, new TypeReference<>() { }); } catch (Exception exception) { throw new IllegalStateException("侧栏偏好数据损坏", exception); }
    }

    private boolean value(Boolean requested, boolean fallback) { return requested == null ? fallback : requested; }
    private String time(String requested, String fallback, String label) {
        String value = requested == null ? fallback : requested.trim();
        if (value == null || value.isBlank()) return null;
        try { return java.time.LocalTime.parse(value).toString(); }
        catch (Exception exception) { throw new IllegalArgumentException(label + "格式应为 HH:mm"); }
    }

    private TaskItem changeStatus(String publicId, String ifMatch, String status, String opId) {
        long userId = currentUser.id();
        TaskItem before = taskForUser(userId, publicId);
        long expected = requiredRevision(ifMatch);
        if (before.revision() != expected) throw new ConflictException("任务版本已变化", before.revision());
        int updated = jdbcTemplate.update("UPDATE task SET status = ?, completed_at = ?, abandoned_at = ?, " +
                        "revision = revision + 1 WHERE public_id = ? AND user_id = ? AND deleted = FALSE AND revision = ?",
                status,
                "COMPLETED".equals(status) ? timestamp(before.completedAt() == null ? Instant.now() : Instant.parse(before.completedAt())) : null,
                "ABANDONED".equals(status) ? timestamp(before.abandonedAt() == null ? Instant.now() : Instant.parse(before.abandonedAt())) : null,
                validUuid(publicId, "publicId"), userId, expected);
        ensureUpdated(updated, userId, publicId);
        if ("COMPLETED".equals(status)) {
            generateNextInstance(userId, before, Instant.now());
        }
        if ("COMPLETED".equals(status) || "ABANDONED".equals(status)) {
            jdbcTemplate.update("UPDATE task_reminder r JOIN task t ON t.id=r.task_id SET r.cancelled_at=CURRENT_TIMESTAMP(3),r.revision=r.revision+1 " +
                    "WHERE t.public_id=? AND t.user_id=? AND r.cancelled_at IS NULL AND r.sent_at IS NULL", publicId, userId);
        }
        TaskItem changed = taskForUser(userId, publicId);
        changeLog.append(userId, opId, publicId, "UPSERT", syncEntity(changed, false));
        String event = switch (status) {
            case "COMPLETED" -> "COMPLETED";
            case "ABANDONED" -> "ABANDONED";
            default -> "REOPENED";
        };
        String summary = switch (status) {
            case "COMPLETED" -> "完成了任务";
            case "ABANDONED" -> "放弃了任务";
            default -> "重新打开了任务";
        };
        appendActivity(userId, publicId, event, summary, null);
        return changed;
    }

    private void generateNextInstance(long userId, TaskItem current, Instant completedAt) {
        if (current.rrule() == null || current.seriesId() == null || current.plannedDueAt() == null) return;
        Instant next = TaskRecurrence.next(current.rrule(), Instant.parse(current.plannedDueAt()), completedAt,
                current.recurrenceAnchor(), current.timezone(), (current.seriesSequence() == null ? 1 : current.seriesSequence()) + 1,
                recurrenceCalendar);
        if (next == null) return;
        int sequence = current.seriesSequence() + 1;
        String nextId = UUID.randomUUID().toString();
        String opKey = "recurrence:" + current.seriesId() + ":" + next;
        try {
            int inserted = jdbcTemplate.update("INSERT INTO task(public_id,user_id,list_id,parent_id,title,description,status,priority," +
                            "start_at,due_at,all_day,timezone,duration_minutes,series_id,series_sequence,planned_due_at,rrule," +
                            "recurrence_anchor,source,created_by,client_op_key) SELECT ?,user_id,list_id,parent_id,title,description,'OPEN',priority," +
                            "CASE WHEN start_at IS NULL THEN NULL ELSE TIMESTAMPADD(MICROSECOND,TIMESTAMPDIFF(MICROSECOND,due_at,?),start_at) END," +
                            "?,all_day,timezone,duration_minutes,series_id,?,?,rrule,recurrence_anchor,'RECURRENCE',created_by,? " +
                            "FROM task WHERE public_id=? AND user_id=?", nextId, Timestamp.from(next), Timestamp.from(next),
                    sequence, Timestamp.from(next), opKey, current.publicId(), userId);
            if (inserted == 0) return;
        } catch (DuplicateKeyException duplicate) {
            return;
        }
        Long sourceId = jdbcTemplate.queryForObject("SELECT id FROM task WHERE public_id=?", Long.class, current.publicId());
        Long targetId = jdbcTemplate.queryForObject("SELECT id FROM task WHERE public_id=?", Long.class, nextId);
        jdbcTemplate.update("INSERT INTO task_tag_link(task_id,tag_id) SELECT ?,tag_id FROM task_tag_link WHERE task_id=?", targetId, sourceId);
        jdbcTemplate.update("INSERT INTO task_checklist_item(public_id,user_id,task_id,title,completed,sort_order) " +
                "SELECT UUID(),user_id,?,title,FALSE,sort_order FROM task_checklist_item WHERE task_id=? AND deleted=FALSE", targetId, sourceId);
        jdbcTemplate.update("INSERT INTO task_reminder(public_id,user_id,task_id,kind,offset_minutes,remind_at,channel,daily_until_done) " +
                "SELECT UUID(),user_id,?,kind,offset_minutes,CASE WHEN kind='RELATIVE' THEN TIMESTAMPADD(MINUTE,-offset_minutes,?) ELSE remind_at END,channel,daily_until_done " +
                "FROM task_reminder WHERE task_id=? AND cancelled_at IS NULL", targetId, Timestamp.from(next), sourceId);
        jdbcTemplate.update("INSERT IGNORE INTO task_recurrence_instance(series_id,planned_due_at,task_id) VALUES(?,?,?)",
                current.seriesId(), Timestamp.from(next), targetId);
        TaskItem generated = taskForUser(userId, nextId);
        changeLog.append(userId, opKey, nextId, "UPSERT", syncEntity(generated, false));
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    @SchedulerLock(name = "taskRecurrenceScan", lockAtMostFor = "PT25S", lockAtLeastFor = "PT1S")
    @Transactional
    public void scanDueRecurrences() {
        List<ScheduledRecurrence> due = jdbcTemplate.query("SELECT " + TASK_COLUMNS +
                        " FROM task t JOIN task_list l ON l.id=t.list_id WHERE t.deleted=FALSE AND t.status='OPEN' " +
                        "AND t.recurrence_anchor='DUE_DATE' AND t.planned_due_at<=CURRENT_TIMESTAMP(3)",
                (result, rowNum) -> new ScheduledRecurrence(result.getLong("user_id"), task(result)));
        for (ScheduledRecurrence recurrence : due) generateNextInstance(recurrence.userId(), recurrence.task(), Instant.now());
    }

    @Scheduled(cron = "0 20 3 * * *")
    @SchedulerLock(name = "taskActivityRetention", lockAtMostFor = "PT5M", lockAtLeastFor = "PT1S")
    @Transactional
    public void purgeExpiredActivity() {
        jdbcTemplate.update("DELETE FROM task_activity WHERE created_at<TIMESTAMPADD(MONTH,-12,CURRENT_TIMESTAMP(3))");
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
                item.checklist(), item.deletedAt(), null, null, null, null, (long) item.sortOrder(), null, item.rrule(),
                item.recurrenceAnchor(), item.seriesId(), item.seriesSequence(), item.plannedDueAt(), item.focusMinutes(),
                java.util.Map.of("abandonedAt", item.abandonedAt() == null ? "" : item.abandonedAt()));
    }

    static TaskModels.SyncEntity syncEntity(TaskList item, boolean deleted) {
        return new TaskModels.SyncEntity(item.publicId(), null, null, null, null, null, null, null, false,
                null, null, null, null, item.revision() + (deleted ? 1 : 0), deleted, null, List.of(), List.of(), null,
                item.name(), item.color(), item.icon(), item.systemKey(), item.sortOrder(), item.archived(), null, null,
                null, null, null, null, java.util.Map.of("pinned", item.pinned(), "viewType", item.viewType(), "objectType", item.objectType(), "showInSmartLists", item.showInSmartLists()));
    }

    static TaskModels.SyncEntity syncEntity(TaskTag item, boolean deleted) {
        return new TaskModels.SyncEntity(item.publicId(), null, null, null, null, null, null, null, false,
                null, null, null, null, item.revision() + (deleted ? 1 : 0), deleted, item.parentId(), List.of(), List.of(), null,
                item.name(), item.color(), item.icon(), null, (long) item.sortOrder(), null, null, null, null, null, null, null, null);
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
                "VALUES (?, ?, '收集箱', 'INBOX', 0)", UUID.randomUUID().toString(), userId);
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
        List<TaskList> rows = jdbcTemplate.query("SELECT l.public_id,(SELECT g.public_id FROM task_list_group g WHERE g.id=l.group_id) group_public_id," +
                        "l.name,l.color,l.icon,l.system_key,l.sort_order,l.archived,l.is_pinned,l.view_type,l.object_type,l.show_in_smart_lists,l.revision " +
                        "FROM task_list l WHERE l.public_id=? AND l.user_id=? AND l.deleted=?",
                (result, rowNum) -> taskList(result),
                validUuid(publicId, "publicId"), userId, deleted);
        if (rows.isEmpty()) throw new NotFoundException("任务清单不存在");
        return rows.get(0);
    }

    private TaskList taskList(ResultSet result) throws SQLException {
        return new TaskList(result.getString("public_id"), result.getString("group_public_id"), result.getString("name"), result.getString("color"),
                result.getString("icon"), result.getString("system_key"), result.getLong("sort_order"),
                result.getBoolean("archived"), result.getBoolean("is_pinned"), result.getString("view_type"),
                result.getString("object_type"), result.getBoolean("show_in_smart_lists"), result.getLong("revision"));
    }

    private ListGroup listGroupForUser(long userId, String publicId) {
        List<ListGroup> rows = jdbcTemplate.query("SELECT public_id,name,color,icon,sort_order,is_pinned,revision FROM task_list_group " +
                        "WHERE public_id=? AND user_id=?", (result, rowNum) -> listGroup(result),
                validUuid(publicId, "publicId"), userId);
        if (rows.isEmpty()) throw new NotFoundException("清单分组不存在");
        return rows.get(0);
    }

    private ListGroup listGroup(ResultSet result) throws SQLException {
        return new ListGroup(result.getString("public_id"), result.getString("name"), result.getString("color"),
                result.getString("icon"), result.getInt("sort_order"), result.getBoolean("is_pinned"),
                result.getLong("revision"));
    }

    private void ensureUniqueListGroupName(long userId, String name, String publicId) {
        long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_list_group WHERE user_id=? AND name=? " +
                "AND (? IS NULL OR public_id<>?)", Long.class, userId, name, publicId, publicId);
        if (count > 0) throw new ConflictException("清单分组名称已存在", 0);
    }

    private TaskItem taskForUserIncludingDeleted(long userId, String publicId) {
        List<TaskItem> rows = jdbcTemplate.query("SELECT " + TASK_COLUMNS +
                        " FROM task t JOIN task_list l ON l.id=t.list_id WHERE t.public_id=? AND t.user_id=?",
                (result, rowNum) -> task(result), validUuid(publicId, "publicId"), userId);
        if (rows.isEmpty()) throw new NotFoundException("任务不存在");
        return rows.get(0);
    }

    void appendActivity(long userId, String taskPublicId, String eventType, String summary, String detailJson) {
        jdbcTemplate.update("INSERT INTO task_activity(public_id,user_id,task_id,event_type,summary,detail_json) " +
                        "SELECT ?,user_id,id,?,?,? FROM task WHERE public_id=? AND user_id=?",
                UUID.randomUUID().toString(), eventType, summary, detailJson, taskPublicId, userId);
    }

    private String updateSummary(TaskItem before, TaskItem changed) {
        List<String> fields = new ArrayList<>();
        if (!before.title().equals(changed.title())) fields.add("标题");
        if (!before.description().equals(changed.description())) fields.add("描述");
        if (!before.listId().equals(changed.listId())) fields.add("清单");
        if (!before.priority().equals(changed.priority())) fields.add("优先级");
        if (!java.util.Objects.equals(before.startAt(), changed.startAt()) || !java.util.Objects.equals(before.dueAt(), changed.dueAt())) fields.add("日期");
        if (!java.util.Objects.equals(before.parentId(), changed.parentId())) fields.add("父任务");
        if (!before.tagIds().equals(changed.tagIds())) fields.add("标签");
        if (!before.checklist().equals(changed.checklist())) fields.add("检查项");
        if (!java.util.Objects.equals(before.rrule(), changed.rrule())) fields.add("重复规则");
        return fields.isEmpty() ? "更新了任务" : "更新了" + String.join("、", fields);
    }

    private String updateDetail(TaskItem before, TaskItem changed) {
        Map<String, Object> changes = new LinkedHashMap<>();
        addChange(changes, "title", before.title(), changed.title());
        addChange(changes, "description", before.description(), changed.description());
        addChange(changes, "listId", before.listId(), changed.listId());
        addChange(changes, "priority", before.priority(), changed.priority());
        addChange(changes, "startAt", before.startAt(), changed.startAt());
        addChange(changes, "dueAt", before.dueAt(), changed.dueAt());
        addChange(changes, "parentId", before.parentId(), changed.parentId());
        addChange(changes, "tagIds", before.tagIds(), changed.tagIds());
        addChange(changes, "checklist", before.checklist(), changed.checklist());
        addChange(changes, "rrule", before.rrule(), changed.rrule());
        return changes.isEmpty() ? null : changeLog.json(Map.of("changes", changes));
    }

    private void addChange(Map<String, Object> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) changes.put(field, Map.of("before", before == null ? "" : before,
                "after", after == null ? "" : after));
    }

    private ZoneId userZone(long userId) {
        String timezone = jdbcTemplate.queryForObject("SELECT timezone FROM app_user WHERE id=?", String.class, userId);
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Shanghai" : timezone);
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private TaskTag tagForUser(long userId, String publicId) {
        List<TaskTag> rows = jdbcTemplate.query("SELECT t.public_id,p.public_id parent_public_id,t.name,t.color,t.icon,t.sort_order,t.revision " +
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
                result.getString("color"), result.getString("icon"), result.getInt("sort_order"), result.getLong("revision"));
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

    private long nextListOrder(long userId) {
        return jdbcTemplate.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1024 FROM task_list WHERE user_id=?",
                Long.class, userId);
    }

    private TaskItem task(ResultSet result) throws SQLException {
        String publicId = result.getString("public_id");
        long userId = result.getLong("user_id");
        List<String> tags = jdbcTemplate.query("SELECT t.public_id FROM task_tag t JOIN task_tag_link l ON l.tag_id=t.id " +
                        "JOIN task x ON x.id=l.task_id WHERE x.public_id=? ORDER BY t.sort_order, t.id",
                (row, rowNum) -> row.getString(1), publicId);
        List<TaskModels.ChecklistItem> checklist = jdbcTemplate.query("SELECT public_id,title,completed,sort_order,revision " +
                        "FROM task_checklist_item WHERE task_id=(SELECT id FROM task WHERE public_id=? AND user_id=?) " +
                        "AND deleted=FALSE ORDER BY sort_order,id", (row, rowNum) -> new TaskModels.ChecklistItem(
                        row.getString("public_id"), row.getString("title"), row.getBoolean("completed"),
                        row.getInt("sort_order"), row.getLong("revision")), publicId, userId);
        int completedSubtasks = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task child JOIN task parent ON parent.id=child.parent_id " +
                "WHERE parent.public_id=? AND child.user_id=? AND child.deleted=FALSE AND child.status='COMPLETED'", Integer.class, publicId, userId);
        int totalSubtasks = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task child JOIN task parent ON parent.id=child.parent_id " +
                "WHERE parent.public_id=? AND child.user_id=? AND child.deleted=FALSE", Integer.class, publicId, userId);
        return new TaskItem(publicId, result.getString("list_public_id"),
                result.getString("title"), result.getString("description"), result.getString("status"),
                result.getString("priority"), instant(result.getTimestamp("start_at")),
                instant(result.getTimestamp("due_at")), result.getBoolean("all_day"), result.getString("timezone"),
                (Integer) result.getObject("duration_minutes"), result.getString("source"),
                instant(result.getTimestamp("completed_at")), result.getLong("revision"),
                result.getString("parent_public_id"), tags, checklist, completedSubtasks, totalSubtasks,
                result.getBoolean("deleted"), instant(result.getTimestamp("deleted_at")), result.getString("rrule"),
                result.getString("recurrence_anchor"), result.getString("series_id"),
                (Integer) result.getObject("series_sequence"), instant(result.getTimestamp("planned_due_at")),
                result.getInt("sort_order"), result.getInt("focus_minutes"), instant(result.getTimestamp("created_at")),
                result.getInt("attachment_count"), instant(result.getTimestamp("abandoned_at")));
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
        return enumValue(value, null, List.of("OPEN", "COMPLETED", "ABANDONED"), "status");
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

    private record ScheduledRecurrence(long userId, TaskItem task) {
    }
}
