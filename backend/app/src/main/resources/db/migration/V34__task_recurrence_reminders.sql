ALTER TABLE task
    ADD COLUMN series_id CHAR(36) NULL AFTER duration_minutes,
    ADD COLUMN series_sequence INT NULL AFTER series_id,
    ADD COLUMN planned_due_at TIMESTAMP(3) NULL AFTER series_sequence,
    ADD COLUMN rrule VARCHAR(500) NULL AFTER planned_due_at,
    ADD COLUMN recurrence_anchor VARCHAR(24) NULL AFTER rrule,
    ADD COLUMN recurrence_exception_of CHAR(36) NULL AFTER recurrence_anchor,
    ADD UNIQUE KEY uk_task_series_due (series_id, planned_due_at),
    ADD KEY idx_task_recurrence_scan (status, deleted, recurrence_anchor, planned_due_at);

CREATE TABLE task_reminder (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    kind VARCHAR(16) NOT NULL,
    offset_minutes INT NULL,
    remind_at TIMESTAMP(3) NULL,
    channel VARCHAR(16) NOT NULL DEFAULT 'IN_APP',
    sent_at TIMESTAMP(3) NULL,
    cancelled_at TIMESTAMP(3) NULL,
    daily_until_done BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_reminder_public_id (public_id),
    KEY idx_task_reminder_due (cancelled_at, sent_at, remind_at),
    KEY idx_task_reminder_user (user_id, task_id),
    CONSTRAINT fk_task_reminder_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_task_reminder_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_reminder_fire (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reminder_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    fire_date DATE NOT NULL,
    fired_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    channel VARCHAR(16) NOT NULL,
    result VARCHAR(16) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_reminder_fire (reminder_id, fire_date),
    KEY idx_task_reminder_fire_user (user_id, fired_at),
    CONSTRAINT fk_task_reminder_fire_reminder FOREIGN KEY (reminder_id) REFERENCES task_reminder(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_reminder_fire_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_inbox_message (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    category VARCHAR(24) NOT NULL,
    type VARCHAR(48) NOT NULL,
    title VARCHAR(500) NOT NULL,
    body VARCHAR(2000) NOT NULL DEFAULT '',
    level VARCHAR(16) NOT NULL DEFAULT 'INFO',
    task_id BIGINT NULL,
    deep_link VARCHAR(500) NULL,
    dedupe_key VARCHAR(180) NOT NULL,
    read_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at TIMESTAMP(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_inbox_public_id (public_id),
    UNIQUE KEY uk_task_inbox_user_dedupe (user_id, dedupe_key),
    KEY idx_task_inbox_unread (user_id, read_at, created_at),
    CONSTRAINT fk_task_inbox_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_task_inbox_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_recurrence_instance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    series_id CHAR(36) NOT NULL,
    planned_due_at TIMESTAMP(3) NOT NULL,
    task_id BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_recurrence_instance (series_id, planned_due_at),
    CONSTRAINT fk_task_recurrence_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
