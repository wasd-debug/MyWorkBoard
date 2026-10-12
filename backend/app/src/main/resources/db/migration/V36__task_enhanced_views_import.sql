CREATE TABLE task_filter (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    list_ids TEXT NOT NULL,
    tag_ids TEXT NOT NULL,
    priorities VARCHAR(64) NOT NULL,
    task_status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    due_range VARCHAR(16) NOT NULL DEFAULT 'ANY',
    keyword VARCHAR(200) NOT NULL DEFAULT '',
    include_subtasks BOOLEAN NOT NULL DEFAULT TRUE,
    match_all_tags BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_filter_public_id (public_id),
    KEY idx_task_filter_user_sort (user_id, pinned, sort_order),
    CONSTRAINT fk_task_filter_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    rows_json MEDIUMTEXT NOT NULL,
    valid_count INT NOT NULL,
    duplicate_count INT NOT NULL,
    error_count INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    committed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_import_public_id (public_id),
    KEY idx_task_import_user_expiry (user_id, expires_at),
    CONSTRAINT fk_task_import_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE task ADD FULLTEXT KEY ft_task_title_description (title, description);
