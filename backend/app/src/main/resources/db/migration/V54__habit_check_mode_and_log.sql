-- 习惯页对齐参考图第 2 轮：按时间间隔频率、打卡方式、每次记录量、显示开关、打卡日志心情与心得
ALTER TABLE habit
    ADD COLUMN interval_days INT NOT NULL DEFAULT 2 AFTER daily_times,
    ADD COLUMN check_mode VARCHAR(16) NOT NULL DEFAULT 'AUTO' AFTER interval_days,
    ADD COLUMN per_amount INT NOT NULL DEFAULT 1 AFTER check_mode,
    ADD COLUMN show_in_today BOOLEAN NOT NULL DEFAULT TRUE AFTER per_amount;

ALTER TABLE habit_checkin
    ADD COLUMN mood VARCHAR(16) NOT NULL DEFAULT '' AFTER amount,
    ADD COLUMN note VARCHAR(500) NOT NULL DEFAULT '' AFTER mood;
