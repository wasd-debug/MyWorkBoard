ALTER TABLE task_list_group
    ADD COLUMN color VARCHAR(16) NULL AFTER name,
    ADD COLUMN icon VARCHAR(24) NULL AFTER color;
