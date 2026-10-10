ALTER TABLE task_list
    ADD COLUMN color VARCHAR(24) NULL AFTER name,
    ADD COLUMN icon VARCHAR(40) NULL AFTER color,
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE AFTER sort_order;

ALTER TABLE task
    ADD COLUMN parent_id BIGINT NULL AFTER list_id,
    ADD COLUMN sort_order INT NOT NULL DEFAULT 0 AFTER parent_id,
    MODIFY COLUMN title VARCHAR(500) NOT NULL,
    MODIFY COLUMN description MEDIUMTEXT NOT NULL,
    ADD KEY idx_task_user_parent (user_id, parent_id, deleted, sort_order),
    ADD KEY idx_task_user_due (user_id, deleted, status, due_at),
    ADD CONSTRAINT fk_task_parent FOREIGN KEY (parent_id) REFERENCES task(id);

CREATE TABLE task_tag (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    parent_id BIGINT NULL,
    name VARCHAR(30) NOT NULL,
    normalized_name VARCHAR(30) NOT NULL,
    color VARCHAR(24) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_tag_public_id (public_id),
    UNIQUE KEY uk_task_tag_user_name (user_id, normalized_name),
    KEY idx_task_tag_user_parent (user_id, parent_id, deleted, sort_order),
    CONSTRAINT fk_task_tag_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_task_tag_parent FOREIGN KEY (parent_id) REFERENCES task_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_tag_link (
    task_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (task_id, tag_id),
    KEY idx_task_tag_link_tag (tag_id, task_id),
    CONSTRAINT fk_task_tag_link_task FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_task_tag_link_tag FOREIGN KEY (tag_id) REFERENCES task_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_checklist_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_checklist_public_id (public_id),
    KEY idx_task_checklist_task_sort (task_id, deleted, sort_order),
    CONSTRAINT fk_task_checklist_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_task_checklist_task FOREIGN KEY (task_id) REFERENCES task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
