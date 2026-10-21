ALTER TABLE task_filter
    ADD COLUMN filter_mode VARCHAR(16) NOT NULL DEFAULT 'BASIC' AFTER name,
    ADD COLUMN tag_mode VARCHAR(16) NOT NULL DEFAULT 'SELECTED' AFTER tag_ids,
    ADD COLUMN task_types VARCHAR(32) NOT NULL DEFAULT '' AFTER priorities,
    ADD COLUMN due_start DATE NULL AFTER due_range,
    ADD COLUMN due_end DATE NULL AFTER due_start;
