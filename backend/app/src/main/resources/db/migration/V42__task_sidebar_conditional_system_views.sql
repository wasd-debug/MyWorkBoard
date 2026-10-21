ALTER TABLE task_sidebar_preference
    ADD COLUMN conditional_system_views_json MEDIUMTEXT NULL AFTER pinned_system_views_json;

UPDATE task_sidebar_preference
SET conditional_system_views_json = '[]'
WHERE conditional_system_views_json IS NULL;

ALTER TABLE task_sidebar_preference
    MODIFY COLUMN conditional_system_views_json MEDIUMTEXT NOT NULL;
