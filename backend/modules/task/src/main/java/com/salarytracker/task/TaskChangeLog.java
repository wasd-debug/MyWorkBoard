package com.salarytracker.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class TaskChangeLog {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    TaskChangeLog(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    void append(long userId, String opId, String entityId, String operation, Object payload) {
        append(userId, opId, "task", entityId, operation, payload);
    }

    void append(long userId, String opId, String entityType, String entityId, String operation, Object payload) {
        try {
            jdbc.update("INSERT INTO task_sync_oplog(user_id,op_id,entity_type,entity_id,operation,payload_json) " +
                            "VALUES(?,?,?,?,?,?)",
                    userId, opId, entityType, entityId, operation, mapper.writeValueAsString(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("任务同步数据序列化失败", exception);
        }
    }
}
