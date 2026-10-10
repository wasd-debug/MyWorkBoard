ALTER TABLE task ADD COLUMN focus_minutes INT NOT NULL DEFAULT 0;

CREATE TABLE focus_setting (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    focus_minutes INT NOT NULL DEFAULT 25,
    short_break_minutes INT NOT NULL DEFAULT 5,
    long_break_minutes INT NOT NULL DEFAULT 15,
    long_break_interval INT NOT NULL DEFAULT 4,
    auto_start_break BOOLEAN NOT NULL DEFAULT FALSE,
    auto_start_next BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_focus_setting_user (user_id),
    CONSTRAINT fk_focus_setting_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE focus_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    planned_minutes INT NOT NULL,
    actual_minutes INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP(3) NOT NULL,
    ended_at TIMESTAMP(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    running_user_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'RUNNING' THEN user_id ELSE NULL END) STORED,
    device_label VARCHAR(120) NOT NULL DEFAULT '',
    client_op_key VARCHAR(120) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_focus_public_id (public_id),
    UNIQUE KEY uk_focus_running_user (running_user_id),
    UNIQUE KEY uk_focus_user_op (user_id, client_op_key),
    KEY idx_focus_user_started (user_id, started_at),
    CONSTRAINT fk_focus_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_focus_task FOREIGN KEY (task_id) REFERENCES task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE habit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    icon VARCHAR(32) NOT NULL DEFAULT 'check',
    color VARCHAR(32) NOT NULL DEFAULT '#2f746f',
    frequency VARCHAR(16) NOT NULL DEFAULT 'DAILY',
    target_count INT NOT NULL DEFAULT 1,
    custom_days VARCHAR(32) NULL,
    remind_at VARCHAR(5) NULL,
    start_date DATE NOT NULL,
    is_archived BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_habit_public_id (public_id),
    KEY idx_habit_user_active (user_id, is_archived, deleted_at),
    CONSTRAINT fk_habit_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE habit_checkin (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    habit_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    checkin_date DATE NOT NULL,
    count INT NOT NULL DEFAULT 1,
    status VARCHAR(8) NOT NULL DEFAULT 'DONE',
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id), UNIQUE KEY uk_habit_checkin_day (habit_id, checkin_date),
    KEY idx_habit_checkin_user_date (user_id, checkin_date),
    CONSTRAINT fk_habit_checkin_habit FOREIGN KEY (habit_id) REFERENCES habit(id),
    CONSTRAINT fk_habit_checkin_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE countdown (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    target_date DATE NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'COUNTDOWN',
    repeat_yearly BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    color VARCHAR(32) NOT NULL DEFAULT '#2f746f',
    note VARCHAR(1000) NOT NULL DEFAULT '',
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id), UNIQUE KEY uk_countdown_public_id (public_id),
    KEY idx_countdown_user_target (user_id, target_date),
    CONSTRAINT fk_countdown_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
