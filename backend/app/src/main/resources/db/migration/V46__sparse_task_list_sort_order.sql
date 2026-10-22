ALTER TABLE task_list
    MODIFY COLUMN sort_order BIGINT NOT NULL DEFAULT 0;

UPDATE task_list
SET sort_order = sort_order * 1024;
