ALTER TABLE task_list
    ADD COLUMN view_type VARCHAR(16) NOT NULL DEFAULT 'LIST' AFTER icon,
    ADD COLUMN object_type VARCHAR(16) NOT NULL DEFAULT 'TASK' AFTER view_type,
    ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE AFTER archived;

ALTER TABLE task_list_group
    ADD COLUMN is_pinned BOOLEAN NOT NULL DEFAULT FALSE AFTER sort_order;

ALTER TABLE task_tag
    ADD COLUMN icon VARCHAR(40) NULL AFTER color;

ALTER TABLE task_filter
    ADD COLUMN icon VARCHAR(40) NULL AFTER name,
    ADD COLUMN rules_json MEDIUMTEXT NULL AFTER keyword;

CREATE TABLE task_sidebar_preference (
    user_id BIGINT NOT NULL,
    hidden_system_views_json MEDIUMTEXT NOT NULL,
    pinned_system_views_json MEDIUMTEXT NOT NULL,
    hidden_list_ids_json MEDIUMTEXT NOT NULL,
    pinned_list_ids_json MEDIUMTEXT NOT NULL,
    hidden_tag_ids_json MEDIUMTEXT NOT NULL,
    pinned_tag_ids_json MEDIUMTEXT NOT NULL,
    hidden_filter_ids_json MEDIUMTEXT NOT NULL,
    pinned_filter_ids_json MEDIUMTEXT NOT NULL,
    collapsed_sections_json MEDIUMTEXT NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_task_sidebar_preference_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
