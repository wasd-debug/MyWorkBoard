-- 检查项级提醒：remind_at 为用户设置的时间，fired_at 记录已推送时间（避免重复提醒）
SET @remind_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'task_checklist_item' AND column_name = 'remind_at'
);
SET @ddl_remind := IF(@remind_exists = 0,
    'ALTER TABLE task_checklist_item ADD COLUMN remind_at timestamp(3) NULL AFTER sort_order',
    'SELECT 1');
PREPARE add_remind FROM @ddl_remind;
EXECUTE add_remind;
DEALLOCATE PREPARE add_remind;

SET @fired_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'task_checklist_item' AND column_name = 'fired_at'
);
SET @ddl_fired := IF(@fired_exists = 0,
    'ALTER TABLE task_checklist_item ADD COLUMN fired_at timestamp(3) NULL AFTER remind_at',
    'SELECT 1');
PREPARE add_fired FROM @ddl_fired;
EXECUTE add_fired;
DEALLOCATE PREPARE add_fired;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'task_checklist_item' AND index_name = 'idx_checklist_remind'
);
SET @ddl_idx := IF(@idx_exists = 0,
    'CREATE INDEX idx_checklist_remind ON task_checklist_item (remind_at, fired_at)',
    'SELECT 1');
PREPARE add_idx FROM @ddl_idx;
EXECUTE add_idx;
DEALLOCATE PREPARE add_idx;
