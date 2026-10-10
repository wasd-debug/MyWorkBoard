package com.salarytracker.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salarytracker.identity.CurrentUserResolver;
import com.salarytracker.platform.ConflictException;
import com.salarytracker.platform.NotFoundException;
import com.salarytracker.platform.SyncResetRequiredException;
import com.salarytracker.task.TaskModels.SyncAction;
import com.salarytracker.task.TaskModels.SyncChange;
import com.salarytracker.task.TaskModels.SyncEntity;
import com.salarytracker.task.TaskModels.SyncOperation;
import com.salarytracker.task.TaskModels.SyncOperationResult;
import com.salarytracker.task.TaskModels.SyncPayload;
import com.salarytracker.task.TaskModels.SyncPullResponse;
import com.salarytracker.task.TaskModels.SyncPushResponse;
import com.salarytracker.task.TaskModels.SyncStatus;
import com.salarytracker.task.TaskModels.TaskCommand;
import com.salarytracker.task.TaskModels.TaskItem;
import com.salarytracker.task.TaskModels.TaskListCommand;
import com.salarytracker.task.TaskModels.TaskTagCommand;
import com.salarytracker.task.TaskEfficiencyModels.Countdown;
import com.salarytracker.task.TaskEfficiencyModels.CountdownCommand;
import com.salarytracker.task.TaskEfficiencyModels.Habit;
import com.salarytracker.task.TaskEfficiencyModels.HabitCheckin;
import com.salarytracker.task.TaskEfficiencyModels.HabitCheckinCommand;
import com.salarytracker.task.TaskEfficiencyModels.HabitCommand;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TaskSyncService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final CurrentUserResolver currentUser;
    private final TaskService tasks;
    private final TaskEfficiencyService efficiency;

    public TaskSyncService(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserResolver currentUser, TaskService tasks,
                           TaskEfficiencyService efficiency) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.tasks = tasks;
        this.efficiency = efficiency;
    }

    public SyncPushResponse push(List<SyncOperation> operations) {
        List<SyncOperationResult> results = new ArrayList<>();
        for (SyncOperation operation : operations == null ? List.<SyncOperation>of() : operations) {
            results.add(apply(operation));
        }
        return new SyncPushResponse(results,
                results.stream().filter(result -> result.status() == SyncStatus.APPLIED).count(),
                results.stream().filter(result -> result.status() == SyncStatus.DUPLICATE).count());
    }

    public SyncPullResponse pull(long cursor, int limit) {
        long userId = currentUser.id();
        long max = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM task_sync_oplog WHERE user_id=?",
                Long.class, userId);
        long min = jdbc.queryForObject("SELECT COALESCE(MIN(id),0) FROM task_sync_oplog WHERE user_id=?",
                Long.class, userId);
        if (cursor < 0 || cursor > max || cursor > 0 && min > 0 && cursor < min - 1) {
            throw new SyncResetRequiredException("任务同步游标已失效，请重新下载当前用户任务");
        }
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        List<SyncChange> changes = jdbc.query("SELECT id,op_id,entity_type,entity_id,operation,payload_json,created_at " +
                        "FROM task_sync_oplog WHERE user_id=? AND id>? ORDER BY id LIMIT ?",
                (result, rowNum) -> new SyncChange(result.getLong("id"), result.getString("op_id"),
                        result.getString("entity_type"), result.getString("entity_id"),
                        SyncAction.valueOf(result.getString("operation")),
                        readEntity(result.getString("payload_json")), timestamp(result.getTimestamp("created_at"))),
                userId, cursor, safeLimit);
        long next = changes.isEmpty() ? cursor : changes.get(changes.size() - 1).cursor();
        return new SyncPullResponse(changes, next, next < max, max);
    }

    private SyncOperationResult apply(SyncOperation operation) {
        String opId = operation == null ? null : operation.opId();
        String entityId = operation == null ? null : first(operation.entityId(),
                operation.payload() == null ? null : operation.payload().id());
        String entityType = operation == null ? "task" : operation.entityType();
        if (!validId(opId, 120)) return result(opId, entityType, entityId, SyncStatus.REJECTED, null, null,
                "opId 必填且长度不能超过 120");
        if (entityType == null || !List.of("task", "task-list", "task-tag", "habit", "habit-checkin", "countdown").contains(entityType)) return result(opId,
                operation == null ? "task" : operation.entityType(), entityId, SyncStatus.REJECTED,
                null, null, "entityType 不支持");
        if (!validUuid(entityId)) return result(opId, entityType, entityId, SyncStatus.REJECTED,
                null, null, "entityId 必须是 UUID");
        if (operation.operation() == null || operation.operation() == SyncAction.PURGE) {
            return result(opId, entityType, entityId, SyncStatus.REJECTED, null, null, "operation 不支持");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM task_sync_oplog WHERE user_id=? AND op_id=?",
                Long.class, currentUser.id(), opId) > 0) {
            return result(opId, operation.entityType(), entityId, SyncStatus.DUPLICATE, null, null, null);
        }
        try {
            if ("task-list".equals(operation.entityType())) return applyList(operation, entityId);
            if ("task-tag".equals(operation.entityType())) return applyTag(operation, entityId);
            if ("habit".equals(operation.entityType())) return applyHabit(operation, entityId);
            if ("habit-checkin".equals(operation.entityType())) return applyHabitCheckin(operation, entityId);
            if ("countdown".equals(operation.entityType())) return applyCountdown(operation, entityId);
            TaskItem current = current(entityId);
            long baseRevision = operation.baseRevision() == null ? 0 : operation.baseRevision();
            if (operation.operation() == SyncAction.DELETE) {
                if (current == null) {
                    return inaccessible(opId, entityId, "任务不存在");
                }
                tasks.deleteForSync(entityId, baseRevision, opId);
                SyncEntity deleted = tasks.deletedEntityForSync(entityId);
                return result(opId, entityId, SyncStatus.APPLIED, deleted, null, null);
            }
            SyncPayload payload = operation.payload();
            if (payload == null) throw new IllegalArgumentException("payload 必填");
            if (current == null && (baseRevision > 0 || tasks.existsForAnotherUser(entityId))) return inaccessible(opId, entityId, "任务不存在或已删除，请刷新垃圾桶");
            TaskCommand command = new TaskCommand(payload.listId(), payload.title(), payload.description(),
                    payload.priority(), payload.startAt(), payload.dueAt(), payload.allDay(), payload.timezone(),
                    payload.durationMinutes(), payload.parentId(), payload.tagIds(), payload.checklist(), payload.rrule(),
                    payload.recurrenceAnchor());
            TaskItem saved = current == null
                    ? tasks.createForSync(command, payload.status(), opId, entityId)
                    : tasks.updateForSync(entityId, command, payload.status(), baseRevision, opId);
            return result(opId, entityId, SyncStatus.APPLIED, TaskService.syncEntity(saved, false), null, null);
        } catch (ConflictException exception) {
            if ("task-list".equals(operation.entityType())) {
                var server = currentList(entityId);
                return new SyncOperationResult(opId, "task-list", entityId, SyncStatus.CONFLICT, null,
                        exception.getServerRevision(), server == null ? null : TaskService.syncEntity(server, false),
                        List.of(), exception.getMessage());
            }
            if ("task-tag".equals(operation.entityType())) {
                var server = currentTag(entityId);
                return new SyncOperationResult(opId, "task-tag", entityId, SyncStatus.CONFLICT, null,
                        exception.getServerRevision(), server == null ? null : TaskService.syncEntity(server, false),
                        List.of(), exception.getMessage());
            }
            if ("habit".equals(operation.entityType())) return efficiencyConflict(operation, exception, currentHabit(entityId));
            if ("habit-checkin".equals(operation.entityType())) return efficiencyConflict(operation, exception, currentCheckin(entityId));
            if ("countdown".equals(operation.entityType())) return efficiencyConflict(operation, exception, currentCountdown(entityId));
            TaskItem server = current(entityId);
            return new SyncOperationResult(opId, "task", entityId, SyncStatus.CONFLICT, null,
                    exception.getServerRevision(), server == null ? null : TaskService.syncEntity(server, false),
                    conflictFields(operation.payload(), server), exception.getMessage());
        } catch (NotFoundException exception) {
            return "task".equals(entityType) ? inaccessible(opId, entityId, exception.getMessage())
                    : inaccessible(opId, entityType, entityId, exception.getMessage());
        } catch (DuplicateKeyException exception) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM task_sync_oplog WHERE user_id=? AND op_id=?",
                    Long.class, currentUser.id(), opId) > 0) {
                return result(opId, operation.entityType(), entityId, SyncStatus.DUPLICATE, null, null, null);
            }
            return result(opId, entityType, entityId, SyncStatus.REJECTED, null, null, "资源标识、名称或操作已存在");
        } catch (IllegalArgumentException exception) {
            return result(opId, entityType, entityId, SyncStatus.REJECTED, null, null, exception.getMessage());
        }
    }

    private SyncOperationResult applyList(SyncOperation operation, String entityId) {
        var current = currentList(entityId);
        long base = operation.baseRevision() == null ? 0 : operation.baseRevision();
        if (operation.operation() == SyncAction.DELETE) {
            if (current == null) return inaccessible(operation.opId(), "task-list", entityId, "任务清单不存在");
            tasks.deleteListForSync(entityId, base, operation.opId());
            return result(operation.opId(), "task-list", entityId, SyncStatus.APPLIED,
                    TaskService.syncEntity(current, true), null, null);
        }
        SyncPayload payload = operation.payload();
        if (payload == null) throw new IllegalArgumentException("payload 必填");
        if (current == null && (base > 0 || tasks.listExistsForAnotherUser(entityId))) return inaccessible(operation.opId(), "task-list", entityId, "清单不存在或已删除");
        TaskListCommand command = new TaskListCommand(payload.name(), payload.color(), payload.icon(),
                payload.sortOrder(), payload.archived());
        var saved = current == null ? tasks.createListForSync(command, entityId, operation.opId())
                : tasks.updateListForSync(entityId, command, base, operation.opId());
        return result(operation.opId(), "task-list", entityId, SyncStatus.APPLIED,
                TaskService.syncEntity(saved, false), null, null);
    }

    private SyncOperationResult applyTag(SyncOperation operation, String entityId) {
        var current = currentTag(entityId);
        long base = operation.baseRevision() == null ? 0 : operation.baseRevision();
        if (operation.operation() == SyncAction.DELETE) {
            if (current == null) return inaccessible(operation.opId(), "task-tag", entityId, "任务标签不存在");
            tasks.deleteTagForSync(entityId, base, operation.opId());
            return result(operation.opId(), "task-tag", entityId, SyncStatus.APPLIED,
                    TaskService.syncEntity(current, true), null, null);
        }
        SyncPayload payload = operation.payload();
        if (payload == null) throw new IllegalArgumentException("payload 必填");
        if (current == null && (base > 0 || tasks.tagExistsForAnotherUser(entityId))) return inaccessible(operation.opId(), "task-tag", entityId, "标签不存在或已删除");
        TaskTagCommand command = new TaskTagCommand(payload.parentId(), payload.name(), payload.color(), payload.sortOrder());
        var saved = current == null ? tasks.createTagForSync(command, entityId, operation.opId())
                : tasks.updateTagForSync(entityId, command, base, operation.opId());
        return result(operation.opId(), "task-tag", entityId, SyncStatus.APPLIED,
                TaskService.syncEntity(saved, false), null, null);
    }

    private SyncOperationResult applyHabit(SyncOperation operation, String entityId) {
        Habit current = currentHabit(entityId);
        long base = operation.baseRevision() == null ? 0 : operation.baseRevision();
        if (operation.operation() == SyncAction.DELETE) {
            if (current == null) return result(operation.opId(), "habit", entityId, SyncStatus.REJECTED, null, null, "习惯不存在");
            efficiency.deleteHabitForSync(entityId, base, operation.opId());
            return result(operation.opId(), "habit", entityId, SyncStatus.APPLIED,
                    TaskEfficiencyService.syncEntity(current, true), null, null);
        }
        var extra = operation.payload() == null ? null : operation.payload().extra();
        if (extra == null) throw new IllegalArgumentException("payload.extra 必填");
        HabitCommand command = new HabitCommand(string(extra, "name"), string(extra, "icon"), string(extra, "color"),
                string(extra, "frequency"), integer(extra, "targetCount"), integers(extra, "customDays"),
                string(extra, "remindAt"), string(extra, "startDate"), bool(extra, "archived"), integer(extra, "sortOrder"));
        Habit saved = current == null
                ? efficiency.createHabitForSync(command, entityId, operation.opId())
                : efficiency.updateHabitForSync(entityId, command, base, operation.opId());
        return result(operation.opId(), "habit", entityId, SyncStatus.APPLIED,
                TaskEfficiencyService.syncEntity(saved, false), null, null);
    }

    private SyncOperationResult applyHabitCheckin(SyncOperation operation, String entityId) {
        if (operation.operation() == SyncAction.DELETE) throw new IllegalArgumentException("习惯打卡不支持删除");
        var extra = operation.payload() == null ? null : operation.payload().extra();
        if (extra == null) throw new IllegalArgumentException("payload.extra 必填");
        HabitCheckin current = currentCheckin(entityId);
        long base = operation.baseRevision() == null ? 0 : operation.baseRevision();
        HabitCheckin saved = efficiency.checkinForSync(string(extra, "habitId"),
                new HabitCheckinCommand(string(extra, "date"), integer(extra, "count"), string(extra, "status")),
                entityId, base, operation.opId());
        return result(operation.opId(), "habit-checkin", entityId, SyncStatus.APPLIED,
                TaskEfficiencyService.syncEntity(saved, false), null, null);
    }

    private SyncOperationResult applyCountdown(SyncOperation operation, String entityId) {
        Countdown current = currentCountdown(entityId);
        long base = operation.baseRevision() == null ? 0 : operation.baseRevision();
        if (operation.operation() == SyncAction.DELETE) {
            if (current == null) return result(operation.opId(), "countdown", entityId, SyncStatus.REJECTED, null, null, "倒数日不存在");
            efficiency.deleteCountdownForSync(entityId, base, operation.opId());
            return result(operation.opId(), "countdown", entityId, SyncStatus.APPLIED,
                    TaskEfficiencyService.syncEntity(current, true), null, null);
        }
        var extra = operation.payload() == null ? null : operation.payload().extra();
        if (extra == null) throw new IllegalArgumentException("payload.extra 必填");
        CountdownCommand command = new CountdownCommand(string(extra, "title"), string(extra, "targetDate"),
                string(extra, "kind"), bool(extra, "repeatYearly"), bool(extra, "pinned"), string(extra, "color"),
                string(extra, "note"));
        Countdown saved = current == null
                ? efficiency.createCountdownForSync(command, entityId, operation.opId())
                : efficiency.updateCountdownForSync(entityId, command, base, operation.opId());
        return result(operation.opId(), "countdown", entityId, SyncStatus.APPLIED,
                TaskEfficiencyService.syncEntity(saved, false), null, null);
    }

    private SyncOperationResult efficiencyConflict(SyncOperation operation, ConflictException exception, Object current) {
        SyncEntity server = current instanceof Habit habit ? TaskEfficiencyService.syncEntity(habit, false)
                : current instanceof HabitCheckin checkin ? TaskEfficiencyService.syncEntity(checkin, false)
                : current instanceof Countdown countdown ? TaskEfficiencyService.syncEntity(countdown, false) : null;
        return new SyncOperationResult(operation.opId(), operation.entityType(), operation.entityId(), SyncStatus.CONFLICT,
                null, exception.getServerRevision(), server, List.of(), exception.getMessage());
    }

    private Habit currentHabit(String id) { try { return efficiency.habitForSync(id); } catch (NotFoundException exception) { return null; } }
    private HabitCheckin currentCheckin(String id) { try { return efficiency.checkinForSync(id); } catch (NotFoundException exception) { return null; } }
    private Countdown currentCountdown(String id) { try { return efficiency.countdownForSync(id); } catch (NotFoundException exception) { return null; } }

    private String string(java.util.Map<String, Object> values, String key) {
        Object value = values.get(key); return value == null ? null : String.valueOf(value);
    }
    private Integer integer(java.util.Map<String, Object> values, String key) {
        Object value = values.get(key); return value == null ? null : value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }
    private Boolean bool(java.util.Map<String, Object> values, String key) {
        Object value = values.get(key); return value == null ? null : value instanceof Boolean flag ? flag : Boolean.valueOf(String.valueOf(value));
    }
    private List<Integer> integers(java.util.Map<String, Object> values, String key) {
        Object value = values.get(key); if (!(value instanceof List<?> list)) return null;
        return list.stream().map(item -> item instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(item))).toList();
    }

    private SyncOperationResult inaccessible(String opId, String entityId, String message) {
        SyncStatus status = tasks.existsForAnotherUser(entityId) ? SyncStatus.FORBIDDEN : SyncStatus.REJECTED;
        return result(opId, entityId, status, null, null, message);
    }

    private SyncOperationResult inaccessible(String opId, String entityType, String entityId, String message) {
        if (!List.of("task-list", "task-tag").contains(entityType)) {
            return result(opId, entityType, entityId, SyncStatus.REJECTED, null, null, message);
        }
        boolean foreign = "task-list".equals(entityType) ? tasks.listExistsForAnotherUser(entityId)
                : tasks.tagExistsForAnotherUser(entityId);
        return result(opId, entityType, entityId, foreign ? SyncStatus.FORBIDDEN : SyncStatus.REJECTED,
                null, null, message);
    }

    private TaskModels.TaskList currentList(String entityId) { try { return tasks.currentListForSync(entityId); } catch (NotFoundException exception) { return null; } }
    private TaskModels.TaskTag currentTag(String entityId) { try { return tasks.currentTagForSync(entityId); } catch (NotFoundException exception) { return null; } }

    private TaskItem current(String entityId) {
        try {
            return tasks.currentForSync(entityId);
        } catch (NotFoundException exception) {
            return null;
        }
    }

    private List<String> conflictFields(SyncPayload local, TaskItem server) {
        if (local == null || server == null) return List.of();
        List<String> fields = new ArrayList<>();
        different(fields, "listId", local.listId(), server.listId());
        different(fields, "title", local.title(), server.title());
        different(fields, "description", local.description(), server.description());
        different(fields, "status", local.status(), server.status());
        different(fields, "priority", local.priority(), server.priority());
        different(fields, "dueAt", local.dueAt(), server.dueAt());
        different(fields, "rrule", local.rrule(), server.rrule());
        different(fields, "recurrenceAnchor", local.recurrenceAnchor(), server.recurrenceAnchor());
        return fields;
    }

    private void different(List<String> fields, String name, Object local, Object server) {
        if (local != null && !String.valueOf(local).equals(String.valueOf(server))) fields.add(name);
    }

    private SyncOperationResult result(String opId, String entityId, SyncStatus status, SyncEntity entity,
                                       Long serverRevision, String message) {
        return result(opId, "task", entityId, status, entity, serverRevision, message);
    }

    private SyncOperationResult result(String opId, String entityType, String entityId, SyncStatus status,
                                       SyncEntity entity, Long serverRevision, String message) {
        return new SyncOperationResult(opId, entityType, entityId, status, entity, serverRevision,
                null, List.of(), message);
    }

    private SyncEntity readEntity(String json) {
        try {
            return mapper.readValue(json, SyncEntity.class);
        } catch (Exception exception) {
            throw new IllegalStateException("任务同步数据读取失败", exception);
        }
    }

    private String timestamp(Timestamp value) {
        return value == null ? null : value.toInstant().toString();
    }

    private String first(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private boolean validId(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }

    private boolean validUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }
}
