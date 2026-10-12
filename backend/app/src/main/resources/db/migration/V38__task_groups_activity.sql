CREATE TABLE task_list_group (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_list_group_public (public_id),
    UNIQUE KEY uk_task_list_group_user_name (user_id, name),
    KEY idx_task_list_group_user_sort (user_id, sort_order, id),
    CONSTRAINT fk_task_list_group_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE task_list
    ADD COLUMN group_id BIGINT NULL AFTER user_id,
    ADD KEY idx_task_list_group_sort (group_id, sort_order, id),
    ADD CONSTRAINT fk_task_list_group FOREIGN KEY (group_id) REFERENCES task_list_group(id) ON DELETE SET NULL;

CREATE TABLE task_activity (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    summary VARCHAR(255) NOT NULL,
    detail_json JSON NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_activity_public (public_id),
    KEY idx_task_activity_task_time (task_id, created_at DESC, id DESC),
    KEY idx_task_activity_retention (created_at),
    CONSTRAINT fk_task_activity_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_task_activity_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
