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

    public TaskSyncService(JdbcTemplate jdbc, ObjectMapper mapper, CurrentUserResolver currentUser, TaskService tasks) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.tasks = tasks;
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
        if (!validId(opId, 120)) return result(opId, entityId, SyncStatus.REJECTED, null, null,
                "opId 必填且长度不能超过 120");
        if (!"task".equals(operation.entityType())) return result(opId, entityId, SyncStatus.REJECTED,
                null, null, "entityType 仅支持 task");
        if (!validUuid(entityId)) return result(opId, entityId, SyncStatus.REJECTED,
                null, null, "entityId 必须是 UUID");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM task_sync_oplog WHERE user_id=? AND op_id=?",
                Long.class, currentUser.id(), opId) > 0) {
            return result(opId, entityId, SyncStatus.DUPLICATE, null, null, null);
        }
        try {
            TaskItem current = current(entityId);
            long baseRevision = operation.baseRevision() == null ? 0 : operation.baseRevision();
            if (operation.operation() == SyncAction.DELETE) {
                if (current == null) {
                    return inaccessible(opId, entityId, "任务不存在");
                }
                tasks.deleteForSync(entityId, baseRevision, opId);
                SyncEntity deleted = new SyncEntity(entityId, current.listId(), current.title(), current.description(),
                        current.status(), current.priority(), current.startAt(), current.dueAt(), current.allDay(),
                        current.timezone(), current.durationMinutes(), current.source(), current.completedAt(),
                        baseRevision + 1, true);
                return result(opId, entityId, SyncStatus.APPLIED, deleted, null, null);
            }
            SyncPayload payload = operation.payload();
            if (payload == null) throw new IllegalArgumentException("payload 必填");
            TaskCommand command = new TaskCommand(payload.listId(), payload.title(), payload.description(),
                    payload.priority(), payload.startAt(), payload.dueAt(), payload.allDay(), payload.timezone(),
                    payload.durationMinutes());
            TaskItem saved = current == null
                    ? tasks.createForSync(command, payload.status(), opId, entityId)
                    : tasks.updateForSync(entityId, command, payload.status(), baseRevision, opId);
            return result(opId, entityId, SyncStatus.APPLIED, TaskService.syncEntity(saved, false), null, null);
        } catch (ConflictException exception) {
            TaskItem server = current(entityId);
            return new SyncOperationResult(opId, "task", entityId, SyncStatus.CONFLICT, null,
                    exception.getServerRevision(), server == null ? null : TaskService.syncEntity(server, false),
                    conflictFields(operation.payload(), server), exception.getMessage());
        } catch (NotFoundException exception) {
            return inaccessible(opId, entityId, exception.getMessage());
        } catch (DuplicateKeyException exception) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM task_sync_oplog WHERE user_id=? AND op_id=?",
                    Long.class, currentUser.id(), opId) > 0) {
                return result(opId, entityId, SyncStatus.DUPLICATE, null, null, null);
            }
            return result(opId, entityId, SyncStatus.REJECTED, null, null, "任务标识或操作已存在");
        } catch (IllegalArgumentException exception) {
            return result(opId, entityId, SyncStatus.REJECTED, null, null, exception.getMessage());
        }
    }

    private SyncOperationResult inaccessible(String opId, String entityId, String message) {
        SyncStatus status = tasks.existsForAnotherUser(entityId) ? SyncStatus.FORBIDDEN : SyncStatus.REJECTED;
        return result(opId, entityId, status, null, null, message);
    }

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
        return fields;
    }

    private void different(List<String> fields, String name, Object local, Object server) {
        if (local != null && !String.valueOf(local).equals(String.valueOf(server))) fields.add(name);
    }

    private SyncOperationResult result(String opId, String entityId, SyncStatus status, SyncEntity entity,
                                       Long serverRevision, String message) {
        return new SyncOperationResult(opId, "task", entityId, status, entity, serverRevision,
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
