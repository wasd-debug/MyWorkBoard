-- 条目级类型：任务 / 笔记（默认 TASK；历史数据仍按所在清单类型兼容）
-- 幂等写法：本地/生产库可能已手工加过该列，重复执行不报错
SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'task' AND column_name = 'item_kind'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE task ADD COLUMN item_kind VARCHAR(10) NOT NULL DEFAULT ''TASK'' AFTER priority',
    'SELECT 1');
PREPARE add_item_kind FROM @ddl;
EXECUTE add_item_kind;
DEALLOCATE PREPARE add_item_kind;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'task' AND index_name = 'idx_task_item_kind'
);
SET @ddl_index := IF(@index_exists = 0,
    'CREATE INDEX idx_task_item_kind ON task (user_id, item_kind)',
    'SELECT 1');
PREPARE add_item_kind_index FROM @ddl_index;
EXECUTE add_item_kind_index;
DEALLOCATE PREPARE add_item_kind_index;
