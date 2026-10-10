CREATE TABLE task_sync_oplog (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    op_id VARCHAR(120) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id CHAR(36) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_sync_user_op (user_id, op_id),
    KEY idx_task_sync_user_cursor (user_id, id),
    CONSTRAINT fk_task_sync_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
