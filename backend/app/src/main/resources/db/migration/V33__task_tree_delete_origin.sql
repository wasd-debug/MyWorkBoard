ALTER TABLE task
    ADD COLUMN deleted_root_public_id CHAR(36) NULL AFTER deleted_at,
    ADD KEY idx_task_deleted_root (user_id, deleted_root_public_id, deleted);
