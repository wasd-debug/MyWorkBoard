-- 列表页的 ORDER BY 是 t.status = 'COMPLETED', t.sort_order, t.created_at, t.id。
-- 这是个表达式，普通索引帮不上忙，MySQL 只能把该用户的全部任务取出来 filesort
-- （实测 9.7k 行 / 22.6ms，占单页 60ms 里的大头）。
-- 函数索引把「已完成」压成隐藏的 0/1 列并排进索引，排序直接走索引，LIMIT 50 可以提前停。
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'task' AND index_name = 'idx_task_user_order'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE task ADD INDEX idx_task_user_order (user_id, deleted, (status = ''COMPLETED''), sort_order, created_at, id)',
    'SELECT 1');
PREPARE add_task_order_idx FROM @ddl;
EXECUTE add_task_order_idx;
DEALLOCATE PREPARE add_task_order_idx;
