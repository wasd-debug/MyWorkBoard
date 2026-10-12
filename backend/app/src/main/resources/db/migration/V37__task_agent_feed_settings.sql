ALTER TABLE task_setting
    ADD COLUMN default_reminder_minutes INT NULL AFTER default_view,
    ADD COLUMN show_lunar BOOLEAN NOT NULL DEFAULT TRUE AFTER week_start,
    ADD COLUMN overlay_worktime BOOLEAN NOT NULL DEFAULT TRUE AFTER show_lunar,
    ADD COLUMN overlay_ledger BOOLEAN NOT NULL DEFAULT TRUE AFTER overlay_worktime,
    ADD COLUMN overlay_holiday BOOLEAN NOT NULL DEFAULT TRUE AFTER overlay_ledger,
    ADD COLUMN overlay_habit BOOLEAN NOT NULL DEFAULT TRUE AFTER overlay_holiday,
    ADD COLUMN notification_reminders BOOLEAN NOT NULL DEFAULT TRUE AFTER overlay_habit,
    ADD COLUMN notification_overdue BOOLEAN NOT NULL DEFAULT TRUE AFTER notification_reminders,
    ADD COLUMN daily_summary_time TIME NULL AFTER notification_overdue,
    ADD COLUMN quiet_start TIME NULL AFTER daily_summary_time,
    ADD COLUMN quiet_end TIME NULL AFTER quiet_start,
    ADD COLUMN feed_include_description BOOLEAN NOT NULL DEFAULT FALSE AFTER quiet_end;

CREATE TABLE task_feed_token (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_hint VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_accessed_at TIMESTAMP(3) NULL,
    revoked_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_feed_token_hash (token_hash),
    KEY idx_task_feed_user_active (user_id, revoked_at),
    CONSTRAINT fk_task_feed_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
