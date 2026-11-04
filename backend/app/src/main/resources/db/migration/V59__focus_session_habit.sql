-- 专注记录除了挂在任务上，也可以挂在习惯上（滴答清单里两者都能作为专注对象）
ALTER TABLE focus_session
    ADD COLUMN habit_id BIGINT NULL AFTER task_id,
    ADD KEY idx_focus_habit (habit_id),
    ADD CONSTRAINT fk_focus_habit FOREIGN KEY (habit_id) REFERENCES habit(id);
