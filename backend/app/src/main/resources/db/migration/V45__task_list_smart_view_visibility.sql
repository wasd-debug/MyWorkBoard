ALTER TABLE task_list
    ADD COLUMN show_in_smart_lists BOOLEAN NOT NULL DEFAULT TRUE AFTER object_type;
