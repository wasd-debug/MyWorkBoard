ALTER TABLE task_setting
    ADD COLUMN default_due_date VARCHAR(12) NOT NULL DEFAULT 'TODAY' AFTER default_reminder_minutes,
    ADD COLUMN default_due_time VARCHAR(16) NOT NULL DEFAULT 'NEXT_HOUR' AFTER default_due_date;
