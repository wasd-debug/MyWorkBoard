-- 习惯页对齐滴答：分组、坚持天数目标、打卡样式、目标类型、完成量
ALTER TABLE habit
    ADD COLUMN group_key VARCHAR(16) NOT NULL DEFAULT 'other' AFTER sort_order,
    ADD COLUMN goal_days INT NULL AFTER group_key,
    ADD COLUMN check_style VARCHAR(16) NOT NULL DEFAULT 'CHECK' AFTER goal_days,
    ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'CHECK' AFTER check_style,
    ADD COLUMN daily_times INT NOT NULL DEFAULT 1 AFTER target_type,
    ADD COLUMN auto_popup_log BOOLEAN NOT NULL DEFAULT FALSE AFTER daily_times;

ALTER TABLE habit_checkin ADD COLUMN amount INT NOT NULL DEFAULT 0 AFTER count;

