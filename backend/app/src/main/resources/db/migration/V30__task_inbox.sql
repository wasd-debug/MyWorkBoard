CREATE TABLE task_list (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    system_key VARCHAR(32) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_list_public_id (public_id),
    UNIQUE KEY uk_task_list_user_system (user_id, system_key),
    KEY idx_task_list_user_sort (user_id, deleted, sort_order),
    CONSTRAINT fk_task_list_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    list_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(4000) NOT NULL DEFAULT '',
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(24) NOT NULL DEFAULT 'NONE',
    start_at TIMESTAMP(3) NULL,
    due_at TIMESTAMP(3) NULL,
    all_day BOOLEAN NOT NULL DEFAULT FALSE,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    duration_minutes INT NULL,
    source VARCHAR(24) NOT NULL DEFAULT 'WEB',
    completed_at TIMESTAMP(3) NULL,
    created_by BIGINT NOT NULL,
    client_op_key VARCHAR(120) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_public_id (public_id),
    UNIQUE KEY uk_task_user_client_op (user_id, client_op_key),
    KEY idx_task_user_list_status (user_id, list_id, deleted, status, created_at),
    CONSTRAINT fk_task_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_task_list FOREIGN KEY (list_id) REFERENCES task_list(id),
    CONSTRAINT fk_task_created_by FOREIGN KEY (created_by) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_setting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    default_list_id BIGINT NOT NULL,
    default_view VARCHAR(32) NOT NULL DEFAULT 'INBOX',
    week_start TINYINT NOT NULL DEFAULT 1,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_setting_user (user_id),
    CONSTRAINT fk_task_setting_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_task_setting_default_list FOREIGN KEY (default_list_id) REFERENCES task_list(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO permission (code, module, action) VALUES
    ('task:read', 'task', 'read'),
    ('task:write', 'task', 'write');

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.code IN ('task:read', 'task:write')
WHERE r.code IN ('ADMIN', 'USER');
