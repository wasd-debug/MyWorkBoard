CREATE TABLE IF NOT EXISTS ledger_account (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    account_type VARCHAR(32) NOT NULL DEFAULT 'cash',
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    opening_balance DECIMAL(18,2) NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ledger_account_user (user_id, deleted),
    CONSTRAINT fk_ledger_account_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_category (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'EXPENSE',
    parent_id BIGINT NULL,
    color VARCHAR(32) NOT NULL DEFAULT '#0f5132',
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ledger_category_user (user_id, deleted),
    CONSTRAINT fk_ledger_category_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_ledger_category_parent FOREIGN KEY (parent_id) REFERENCES ledger_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    kind VARCHAR(16) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    occurred_on DATE NOT NULL,
    payee VARCHAR(180) NOT NULL DEFAULT '',
    note VARCHAR(500) NOT NULL DEFAULT '',
    source VARCHAR(32) NOT NULL DEFAULT 'manual',
    recurring_id BIGINT NULL,
    client_op_id VARCHAR(160) NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_transaction_op (user_id, client_op_id),
    KEY idx_ledger_transaction_user_date (user_id, occurred_on, deleted),
    KEY idx_ledger_transaction_account (account_id, occurred_on),
    CONSTRAINT fk_ledger_transaction_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_ledger_transaction_account FOREIGN KEY (account_id) REFERENCES ledger_account(id),
    CONSTRAINT fk_ledger_transaction_category FOREIGN KEY (category_id) REFERENCES ledger_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_transaction_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    operation VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    revision BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ledger_history_transaction (transaction_id, created_at),
    CONSTRAINT fk_ledger_history_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transaction(id),
    CONSTRAINT fk_ledger_history_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_budget (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    month_key CHAR(7) NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_budget_user_category_month (user_id, category_id, month_key),
    CONSTRAINT fk_ledger_budget_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_ledger_budget_category FOREIGN KEY (category_id) REFERENCES ledger_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS recurring_bill (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    category_id BIGINT NULL,
    kind VARCHAR(16) NOT NULL DEFAULT 'EXPENSE',
    amount DECIMAL(18,2) NOT NULL,
    currency CHAR(3) NOT NULL DEFAULT 'CNY',
    title VARCHAR(180) NOT NULL,
    frequency VARCHAR(16) NOT NULL DEFAULT 'MONTHLY',
    next_due DATE NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    revision BIGINT NOT NULL DEFAULT 1,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    last_generated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_recurring_due (active, deleted, next_due),
    CONSTRAINT fk_recurring_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_recurring_account FOREIGN KEY (account_id) REFERENCES ledger_account(id),
    CONSTRAINT fk_recurring_category FOREIGN KEY (category_id) REFERENCES ledger_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_balance_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    month_key CHAR(7) NOT NULL,
    balance DECIMAL(18,2) NOT NULL DEFAULT 0,
    calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_balance_snapshot (user_id, account_id, month_key),
    CONSTRAINT fk_ledger_balance_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_ledger_balance_account FOREIGN KEY (account_id) REFERENCES ledger_account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_sync_oplog (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    op_id VARCHAR(160) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(120) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    payload_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ledger_sync_op (user_id, op_id),
    KEY idx_ledger_sync_cursor (user_id, id),
    CONSTRAINT fk_ledger_sync_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ledger_job_lock (
    lock_name VARCHAR(120) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(120) NOT NULL,
    PRIMARY KEY (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO permission (code, module, action) VALUES
    ('ledger:read', 'ledger', 'read'),
    ('ledger:write', 'ledger', 'write'),
    ('ledger:import', 'ledger', 'import');

INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT 1, id FROM permission WHERE code IN ('ledger:read', 'ledger:write', 'ledger:import');
INSERT IGNORE INTO role_permission (role_id, permission_id)
SELECT 2, id FROM permission WHERE code IN ('ledger:read', 'ledger:write', 'ledger:import');
